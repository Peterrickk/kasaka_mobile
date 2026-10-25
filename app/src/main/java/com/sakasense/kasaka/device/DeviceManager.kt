package com.sakasense.kasaka.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.sakasense.kasaka.data.local.RankedCrop
import com.sakasense.kasaka.domain.SoilAnalyzer
import com.sakasense.kasaka.domain.SoilSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.random.Random

data class FoundDevice(val address: String, val name: String, val rssi: Int?, val simulated: Boolean = false)

data class ConnectedDevice(val device: FoundDevice, val serial: String, val firmware: String?, val battery: Int?)

/**
 * Finds and talks to SakaSense devices over Bluetooth LE. A simulated "demo device" is always listed
 * so the full flow can be tried (and demoed) without hardware.
 */
class DeviceManager(private val context: Context) {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var scanJob: Job? = null
    private var ble: BleSoilLink? = null
    private var connected: ConnectedDevice? = null

    private val _found = MutableStateFlow(listOf(DEMO_DEVICE))
    val found: StateFlow<List<FoundDevice>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val hasBluetooth: Boolean get() = adapter != null
    val bluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
            val advertisesService = result.scanRecord?.serviceUuids?.contains(ParcelUuid(DeviceProtocol.SERVICE)) == true
            val looksLikeSakaSense = name != null && DeviceProtocol.NAME_PREFIXES.any { name.startsWith(it, ignoreCase = true) }
            if (!advertisesService && !looksLikeSakaSense) return
            val found = FoundDevice(result.device.address, name ?: "SakaSense device", result.rssi)
            _found.update { list -> (list.filterNot { it.address == found.address } + found).sortedBy { it.simulated } }
        }
    }

    /** Scans for ~12 s. Without permission or Bluetooth, only the demo device is listed. */
    @SuppressLint("MissingPermission")
    fun startScan(scope: CoroutineScope) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !bluetoothEnabled || !hasPermissions()) return
        stopScan()
        _found.update { list -> list.filter { it.simulated } }
        _scanning.value = true
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(null, settings, scanCallback) }
            .onFailure { _scanning.value = false; return }
        scanJob = scope.launch {
            delay(12_000)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        if (_scanning.value && hasPermissions()) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
    }

    suspend fun connect(device: FoundDevice): ConnectedDevice {
        stopScan()
        disconnect()
        val result = if (device.simulated) {
            delay(1_200)
            ConnectedDevice(device, "KS-DEMO-000001", "2.1.0-demo", 88)
        } else {
            val bt = adapter ?: throw IOException("Bluetooth is not available on this phone")
            if (!hasPermissions()) throw IOException("Bluetooth permission is needed to connect")
            val link = BleSoilLink(context, bt)
            val info = link.connect(device.address)
            ble = link
            ConnectedDevice(device, info?.sn ?: device.name, info?.fw, info?.bat)
        }
        connected = result
        return result
    }

    /**
     * Takes one measurement. [onProgress] gets 0..1 — real devices don't report progress, so it is
     * an estimate that stops at 95 % until the result arrives.
     */
    suspend fun measure(scope: CoroutineScope, onProgress: (Float) -> Unit): DevicePayload {
        val device = connected ?: throw IOException("No device connected")
        val ticker = scope.launch {
            var p = 0f
            while (isActive && p < 0.95f) {
                delay(80)
                p = (p + 0.02f).coerceAtMost(0.95f)
                onProgress(p)
            }
        }
        try {
            val payload = if (device.device.simulated) {
                delay(3_500)
                simulatedPayload(device.serial)
            } else {
                (ble ?: throw IOException("Device disconnected")).measure()
            }
            onProgress(1f)
            return payload
        } finally {
            ticker.cancel()
        }
    }

    fun disconnect() {
        ble?.close()
        ble = null
        connected = null
    }

    private fun simulatedPayload(serial: String): DevicePayload {
        // Picks one of a few realistic soil profiles so repeated demo readings vary.
        val base = listOf(
            SoilSample(6.3, 62, 44, 74),
            SoilSample(5.4, 34, 36, 40),
            SoilSample(6.0, 46, 24, 55),
            SoilSample(6.8, 70, 48, 82),
        ).random()
        val s = SoilSample(
            ph = ((base.ph + Random.nextDouble(-0.2, 0.2)) * 100).roundToInt() / 100.0,
            n = base.n + Random.nextInt(-5, 6),
            p = base.p + Random.nextInt(-3, 4),
            k = base.k + Random.nextInt(-5, 6),
        )
        val crops: List<RankedCrop> = SoilAnalyzer.rankCrops(s)
        return DevicePayload(s.ph, s.n, s.p, s.k, bat = 88, sn = serial, ts = System.currentTimeMillis() / 1000, crops = crops)
    }

    companion object {
        val DEMO_DEVICE = FoundDevice("demo", "SakaSense Demo Device", null, simulated = true)
    }
}
