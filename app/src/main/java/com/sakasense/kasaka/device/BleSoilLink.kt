package com.sakasense.kasaka.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * One GATT connection to a SakaSense device. Callers must hold BLUETOOTH_CONNECT
 * (Android 12+); [DeviceManager] checks that before using this class.
 */
@SuppressLint("MissingPermission")
internal class BleSoilLink(private val context: Context, private val adapter: BluetoothAdapter) {

    private sealed interface Event {
        data object Connected : Event
        data class Disconnected(val status: Int) : Event
        data object MtuChanged : Event
        data class ServicesDiscovered(val ok: Boolean) : Event
        data class DescriptorWritten(val ok: Boolean) : Event
        data class CharacteristicWritten(val ok: Boolean) : Event
        data class CharacteristicRead(val value: ByteArray?) : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)
    private val notifications = Channel<ByteArray>(Channel.UNLIMITED)
    private var gatt: BluetoothGatt? = null
    private var reading: BluetoothGattCharacteristic? = null
    private var command: BluetoothGattCharacteristic? = null
    private var info: BluetoothGattCharacteristic? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> events.trySend(Event.Connected)
                BluetoothProfile.STATE_DISCONNECTED -> events.trySend(Event.Disconnected(status))
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            events.trySend(Event.MtuChanged)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            events.trySend(Event.ServicesDiscovered(status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            events.trySend(Event.DescriptorWritten(status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            events.trySend(Event.CharacteristicWritten(status == BluetoothGatt.GATT_SUCCESS))
        }

        // Android 13+
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            events.trySend(Event.CharacteristicRead(if (status == BluetoothGatt.GATT_SUCCESS) value else null))
        }

        @Deprecated("Android 12 and below")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                events.trySend(Event.CharacteristicRead(if (status == BluetoothGatt.GATT_SUCCESS) c.value else null))
            }
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == DeviceProtocol.READING) notifications.trySend(value)
        }

        @Deprecated("Android 12 and below")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && c.uuid == DeviceProtocol.READING) {
                notifications.trySend(c.value)
            }
        }
    }

    suspend fun connect(address: String): DeviceInfoPayload? {
        val device = adapter.getRemoteDevice(address)
        @Suppress("DEPRECATION") // still the only LE-transport overload on minSdk 26
        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw IOException("Could not open a Bluetooth connection")
        gatt = g
        try {
            expect<Event.Connected>(15_000, "The device did not respond. Make sure it is on and nearby.")
            g.requestMtu(DeviceProtocol.MTU)
            withTimeoutOrNull(5_000) { awaitEvent<Event.MtuChanged>() }

            g.discoverServices()
            if (!expect<Event.ServicesDiscovered>(10_000, "Service discovery timed out").ok) throw IOException("Service discovery failed")
            val service = g.getService(DeviceProtocol.SERVICE)
                ?: throw IOException("This is not a SakaSense device (service not found)")
            reading = service.getCharacteristic(DeviceProtocol.READING) ?: throw IOException("Reading characteristic missing")
            command = service.getCharacteristic(DeviceProtocol.COMMAND) ?: throw IOException("Command characteristic missing")
            info = service.getCharacteristic(DeviceProtocol.INFO)

            enableNotifications(g, reading!!)
            return info?.let { readInfo(g, it) }
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    /**
     * Asks the device to take a reading and waits for the JSON result. [crop] (a Kasaka Guidebook crop,
     * or null for "best crop") is sent first so the device works out the code for the crop that is planted.
     */
    suspend fun measure(crop: String?, timeoutMs: Long = 45_000): DevicePayload {
        val g = gatt ?: throw IOException("Not connected")
        val cmd = command ?: throw IOException("Not connected")
        while (notifications.tryReceive().isSuccess) { /* drop stale data */ }

        write(g, cmd, (DeviceProtocol.CMD_CROP + crop.orEmpty()).toByteArray())
        expect<Event.CharacteristicWritten>(5_000, "The device did not accept the command")

        write(g, cmd, DeviceProtocol.CMD_MEASURE.toByteArray())
        if (!expect<Event.CharacteristicWritten>(5_000, "The device did not accept the command").ok) throw IOException("The device rejected the measure command")

        val buffer = StringBuilder()
        return withTimeoutOrNull(timeoutMs) {
            var result: DevicePayload? = null
            while (result == null) {
                buffer.append(notifications.receive().decodeToString())
                val text = buffer.toString().trim()
                // Payloads may arrive split across notifications; parse once a full JSON object is in.
                if (text.endsWith("}")) {
                    result = runCatching { DeviceProtocol.json.decodeFromString<DevicePayload>(text) }.getOrNull()
                }
            }
            result
        } ?: throw IOException("No reading received. Check that the probe is in the soil and try again.")
    }

    fun close() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
    }

    private suspend fun enableNotifications(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(c, true)
        val cccd = c.getDescriptor(DeviceProtocol.CCCD) ?: return
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, value)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
        expect<Event.DescriptorWritten>(5_000, "Could not subscribe to readings")
    }

    private suspend fun readInfo(g: BluetoothGatt, c: BluetoothGattCharacteristic): DeviceInfoPayload? {
        if (!g.readCharacteristic(c)) return null
        val bytes = withTimeoutOrNull(5_000) { awaitEvent<Event.CharacteristicRead>().value } ?: return null
        return runCatching { DeviceProtocol.json.decodeFromString<DeviceInfoPayload>(bytes.decodeToString()) }.getOrNull()
    }

    private fun write(g: BluetoothGatt, c: BluetoothGattCharacteristic, bytes: ByteArray) {
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, bytes, type)
        } else {
            @Suppress("DEPRECATION")
            c.writeType = type
            @Suppress("DEPRECATION")
            c.value = bytes
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }

    private suspend inline fun <reified T : Event> expect(timeoutMs: Long, timeoutMessage: String): T =
        withTimeoutOrNull(timeoutMs) { awaitEvent<T>() } ?: throw IOException(timeoutMessage)

    private suspend inline fun <reified T : Event> awaitEvent(): T {
        while (true) {
            when (val e = events.receive()) {
                is T -> return e
                is Event.Disconnected -> throw IOException("The device disconnected (status ${e.status})")
                else -> Unit
            }
        }
    }
}
