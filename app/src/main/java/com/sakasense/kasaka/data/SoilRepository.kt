package com.sakasense.kasaka.data

import androidx.room.withTransaction
import com.sakasense.kasaka.data.local.AlertEntity
import com.sakasense.kasaka.data.local.AlertType
import com.sakasense.kasaka.data.local.DeviceEntity
import com.sakasense.kasaka.data.local.DeviceStatus
import com.sakasense.kasaka.data.local.FarmEntity
import com.sakasense.kasaka.data.local.FarmType
import com.sakasense.kasaka.data.local.KasakaDatabase
import com.sakasense.kasaka.data.local.PlotEntity
import com.sakasense.kasaka.data.local.PlotStatus
import com.sakasense.kasaka.data.local.RankedCrop
import com.sakasense.kasaka.data.local.ReadingEntity
import com.sakasense.kasaka.data.local.ReadingSource
import com.sakasense.kasaka.data.local.RecommendationEntity
import com.sakasense.kasaka.domain.Guidebook
import com.sakasense.kasaka.domain.SoilAnalysis
import com.sakasense.kasaka.domain.SoilAnalyzer
import com.sakasense.kasaka.domain.SoilCondition
import com.sakasense.kasaka.domain.SoilSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

data class PlotSummary(
    val plot: PlotEntity,
    val farm: FarmEntity?,
    val latest: ReadingEntity?,
    val recommendation: RecommendationEntity?,
    val device: DeviceEntity?,
) {
    val healthIndex: Int?
        get() = recommendation?.healthIndex ?: latest?.let { SoilAnalyzer.healthIndex(it.toSample()) }
}

fun ReadingEntity.toSample() = SoilSample(ph, n, p, k)

/** What the field device itself concluded (its on-board model), if it sent that along. */
data class DeviceVerdict(val rankedCrops: List<RankedCrop>?, val guidebookCode: String?)

class SoilRepository(private val db: KasakaDatabase) {

    val farms: Flow<List<FarmEntity>> = db.farms().observeAll()
    val devices: Flow<List<DeviceEntity>> = db.devices().observeAll()
    val alerts: Flow<List<AlertEntity>> = db.alerts().observeAll()

    val plotSummaries: Flow<List<PlotSummary>> = combine(
        db.plots().observeAll(),
        farms,
        db.readings().observeLatestPerPlot(),
        db.recommendations().observeLatestPerPlot(),
        devices,
    ) { plots, farms, readings, recs, devices ->
        val farmById = farms.associateBy { it.id }
        val readingByPlot = readings.associateBy { it.plotId }
        val recByPlot = recs.associateBy { it.plotId }
        val deviceByPlot = devices.filter { it.plotId != null }.associateBy { it.plotId }
        plots.map { PlotSummary(it, farmById[it.farmId], readingByPlot[it.id], recByPlot[it.id], deviceByPlot[it.id]) }
    }

    fun plot(id: String): Flow<PlotSummary?> = plotSummaries.map { list -> list.firstOrNull { it.plot.id == id } }

    fun readings(plotId: String, days: Int): Flow<List<ReadingEntity>> =
        db.readings().observeForPlot(plotId, System.currentTimeMillis() - days * DAY)

    fun recommendations(plotId: String): Flow<List<RecommendationEntity>> = db.recommendations().observeForPlot(plotId)

    /**
     * Stores a reading, runs the analyzer and updates the plot's status, recommendation and alerts.
     * If the device sent its own crop ranking / Guidebook code, those are kept (generatedOnDevice).
     */
    suspend fun saveReading(
        plotId: String,
        sample: SoilSample,
        source: ReadingSource,
        deviceSerial: String? = null,
        deviceBattery: Int? = null,
        verdict: DeviceVerdict? = null,
        at: Long = System.currentTimeMillis(),
    ): SoilAnalysis = db.withTransaction {
        val plot = requireNotNull(db.plots().get(plotId)) { "Plot $plotId not found" }
        val farmType = db.farms().get(plot.farmId)?.type ?: FarmType.RURAL_FIELD
        val analysis = SoilAnalyzer.analyze(sample, plot.cropCurrent, farmType)
        val readingId = UUID.randomUUID().toString()

        db.readings().insert(listOf(ReadingEntity(readingId, plotId, deviceSerial, at, sample.ph, sample.n, sample.p, sample.k, source)))
        db.recommendations().insert(listOf(analysis.toRecommendation(plotId, readingId, at, verdict)))
        db.plots().setStatus(plotId, analysis.status)

        if (deviceSerial != null) {
            val existing = db.devices().bySerial(deviceSerial)
            db.devices().upsert(
                DeviceEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    plotId = plotId,
                    serialNumber = deviceSerial,
                    model = existing?.model ?: "ESP32-SakaSense-v2",
                    lastSeenAt = at,
                    batteryPercent = deviceBattery ?: existing?.batteryPercent,
                    status = DeviceStatus.ONLINE,
                )
            )
            // Any device-offline alert raised for this plot is stale now.
            db.alerts().resolveForPlot(plotId, AlertType.DEVICE_OFFLINE, at)
        }

        db.alerts().resolveForPlot(plotId, AlertType.DEFICIENCY, at)
        db.alerts().resolveForPlot(plotId, AlertType.CHECK_IN_DUE, at)
        if (analysis.status != PlotStatus.HEALTHY) {
            db.alerts().insert(listOf(AlertEntity(UUID.randomUUID().toString(), plotId, AlertType.DEFICIENCY, alertMessage(analysis, plot.name), at)))
        }
        analysis
    }

    /** Raises device-offline and check-in-due alerts. Call on app start. */
    suspend fun refreshAlerts(now: Long = System.currentTimeMillis()) = db.withTransaction {
        val open = db.alerts().open()
        val newAlerts = mutableListOf<AlertEntity>()
        db.devices().all().forEach { d ->
            val plotId = d.plotId ?: return@forEach
            val stale = now - d.lastSeenAt > DAY
            if (stale && d.status != DeviceStatus.OFFLINE) db.devices().upsert(d.copy(status = DeviceStatus.OFFLINE))
            if (stale && open.none { it.plotId == plotId && it.type == AlertType.DEVICE_OFFLINE }) {
                val days = ((now - d.lastSeenAt) / DAY).toInt()
                newAlerts += AlertEntity(
                    UUID.randomUUID().toString(), plotId, AlertType.DEVICE_OFFLINE,
                    "Device ${d.serialNumber} last synced $days day${if (days == 1) "" else "s"} ago", now,
                )
            }
        }
        val latest = db.readings().allLatest()
        latest.forEach { r ->
            if (now - r.timestamp > 7 * DAY && open.none { it.plotId == r.plotId && it.type == AlertType.CHECK_IN_DUE }) {
                val name = db.plots().get(r.plotId)?.name ?: "plot"
                newAlerts += AlertEntity(UUID.randomUUID().toString(), r.plotId, AlertType.CHECK_IN_DUE, "Recommended check-in overdue for $name", now)
            }
        }
        if (newAlerts.isNotEmpty()) db.alerts().insert(newAlerts)
    }

    suspend fun addFarm(name: String, location: String, type: FarmType): String {
        val id = UUID.randomUUID().toString()
        db.farms().upsert(FarmEntity(id, name.trim(), location.trim(), type))
        return id
    }

    /** Adds a plot below the existing ones on the farm layout map. */
    suspend fun addPlot(farmId: String, name: String, crop: String?, area: String): String {
        val id = UUID.randomUUID().toString()
        val nextRow = (db.plots().forFarm(farmId).maxOfOrNull { it.layoutRow + it.rowSpan - 1 } ?: 0) + 1
        db.plots().upsert(PlotEntity(id, farmId, name.trim(), crop, area.trim(), PlotStatus.NEEDS_ATTENTION, 1, nextRow, 3, 1))
        return id
    }

    suspend fun updatePlotCrop(plotId: String, crop: String?) {
        db.plots().get(plotId)?.let { db.plots().upsert(it.copy(cropCurrent = crop)) }
    }

    suspend fun deletePlot(plotId: String) = db.plots().delete(plotId)

    suspend fun deleteFarm(farmId: String) = db.farms().delete(farmId)

    suspend fun resolveAlert(id: String) = db.alerts().resolve(id, System.currentTimeMillis())

    suspend fun csvReport(plotId: String): String {
        val plot = db.plots().get(plotId)
        val rows = db.readings().allForPlot(plotId)
        return buildString {
            appendLine("# SakaSense / Kasaka soil report — ${plot?.name.orEmpty()} (${plot?.cropCurrent ?: "no crop"})")
            appendLine("timestamp_iso,ph,nitrogen_mg_kg,phosphorus_mg_kg,potassium_mg_kg,health_index,source,device")
            rows.forEach { r ->
                appendLine(
                    listOf(
                        java.time.Instant.ofEpochMilli(r.timestamp).toString(), r.ph, r.n, r.p, r.k,
                        SoilAnalyzer.healthIndex(r.toSample()), r.source.name.lowercase(), r.deviceSerial.orEmpty(),
                    ).joinToString(",")
                )
            }
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) { db.clearAllTables() }

    suspend fun seedDemo() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        DemoData.seed(db, this@SoilRepository)
    }

    private fun alertMessage(a: SoilAnalysis, plotName: String): String {
        val what = when (a.condition) {
            SoilCondition.BALANCED -> "Borderline nutrients"
            SoilCondition.ACIDIC -> "Soil too acidic"
            SoilCondition.ALKALINE -> "Soil too alkaline"
            SoilCondition.N_LOW -> "Nitrogen deficiency"
            SoilCondition.P_LOW -> "Phosphorus deficiency"
            SoilCondition.K_LOW -> "Potassium deficiency"
            SoilCondition.MULTI_LOW -> "Multiple nutrient deficiencies"
        }
        return if (a.status == PlotStatus.ACTION_REQUIRED) "$what — Action Required in $plotName" else "$what in $plotName"
    }

    companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}

internal fun SoilAnalysis.toRecommendation(plotId: String, readingId: String?, at: Long, verdict: DeviceVerdict?) =
    RecommendationEntity(
        id = UUID.randomUUID().toString(),
        plotId = plotId,
        readingId = readingId,
        timestamp = at,
        healthIndex = healthIndex,
        rankedCrops = verdict?.rankedCrops?.takeIf { it.isNotEmpty() } ?: rankedCrops,
        guidebookCode = verdict?.guidebookCode?.takeIf { Guidebook.find(it) != null } ?: guidebook.code,
        fertilizerProduct = fertilizer.product,
        fertilizerAmount = fertilizer.amount,
        fertilizerTiming = fertilizer.timing,
        fertilizerNotes = fertilizer.notes,
        insight = insight,
        generatedOnDevice = verdict != null,
    )
