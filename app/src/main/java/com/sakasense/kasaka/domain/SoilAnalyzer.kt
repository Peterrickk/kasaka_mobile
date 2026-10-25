package com.sakasense.kasaka.domain

import com.sakasense.kasaka.data.local.FarmType
import com.sakasense.kasaka.data.local.PlotStatus
import com.sakasense.kasaka.data.local.RankedCrop
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class SoilSample(val ph: Double, val n: Int, val p: Int, val k: Int)

enum class Level { LOW, FAIR, GOOD, HIGH }

enum class Nutrient(val label: String, val short: String, val unit: String) {
    PH("pH", "pH", ""),
    N("Nitrogen", "N", "mg/kg"),
    P("Phosphorus", "P", "mg/kg"),
    K("Potassium", "K", "mg/kg"),
}

data class NutrientBand(val nutrient: Nutrient, val value: Double, val level: Level, val label: String, val target: String)

data class Fertilizer(val product: String, val amount: String, val timing: String, val notes: String)

data class SoilAnalysis(
    val sample: SoilSample,
    val healthIndex: Int,
    val status: PlotStatus,
    val bands: List<NutrientBand>,
    val rankedCrops: List<RankedCrop>,
    /** The crop the prescription was written for (the plot's current crop, or the top-ranked one). */
    val targetCrop: String,
    val condition: SoilCondition,
    val fertilizer: Fertilizer,
    val guidebook: GuidebookEntry,
    val insight: String,
)

/**
 * On-phone version of SakaSense AI's rule engine: soil health index, crop ranking,
 * fertilizer prescription and Guidebook lookup. Runs fully offline.
 *
 * The health index is the same formula the web dashboard uses (computeSoilHealthIndex),
 * so a reading scores identically on the device, the app and sakasense.com.
 */
object SoilAnalyzer {
    // Reference targets of the health index (dashboard formula).
    private const val PH_OPTIMAL = 6.5
    private const val N_REF = 80.0
    private const val P_REF = 50.0
    private const val K_REF = 80.0
    /** How far outside a crop's pH range before liming / acidifying is advised. */
    private const val PH_MARGIN = 0.1

    fun healthIndex(s: SoilSample): Int = (
        0.30 * (100 - min(100.0, 40 * abs(s.ph - PH_OPTIMAL))) +
            0.25 * min(100.0, s.n / N_REF * 100) +
            0.20 * min(100.0, s.p / P_REF * 100) +
            0.25 * min(100.0, s.k / K_REF * 100)
        ).roundToInt()

    fun statusFor(index: Int): PlotStatus = when {
        index >= 78 -> PlotStatus.HEALTHY
        index >= 60 -> PlotStatus.NEEDS_ATTENTION
        else -> PlotStatus.ACTION_REQUIRED
    }

    /** pH judged against the crop's range (or 6.0–7.0 when no crop is known). */
    fun phBand(ph: Double, crop: CropProfile? = null): NutrientBand {
        val min = crop?.phMin ?: 6.0
        val max = crop?.phMax ?: 7.0
        val (level, label) = when {
            ph < min - PH_MARGIN -> Level.LOW to "Too acidic"
            ph < min -> Level.FAIR to "Slightly acidic"
            ph <= max -> Level.GOOD to "Optimal"
            ph <= max + PH_MARGIN -> Level.FAIR to "Slightly alkaline"
            else -> Level.HIGH to "Too alkaline"
        }
        return NutrientBand(Nutrient.PH, ph, level, label, "${fmt(min)} – ${fmt(max)}")
    }

    fun nutrientBand(nutrient: Nutrient, value: Int, target: Int): NutrientBand {
        val r = value.toDouble() / target
        val (level, label) = when {
            r < 0.6 -> Level.LOW to "Low"
            r < 0.85 -> Level.FAIR to "Fair"
            r <= 1.6 -> Level.GOOD to "Good"
            else -> Level.HIGH to "High"
        }
        return NutrientBand(nutrient, value.toDouble(), level, label, "≥ $target")
    }

    fun bands(s: SoilSample, crop: CropProfile? = null): List<NutrientBand> = listOf(
        phBand(s.ph, crop),
        nutrientBand(Nutrient.N, s.n, crop?.nTarget ?: N_REF.toInt()),
        nutrientBand(Nutrient.P, s.p, crop?.pTarget ?: P_REF.toInt()),
        nutrientBand(Nutrient.K, s.k, crop?.kTarget ?: K_REF.toInt()),
    )

    /** 0–100 suitability of [s] for [crop]. */
    fun suitability(s: SoilSample, crop: CropProfile): Int {
        val phScore = when {
            s.ph < crop.phMin -> 100 - 35 * (crop.phMin - s.ph)
            s.ph > crop.phMax -> 100 - 35 * (s.ph - crop.phMax)
            else -> 100.0
        }.coerceIn(0.0, 100.0)
        fun nutrientScore(v: Int, target: Int): Double {
            val r = v.toDouble() / target
            return when {
                r < 1 -> 100 * r
                r <= 1.6 -> 100.0
                else -> 100 - (r - 1.6) * 60
            }.coerceIn(0.0, 100.0)
        }
        return (0.35 * phScore + 0.25 * nutrientScore(s.n, crop.nTarget) +
            0.20 * nutrientScore(s.p, crop.pTarget) + 0.20 * nutrientScore(s.k, crop.kTarget)).roundToInt()
    }

    fun rankCrops(s: SoilSample, top: Int = 3): List<RankedCrop> =
        CropProfiles.all.map { RankedCrop(it.name, suitability(s, it)) }
            .sortedByDescending { it.score }
            .take(top)

    fun analyze(s: SoilSample, currentCrop: String? = null, farmType: FarmType = FarmType.RURAL_FIELD): SoilAnalysis {
        val index = healthIndex(s)
        val ranked = rankCrops(s)
        val crop = CropProfiles.find(currentCrop) ?: CropProfiles.find(ranked.first().crop)!!
        val container = farmType == FarmType.URBAN_CONTAINER

        val nDef = deficit(s.n, crop.nTarget)
        val pDef = deficit(s.p, crop.pTarget)
        val kDef = deficit(s.k, crop.kTarget)
        val lows = listOf(Nutrient.N to nDef, Nutrient.P to pDef, Nutrient.K to kDef).filter { it.second > 0.15 }
        val worst = maxOf(nDef, pDef, kDef)

        val condition = when {
            s.ph < crop.phMin - PH_MARGIN -> if (lows.size >= 2) SoilCondition.MULTI_LOW else SoilCondition.ACIDIC
            s.ph > crop.phMax + PH_MARGIN -> SoilCondition.ALKALINE
            lows.size >= 2 -> SoilCondition.MULTI_LOW
            lows.size == 1 -> when (lows.single().first) {
                Nutrient.N -> SoilCondition.N_LOW
                Nutrient.P -> SoilCondition.P_LOW
                else -> SoilCondition.K_LOW
            }
            else -> SoilCondition.BALANCED
        }
        val guide = Guidebook.match(crop.name, condition)
        val acidic = s.ph < crop.phMin - PH_MARGIN

        val timing = when {
            acidic -> "1–2 weeks before fertilizing"
            worst > 0.45 -> "Within 3 days"
            worst > 0.25 -> "Within 5 days"
            else -> "Within 7 days (basal)"
        }
        val fertilizer = when {
            acidic -> Fertilizer(
                product = if (container) "Dolomite lime" else "Agricultural lime",
                amount = if (container) "1 tbsp per 10 L of soil" else "0.5–1 ton / ha",
                timing = timing,
                notes = "pH ${fmt(s.ph)} is below the ${crop.name} range (${fmt(crop.phMin)}–${fmt(crop.phMax)}). " +
                    "Lime first so the fertilizer you apply afterwards can be absorbed.",
            )
            s.ph > crop.phMax + PH_MARGIN -> Fertilizer(
                product = "Ammonium sulfate 21-0-0",
                amount = if (container) "1 tsp per 2 L water" else "1–2 bags / ha",
                timing = timing,
                notes = "pH ${fmt(s.ph)} is above the ${crop.name} range. Use an acid-forming nitrogen source and add compost.",
            )
            lows.isEmpty() -> Fertilizer(
                product = if (container) "Compost tea" else "Complete 14-14-14 (maintenance)",
                amount = if (container) "200–300 mL per pot" else "1 bag / ha",
                timing = if (container) "Weekly" else "Next scheduled feeding",
                notes = "Soil is balanced for ${crop.name}. A light maintenance feed is enough.",
            )
            else -> {
                val product = when (lows.map { it.first }.toSet()) {
                    setOf(Nutrient.N) -> "Urea 46-0-0"
                    setOf(Nutrient.P) -> "Ammonium phosphate 16-20-0"
                    setOf(Nutrient.K) -> "Muriate of Potash 0-0-60"
                    setOf(Nutrient.N, Nutrient.K) -> "Urea + Muriate of Potash"
                    else -> "Complete 14-14-14"
                }
                val bags = when {
                    worst > 0.45 -> "3–4 bags / ha"
                    worst > 0.25 -> "2–3 bags / ha"
                    else -> "1–2 bags / ha"
                }
                Fertilizer(
                    product = if (container) "$product (soluble)" else product,
                    amount = if (container) "1 tsp per 2 L water" else bags,
                    timing = timing,
                    notes = naturalJoin(lows.map { it.first.label }) +
                        (if (lows.size == 1) " is" else " are") + " below what ${crop.name} needs. Water thoroughly after applying.",
                )
            }
        }

        val insight = buildInsight(s, crop, condition, lows.map { it.first }, fertilizer, guide)
        return SoilAnalysis(
            sample = s,
            healthIndex = index,
            status = statusFor(index),
            bands = bands(s, crop),
            rankedCrops = ranked,
            targetCrop = crop.name,
            condition = condition,
            fertilizer = fertilizer,
            guidebook = guide,
            insight = insight,
        )
    }

    private fun buildInsight(
        s: SoilSample,
        crop: CropProfile,
        condition: SoilCondition,
        lows: List<Nutrient>,
        f: Fertilizer,
        g: GuidebookEntry,
    ): String = when (condition) {
        SoilCondition.BALANCED ->
            "Soil is well suited for ${crop.name}. Maintain current care — re-test in 5–7 days."
        SoilCondition.ACIDIC ->
            "Soil is too acidic for ${crop.name} (pH ${fmt(s.ph)}) — apply lime before any nitrogen, per Guidebook ${g.code}."
        SoilCondition.ALKALINE ->
            "Soil is too alkaline for ${crop.name} (pH ${fmt(s.ph)}) — switch to ${f.product}, per Guidebook ${g.code}."
        else -> {
            val what = naturalJoin(lows.map { it.label })
            val verb = if (lows.size == 1) "is" else "are"
            if (s.ph < crop.phMin - PH_MARGIN) {
                // The prescription is lime here; fertilizer comes after the pH recovers.
                "Soil is acidic and $what $verb low for ${crop.name} — apply ${f.product.lowercase()} first, then fertilize. See Guidebook ${g.code}."
            } else {
                "$what $verb low for ${crop.name} — apply ${f.product} ${f.timing.lowercase()}, per Guidebook ${g.code}."
            }
        }
    }

    /** "A", "A and B", "A, B and C" */
    private fun naturalJoin(items: List<String>) =
        if (items.size <= 2) items.joinToString(" and ") else items.dropLast(1).joinToString(", ") + " and " + items.last()

    private fun deficit(value: Int, target: Int) = ((target - value).toDouble() / target).coerceAtLeast(0.0)

    private fun fmt(d: Double) = String.format(Locale.US, "%.1f", d)
}
