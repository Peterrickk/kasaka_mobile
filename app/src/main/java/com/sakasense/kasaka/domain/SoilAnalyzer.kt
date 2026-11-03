package com.sakasense.kasaka.domain

import com.sakasense.kasaka.data.local.FarmType
import com.sakasense.kasaka.data.local.PlotStatus
import com.sakasense.kasaka.data.local.RankedCrop
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class SoilSample(val ph: Double, val n: Int, val p: Int, val k: Int)

/** Temperature (°C) and relative humidity (%) sent with a reading. Shown to the farmer; not used in the analysis. */
data class Climate(val temperatureC: Double?, val humidityPct: Double?) {
    val isEmpty: Boolean get() = temperatureC == null && humidityPct == null
}

/** The four reading boxes of the Kasaka Guidebook. */
enum class Level(val box: String) {
    LOW("✕ LOW"),
    SLIGHTLY_LOW("! SLIGHTLY LOW"),
    GOOD("✓ GOOD"),
    HIGH("▲ HIGH");

    val isShort: Boolean get() = this == LOW || this == SLIGHTLY_LOW
}

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
    /** The crop the prescription was written for (the plot's current crop, or the best match from the book). */
    val targetCrop: String,
    val condition: SoilCondition,
    /** ! SLIGHTLY LOW: the book says to use half the fix. */
    val halfDose: Boolean,
    val fertilizer: Fertilizer,
    val insight: String,
) {
    /** Kasaka Guidebook code, 1–7. */
    val code: Int get() = condition.code
}

/**
 * On-phone version of SakaSense AI's rule engine, following the Kasaka Guidebook: reading boxes,
 * the 1–7 code, the crop's Fix-it prescription and crop ranking. Runs fully offline.
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

    /** The book's tables use one decimal for pH, so 5.45 is read as 5.5, like the farmer sees it. */
    private fun bookPh(ph: Double) = (ph * 10).roundToInt() / 10.0

    fun level(s: SoilSample, nutrient: Nutrient, crop: CropProfile): Level = crop.row(nutrient).level(
        when (nutrient) {
            Nutrient.PH -> bookPh(s.ph)
            Nutrient.N -> s.n.toDouble()
            Nutrient.P -> s.p.toDouble()
            Nutrient.K -> s.k.toDouble()
        }
    )

    fun band(s: SoilSample, nutrient: Nutrient, crop: CropProfile): NutrientBand {
        val level = level(s, nutrient, crop)
        val row = crop.row(nutrient)
        val value = when (nutrient) {
            Nutrient.PH -> s.ph
            Nutrient.N -> s.n.toDouble()
            Nutrient.P -> s.p.toDouble()
            Nutrient.K -> s.k.toDouble()
        }
        val label = if (nutrient == Nutrient.PH) {
            when (level) {
                Level.LOW -> "Too sour"
                Level.SLIGHTLY_LOW -> "A bit sour"
                Level.GOOD -> "Good"
                Level.HIGH -> "Alkaline"
            }
        } else {
            when (level) {
                Level.LOW -> "Low"
                Level.SLIGHTLY_LOW -> "Slightly low"
                Level.GOOD -> "Good"
                Level.HIGH -> "High"
            }
        }
        return NutrientBand(nutrient, value, level, label, "${num(row.goodFrom, nutrient)} – ${num(row.goodTo, nutrient)}")
    }

    fun bands(s: SoilSample, crop: CropProfile): List<NutrientBand> = Nutrient.entries.map { band(s, it, crop) }

    /** N, P and K that read ✕ LOW or ! SLIGHTLY LOW. */
    fun shortNutrients(s: SoilSample, crop: CropProfile): List<Nutrient> =
        listOf(Nutrient.N, Nutrient.P, Nutrient.K).filter { level(s, it, crop).isShort }

    /**
     * The Guidebook code (page 27): pH comes first. Low or slightly low pH is code 2 (for sweet potato,
     * only below 4.5) and high pH is code 3. Otherwise two or more short nutrients is code 7, one is 4, 5 or 6.
     */
    fun condition(s: SoilSample, crop: CropProfile): SoilCondition {
        val ph = level(s, Nutrient.PH, crop)
        val sour = if (crop.limeOnlyWhenLow) ph == Level.LOW else ph.isShort
        if (sour) return SoilCondition.ACIDIC
        if (ph == Level.HIGH) return SoilCondition.ALKALINE
        val short = shortNutrients(s, crop)
        return when {
            short.size >= 2 -> SoilCondition.MULTI_LOW
            short.isEmpty() -> SoilCondition.BALANCED
            else -> when (short.single()) {
                Nutrient.N -> SoilCondition.N_LOW
                Nutrient.P -> SoilCondition.P_LOW
                else -> SoilCondition.K_LOW
            }
        }
    }

    /** ! SLIGHTLY LOW on the reading behind the code means half the fix. */
    fun halfDose(s: SoilSample, crop: CropProfile, condition: SoilCondition): Boolean = when (condition) {
        SoilCondition.ACIDIC -> level(s, Nutrient.PH, crop) == Level.SLIGHTLY_LOW
        SoilCondition.N_LOW -> level(s, Nutrient.N, crop) == Level.SLIGHTLY_LOW
        SoilCondition.P_LOW -> level(s, Nutrient.P, crop) == Level.SLIGHTLY_LOW
        SoilCondition.K_LOW -> level(s, Nutrient.K, crop) == Level.SLIGHTLY_LOW
        else -> false
    }

    /** 0–100 suitability of [s] for [crop], from its reading boxes and how well it copes with sour soil. */
    fun suitability(s: SoilSample, crop: CropProfile): Int {
        val phScore = when (level(s, Nutrient.PH, crop)) {
            Level.GOOD -> 100.0
            Level.SLIGHTLY_LOW -> when (crop.sourTolerance) {
                SourTolerance.BEST -> 85.0
                SourTolerance.FAIR -> 60.0
                SourTolerance.POOR -> 45.0
            }
            Level.LOW -> when (crop.sourTolerance) {
                SourTolerance.BEST -> 45.0
                SourTolerance.FAIR -> 20.0
                SourTolerance.POOR -> 10.0
            }
            Level.HIGH -> 30.0
        }
        fun nutrientScore(n: Nutrient) = when (level(s, n, crop)) {
            Level.GOOD -> 100.0
            Level.SLIGHTLY_LOW -> 65.0
            Level.LOW -> 30.0
            Level.HIGH -> if (n == Nutrient.N && crop.highNitrogenHurts) 40.0 else 85.0
        }
        return (0.35 * phScore + 0.25 * nutrientScore(Nutrient.N) +
            0.20 * nutrientScore(Nutrient.P) + 0.20 * nutrientScore(Nutrient.K)).roundToInt()
    }

    /** Best crops for [s]; [books] limits it to the Guidebook crops (what the device ranks). */
    fun rankCrops(s: SoilSample, top: Int = 3, books: Boolean = false): List<RankedCrop> =
        (if (books) CropProfiles.inBook else CropProfiles.all).map { RankedCrop(it.name, suitability(s, it)) }
            .sortedByDescending { it.score }
            .take(top)

    /** The plot's crop if the app knows it, else the Guidebook crop that suits this soil best. */
    fun cropFor(s: SoilSample, crop: String?): CropProfile =
        CropProfiles.find(crop) ?: CropProfiles.inBook.maxBy { suitability(s, it) }

    /**
     * @param areaM2 plot size, when known, so field amounts can also be given for the whole plot.
     */
    fun analyze(
        s: SoilSample,
        currentCrop: String? = null,
        farmType: FarmType = FarmType.RURAL_FIELD,
        areaM2: Double? = null,
    ): SoilAnalysis {
        val index = healthIndex(s)
        val ranked = rankCrops(s)
        val crop = cropFor(s, currentCrop)
        val condition = condition(s, crop)
        val half = halfDose(s, crop, condition)
        val fix = Guidebook.fix(crop.name, condition)
        val tooHigh = Guidebook.tooHigh(crop.name)
        val container = farmType == FarmType.URBAN_CONTAINER
        val short = shortNutrients(s, crop)
        val high = listOf(Nutrient.N, Nutrient.P, Nutrient.K).filter { level(s, it, crop) == Level.HIGH }

        val doses = if (half) fix.doses.map { it.copy(grams = it.grams / 2) } else fix.doses
        val alternative = fix.alternative?.let { if (half) it.copy(grams = it.grams / 2) else it }
        val amount = Doses.amount(doses, container, areaM2) +
            (alternative?.let { ", or ${Doses.grams(it.grams)} ${Doses.shortName(it.product)}" } ?: "")

        val notes = buildList {
            if (half) add("This is half the fix, because the reading is ! SLIGHTLY LOW.")
            add(fix.text)
            high.forEach { n -> tooHigh[n]?.let { add("▲ ${n.short} high: $it") } }
            if (condition == SoilCondition.ACIDIC && short.isNotEmpty()) {
                add("After liming, fix the low ${naturalJoin(short.map { it.label.lowercase() })} (codes 4–7).")
            }
            if (container) add("Container amounts are the bed amounts shared over 20 m² of soil surface.")
        }.joinToString("\n")

        val fertilizer = Fertilizer(
            product = fix.product + (alternative?.let { " (or ${Doses.shortName(it.product)})" } ?: ""),
            amount = amount,
            timing = fix.timing,
            notes = notes,
        )

        val insight = buildInsight(s, crop, currentCrop, condition, half, short, high, doses)
        return SoilAnalysis(
            sample = s,
            healthIndex = index,
            status = statusFor(index),
            bands = bands(s, crop),
            rankedCrops = ranked,
            targetCrop = crop.name,
            condition = condition,
            halfDose = half,
            fertilizer = fertilizer,
            insight = insight,
        )
    }

    private fun buildInsight(
        s: SoilSample,
        crop: CropProfile,
        requested: String?,
        condition: SoilCondition,
        half: Boolean,
        short: List<Nutrient>,
        high: List<Nutrient>,
        doses: List<Dose>,
    ): String {
        val prefix = when {
            !requested.isNullOrBlank() && CropProfiles.find(requested) == null ->
                "$requested is not in the Kasaka Guidebook, so this advice is for ${crop.name}, the best match for this soil. "
            !crop.inGuidebook ->
                "${crop.name} has no chapter in the Kasaka Guidebook, so these are the book's general amounts. Ask your agriculture technician to confirm them. "
            else -> ""
        }
        val code = "Code ${condition.code}"
        val body = when (condition) {
            SoilCondition.BALANCED -> "All four readings are good for ${crop.name}. Follow the standard plan. $code." +
                if (high.isNotEmpty()) " ${naturalJoin(high.map { it.label })} ${isAre(high)} high, so skip that fertilizer." else ""
            SoilCondition.ACIDIC -> "Soil is too sour for ${crop.name} (pH ${fmt(s.ph)}). Lime first, wait 2 weeks, then fertilize. $code." +
                if (short.isNotEmpty()) " Then fix the low ${naturalJoin(short.map { it.label.lowercase() })}." else ""
            SoilCondition.ALKALINE -> "Soil is alkaline for ${crop.name} (pH ${fmt(s.ph)}). No lime or ash; use ammonium sulfate and compost. $code."
            SoilCondition.N_LOW, SoilCondition.P_LOW, SoilCondition.K_LOW -> {
                val n = short.single()
                val d = doses.first()
                "${n.label} is ${if (half) "slightly low" else "low"} for ${crop.name}. " +
                    "Add ${Doses.grams(d.grams)} ${Doses.shortName(d.product)} per bed${if (half) " (half the fix)" else ""}. $code."
            }
            SoilCondition.MULTI_LOW -> "${naturalJoin(short.map { it.label })} are short for ${crop.name}. " +
                "Add more 14-14-14 and compost, then test again in 2 weeks. $code."
        }
        val sourNote = if (crop.limeOnlyWhenLow && condition != SoilCondition.ACIDIC && level(s, Nutrient.PH, crop) == Level.SLIGHTLY_LOW) {
            " pH ${fmt(s.ph)} is a bit sour, but ${crop.localName?.lowercase() ?: crop.name.lowercase()} copes; liming is optional."
        } else ""
        return prefix + body + sourNote
    }

    private fun isAre(items: List<*>) = if (items.size == 1) "is" else "are"

    /** "A", "A and B", "A, B and C" */
    private fun naturalJoin(items: List<String>) =
        if (items.size <= 2) items.joinToString(" and ") else items.dropLast(1).joinToString(", ") + " and " + items.last()

    private fun num(v: Double, n: Nutrient) = if (n == Nutrient.PH) fmt(v) else v.roundToInt().toString()

    private fun fmt(d: Double) = String.format(Locale.US, "%.1f", d)
}

/** Turns per-bed doses from the book into words a farmer can measure. */
object Doses {
    /** The book's amounts are for one bed, 1 m × 20 m. */
    const val BED_M2 = 20.0

    private val shortNames = mapOf(
        Products.COMPLETE to "14-14-14",
        Products.UREA to "urea",
        Products.AMMONIUM_SULFATE to "21-0-0",
        Products.AMMONIUM_PHOSPHATE to "16-20-0",
        Products.SOLOPHOS to "Solophos 0-18-0",
        Products.POTASH to "0-0-60",
        Products.LIME to "lime or dolomite",
        Products.CHICKEN_MANURE to "dried chicken manure",
        Products.ORGANIC to "chicken manure or organic fertilizer",
        Products.DOLOMITE to "dolomite",
        Products.COMPOST to "manure or compost",
    )

    fun shortName(product: String) = shortNames[product] ?: product

    // "No scale? Measuring by spoon and cup" (page 25): grams per level tablespoon and per cup.
    private val spoon = mapOf(
        Products.UREA to (10.0 to 190.0),
        Products.COMPLETE to (15.0 to 260.0),
        Products.AMMONIUM_PHOSPHATE to (15.0 to 260.0),
        Products.AMMONIUM_SULFATE to (15.0 to 260.0),
        Products.POTASH to (15.0 to 260.0),
        Products.LIME to (12.0 to 300.0),
        Products.DOLOMITE to (12.0 to 300.0),
    )

    /** "≈ 5 tbsp", "≈ 1¼ cups", or null when the book has no spoon size for the product. */
    fun measure(product: String, grams: Int): String? {
        val (tbsp, cup) = spoon[product] ?: return null
        val spoons = grams / tbsp
        if (spoons <= 8) return "≈ ${spoons.roundToInt().coerceAtLeast(1)} tbsp"
        val cups = grams / cup
        if (cups >= 4) return "≈ ${cups.roundToInt()} cups"
        val quarters = (cups * 4).roundToInt()
        val whole = quarters / 4
        val frac = when (quarters % 4) { 1 -> "¼"; 2 -> "½"; 3 -> "¾"; else -> "" }
        val text = (if (whole > 0) "$whole" else "") + frac
        return "≈ $text ${if (quarters > 4) "cups" else "cup"}"
    }

    fun grams(g: Int): String = if (g >= 1000) "${trim(g / 1000.0)} kg" else "$g g"

    private fun gramsPrecise(g: Double): String = if (g >= 1000) "${trim(g / 1000)} kg" else "${trim(g)} g"

    private fun trim(d: Double): String {
        val r = (d * 10).roundToInt() / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else String.format(Locale.US, "%.1f", r)
    }

    /** " (≈ 2½ bags)" for plot totals of 50 kg and more, since fertilizer is bought in 50-kg bags. */
    private fun bags(grams: Double): String {
        if (grams < 50_000) return ""
        val halves = (grams / 25_000).roundToInt()
        val text = "${halves / 2}" + if (halves % 2 == 1) "½" else ""
        return " (≈ $text bags)"
    }

    private fun sacks(n: Double) = if (n == 1.0) "1 sack" else "${trim(n)} sacks"

    private fun one(d: Dose, withName: Boolean): String = when {
        d.sacks > 0 -> sacks(d.sacks.toDouble()) + " " + shortName(d.product)
        else -> grams(d.grams) + (if (withName) " " + shortName(d.product) else "") +
            (measure(d.product, d.grams)?.let { " ($it)" } ?: "")
    }

    /**
     * Field: "100 g (≈ ½ cup) per bed · 25 kg for this plot". Containers: per m² of soil surface,
     * since the book only gives bed amounts.
     */
    fun amount(doses: List<Dose>, container: Boolean, areaM2: Double?): String {
        if (container) {
            return doses.joinToString(" + ") { d ->
                if (d.sacks > 0) "${sacks(d.sacks.toDouble())} ${shortName(d.product)} per 20 m²"
                else "${gramsPrecise(d.grams / BED_M2)} ${shortName(d.product)}"
            } + " per m² of soil"
        }
        val perBed = doses.joinToString(" + ") { one(it, withName = doses.size > 1) } + " per bed"
        val beds = areaM2?.takeIf { it > 0 && abs(it - BED_M2) > 0.5 }?.div(BED_M2) ?: return perBed
        val total = doses.joinToString(" + ") { d ->
            if (d.sacks > 0) "${sacks(d.sacks * beds)} ${shortName(d.product)}"
            else gramsPrecise(d.grams * beds) + bags(d.grams * beds) + (if (doses.size > 1) " " + shortName(d.product) else "")
        }
        return "$perBed · $total for this plot"
    }

    private val areaPattern = Regex("""(\d+(?:[.,]\d+)?)\s*(ha|hectares?|m²|m2|sq\.?\s?m|sqm|square\s+met(?:er|re)s?)(?![A-Za-z])""",RegexOption.IGNORE_CASE)

    /** Reads plot sizes like "0.5 ha" or "200 m²". Null when the label has no area (e.g. "20 L pot"). */
    fun parseAreaM2(label: String?): Double? {
        val m = areaPattern.find(label ?: return null) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return if (m.groupValues[2].lowercase().startsWith("h")) value * 10_000 else value
    }
}
