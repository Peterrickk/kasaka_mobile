package com.sakasense.kasaka.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// Mirrors the SakaSense web dashboard's data model (farms → plots → devices/readings/recommendations/alerts)
// so a future sync API can map 1:1.

enum class FarmType { RURAL_FIELD, URBAN_CONTAINER }
enum class PlotStatus { HEALTHY, NEEDS_ATTENTION, ACTION_REQUIRED }
enum class DeviceStatus { ONLINE, SYNCING, OFFLINE }
enum class ReadingSource { BLUETOOTH, LIVE, SYNCED_SD_CARD, MANUAL, DEMO }
enum class AlertType { DEFICIENCY, DEVICE_OFFLINE, CHECK_IN_DUE }

@Entity(tableName = "farms")
data class FarmEntity(
    @PrimaryKey val id: String,
    val name: String,
    val location: String,
    val type: FarmType,
    val lat: Double? = null,
    val lng: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "plots",
    foreignKeys = [ForeignKey(entity = FarmEntity::class, parentColumns = ["id"], childColumns = ["farmId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("farmId")],
)
data class PlotEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val name: String,
    val cropCurrent: String?,
    val areaLabel: String,
    val status: PlotStatus = PlotStatus.NEEDS_ATTENTION,
    // Position on the farm layout map (3-column grid), same as the web dashboard.
    val layoutCol: Int = 1,
    val layoutRow: Int = 1,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "devices", indices = [Index("plotId"), Index(value = ["serialNumber"], unique = true)])
data class DeviceEntity(
    @PrimaryKey val id: String,
    val plotId: String?,
    val serialNumber: String,
    val model: String,
    val lastSeenAt: Long,
    val batteryPercent: Int?,
    val status: DeviceStatus,
)

@Entity(
    tableName = "readings",
    foreignKeys = [ForeignKey(entity = PlotEntity::class, parentColumns = ["id"], childColumns = ["plotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["plotId", "timestamp"])],
)
data class ReadingEntity(
    @PrimaryKey val id: String,
    val plotId: String,
    val deviceSerial: String?,
    val timestamp: Long,
    val ph: Double,
    /** Nitrogen, mg/kg */
    val n: Int,
    /** Phosphorus, mg/kg */
    val p: Int,
    /** Potassium, mg/kg */
    val k: Int,
    val source: ReadingSource,
)

@Serializable
data class RankedCrop(val crop: String, val score: Int)

@Entity(
    tableName = "recommendations",
    foreignKeys = [ForeignKey(entity = PlotEntity::class, parentColumns = ["id"], childColumns = ["plotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["plotId", "timestamp"])],
)
data class RecommendationEntity(
    @PrimaryKey val id: String,
    val plotId: String,
    val readingId: String?,
    val timestamp: Long,
    val healthIndex: Int,
    val rankedCrops: List<RankedCrop>,
    val guidebookCode: String,
    val fertilizerProduct: String,
    val fertilizerAmount: String,
    val fertilizerTiming: String,
    val fertilizerNotes: String,
    val insight: String,
    val generatedOnDevice: Boolean,
)

@Entity(
    tableName = "alerts",
    foreignKeys = [ForeignKey(entity = PlotEntity::class, parentColumns = ["id"], childColumns = ["plotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plotId")],
)
data class AlertEntity(
    @PrimaryKey val id: String,
    val plotId: String,
    val type: AlertType,
    val message: String,
    val triggeredAt: Long,
    val resolvedAt: Long? = null,
)
