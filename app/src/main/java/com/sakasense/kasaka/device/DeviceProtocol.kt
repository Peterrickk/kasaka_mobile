package com.sakasense.kasaka.device

import com.sakasense.kasaka.data.local.RankedCrop
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * BLE contract between the app and the SakaSense field device (ESP32).
 * Full spec for the firmware side: docs/DEVICE_PROTOCOL.md — keep the two in sync.
 */
object DeviceProtocol {
    val SERVICE: UUID = UUID.fromString("7d2a0001-5a4b-4c53-9a1e-5341534b4153")
    /** read + notify: one [DevicePayload] JSON per measurement, newline-terminated. */
    val READING: UUID = UUID.fromString("7d2a0002-5a4b-4c53-9a1e-5341534b4153")
    /** write: ASCII commands, e.g. "MEASURE". */
    val COMMAND: UUID = UUID.fromString("7d2a0003-5a4b-4c53-9a1e-5341534b4153")
    /** read: [DeviceInfoPayload] JSON. */
    val INFO: UUID = UUID.fromString("7d2a0004-5a4b-4c53-9a1e-5341534b4153")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val CMD_MEASURE = "MEASURE"
    val NAME_PREFIXES = listOf("SakaSense", "KS-")
    const val MTU = 247

    val json = Json { ignoreUnknownKeys = true; isLenient = true }
}

@Serializable
data class DevicePayload(
    val ph: Double,
    val n: Int,
    val p: Int,
    val k: Int,
    val bat: Int? = null,
    val sn: String? = null,
    val ts: Long? = null,
    /** Optional: the device's own on-board crop ranking. */
    val crops: List<RankedCrop>? = null,
    /** Optional: Guidebook code shown on the device LCD. */
    val gb: String? = null,
)

@Serializable
data class DeviceInfoPayload(val sn: String, val fw: String? = null, val bat: Int? = null)
