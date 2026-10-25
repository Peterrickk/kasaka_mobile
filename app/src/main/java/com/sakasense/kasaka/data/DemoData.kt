package com.sakasense.kasaka.data

import com.sakasense.kasaka.data.SoilRepository.Companion.HOUR
import com.sakasense.kasaka.data.local.AlertEntity
import com.sakasense.kasaka.data.local.AlertType
import com.sakasense.kasaka.data.local.DeviceEntity
import com.sakasense.kasaka.data.local.DeviceStatus
import com.sakasense.kasaka.data.local.FarmEntity
import com.sakasense.kasaka.data.local.FarmType
import com.sakasense.kasaka.data.local.KasakaDatabase
import com.sakasense.kasaka.data.local.PlotEntity
import com.sakasense.kasaka.data.local.PlotStatus
import com.sakasense.kasaka.data.local.ReadingEntity
import com.sakasense.kasaka.data.local.ReadingSource
import com.sakasense.kasaka.domain.SoilAnalyzer
import com.sakasense.kasaka.domain.SoilSample
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** The same demo farm the web dashboard shows (Maria Santos, Baybay & Tacloban). */
object DemoData {

    // Warm, humid Leyte weather: temperature rises as humidity falls.
    private fun temperature(w: Double, rnd: Random) = ((28 + 0.8 * w + (rnd.nextDouble() - 0.5) * 1.5) * 10).roundToInt() / 10.0
    private fun humidity(w: Double, rnd: Random) = ((58 - 2.5 * w + (rnd.nextDouble() - 0.5) * 6) * 10).roundToInt() / 10.0

    /**
     * Gives demo readings saved before the app knew about temperature and humidity (DB v1) the same
     * kind of values a fresh seed has. Manual readings are left alone: the farmer typed what they had.
     */
    suspend fun backfillClimate(db: KasakaDatabase, now: Long = System.currentTimeMillis()) {
        val missing = db.readings().withoutClimate(listOf(ReadingSource.LIVE, ReadingSource.SYNCED_SD_CARD, ReadingSource.DEMO))
        missing.forEach { r ->
            val a = ((now - r.timestamp) / SoilRepository.DAY).toInt()
            val w = 2 * sin(a / 3.0)
            val rnd = Random(r.id.hashCode())
            db.readings().setClimate(r.id, temperature(w, rnd), humidity(w, rnd))
        }
    }

    private fun hoursAgo(h: Double, now: Long) = now - (h * HOUR).toLong()

    private fun daysAgoAt(days: Int, hour: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -days)
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private class Spec(val plot: PlotEntity, val device: DeviceEntity, val base: SoilSample, val days: Int, val lastDay: Int)

    suspend fun seed(db: KasakaDatabase, repo: SoilRepository) {
        val now = System.currentTimeMillis()
        val rnd = Random(4821)

        db.farms().upsert(
            FarmEntity("farm-1", "Santos Family Farm", "Baybay, Leyte", FarmType.RURAL_FIELD, 10.6785, 124.8006),
            FarmEntity("farm-2", "Rooftop Garden", "Tacloban City", FarmType.URBAN_CONTAINER, 11.244, 125.003),
        )

        val specs = listOf(
            Spec(
                PlotEntity("plot-a", "farm-1", "Zone A — Potato", "Potato", "0.5 ha", PlotStatus.HEALTHY, 1, 1, 2, 2),
                DeviceEntity("dev-1", "plot-a", "KS-ESP32-004821", "ESP32-SakaSense-v2", hoursAgo(0.4, now), 86, DeviceStatus.ONLINE),
                SoilSample(5.8, 58, 42, 82), 30, 0,
            ),
            Spec(
                PlotEntity("plot-b", "farm-1", "Zone B — Tomato", "Tomato", "0.3 ha", PlotStatus.NEEDS_ATTENTION, 3, 1, 1, 2),
                DeviceEntity("dev-2", "plot-b", "KS-ESP32-004822", "ESP32-SakaSense-v2", hoursAgo(6.0, now), 62, DeviceStatus.SYNCING),
                SoilSample(6.2, 48, 40, 58), 30, 0,
            ),
            Spec(
                PlotEntity("plot-c", "farm-1", "Zone C — Eggplant", "Eggplant", "0.2 ha", PlotStatus.ACTION_REQUIRED, 1, 3, 3, 1),
                DeviceEntity("dev-3", "plot-c", "KS-ESP32-004823", "ESP32-SakaSense-v2", daysAgoAt(2, 14), 41, DeviceStatus.OFFLINE),
                SoilSample(5.2, 32, 38, 38), 30, 2,
            ),
            Spec(
                PlotEntity("plot-d", "farm-2", "Balcony Container 1", "Basil", "20 L pot", PlotStatus.HEALTHY, 1, 1, 1, 2),
                DeviceEntity("dev-4", "plot-d", "KS-ESP32-004901", "ESP32-SakaSense-v2", hoursAgo(1.2, now), 91, DeviceStatus.ONLINE),
                SoilSample(6.6, 65, 42, 70), 14, 0,
            ),
            Spec(
                PlotEntity("plot-e", "farm-2", "Balcony Container 2", "Tomato", "30 L pot", PlotStatus.NEEDS_ATTENTION, 2, 1, 1, 2),
                DeviceEntity("dev-5", "plot-e", "KS-ESP32-004902", "ESP32-SakaSense-v2", hoursAgo(18.0, now), 55, DeviceStatus.OFFLINE),
                SoilSample(6.1, 48, 22, 50), 14, 1,
            ),
        )

        db.plots().upsert(*specs.map { it.plot }.toTypedArray())
        db.devices().upsert(*specs.map { it.device }.toTypedArray())

        specs.forEach { spec ->
            val farmType = if (spec.plot.farmId == "farm-2") FarmType.URBAN_CONTAINER else FarmType.RURAL_FIELD
            // Same wave + noise shape as the dashboard's generator, so charts look alike.
            val readings = (spec.days downTo spec.lastDay).map { a ->
                val w = 2 * sin(a / 3.0)
                ReadingEntity(
                    id = "r-${spec.plot.id}-$a",
                    plotId = spec.plot.id,
                    deviceSerial = spec.device.serialNumber,
                    timestamp = daysAgoAt(a, 7 + a % 3),
                    ph = ((spec.base.ph + 0.05 * w + (rnd.nextDouble() - 0.5) * 0.15) * 100).roundToInt() / 100.0,
                    n = (spec.base.n + 1.5 * w + (rnd.nextDouble() - 0.5) * 4).roundToInt(),
                    p = (spec.base.p + 0.8 * w + (rnd.nextDouble() - 0.5) * 2).roundToInt(),
                    k = (spec.base.k + 1.2 * w + (rnd.nextDouble() - 0.5) * 3).roundToInt(),
                    source = if (a > 3 && a % 5 == 0) ReadingSource.SYNCED_SD_CARD else ReadingSource.LIVE,
                    temperatureC = temperature(w, rnd),
                    humidityPct = humidity(w, rnd),
                )
            }
            db.readings().insert(readings)

            // Recommendation history: one per week, newest last.
            val recs = readings.filterIndexed { i, _ -> (readings.size - 1 - i) % 7 == 0 }.map { r ->
                SoilAnalyzer.analyze(r.toSample(), spec.plot.cropCurrent, farmType)
                    .toRecommendation(spec.plot.id, r.id, r.timestamp, DeviceVerdict(null, null))
            }
            db.recommendations().insert(recs)
            val latest = SoilAnalyzer.analyze(readings.last().toSample(), spec.plot.cropCurrent, farmType)
            db.plots().setStatus(spec.plot.id, latest.status)
        }

        db.alerts().insert(
            listOf(
                AlertEntity("alert-1", "plot-c", AlertType.DEFICIENCY, "Nitrogen deficiency — Action Required in Zone C", daysAgoAt(2, 14)),
                AlertEntity("alert-2", "plot-b", AlertType.DEFICIENCY, "Nitrogen trending low in Zone B", hoursAgo(6.0, now)),
                AlertEntity("alert-3", "plot-c", AlertType.DEVICE_OFFLINE, "Device KS-ESP32-004823 last synced 2 days ago", daysAgoAt(1, 8)),
                AlertEntity("alert-4", "plot-e", AlertType.CHECK_IN_DUE, "Recommended check-in overdue for Container 2", hoursAgo(12.0, now)),
                AlertEntity("alert-5", "plot-a", AlertType.DEFICIENCY, "Brief pH dip detected (resolved)", daysAgoAt(10, 11), daysAgoAt(9, 16)),
            )
        )
        repo.refreshAlerts(now)
    }
}
