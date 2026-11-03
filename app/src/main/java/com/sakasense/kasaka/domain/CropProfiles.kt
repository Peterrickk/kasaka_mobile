package com.sakasense.kasaka.domain

/**
 * One row of a crop's reading table in the Kasaka Guidebook:
 * below [lowBelow] is ✕ LOW, below [goodFrom] is ! SLIGHTLY LOW, up to [goodTo] is ✓ GOOD, above it ▲ HIGH.
 */
data class ReadingRow(val lowBelow: Double, val goodFrom: Double, val goodTo: Double) {
    fun level(value: Double): Level = when {
        value < lowBelow -> Level.LOW
        value < goodFrom -> Level.SLIGHTLY_LOW
        value <= goodTo -> Level.GOOD
        else -> Level.HIGH
    }
}

/** How well a crop copes with sour soil ("Which crop suits my soil?", page 7). */
enum class SourTolerance { POOR, FAIR, BEST }

/**
 * Soil targets per crop, copied from the crop's "reading your numbers" page in the Kasaka Guidebook
 * (docs/guidebook/kasaka-guidebook.html). N, P and K are mg/kg as reported by the SakaSense probe.
 * The book is the source of truth: change it first, then these numbers.
 */
data class CropProfile(
    val name: String,
    val localName: String?,
    val emoji: String,
    val ph: ReadingRow,
    /** The "best" pH shown inside the ✓ GOOD box. */
    val bestPh: String,
    val n: ReadingRow,
    val p: ReadingRow,
    val k: ReadingRow,
    val sourTolerance: SourTolerance,
    /** Code 2 (lime) only when pH is ✕ LOW; a bit sour is tolerated (sweet potato). */
    val limeOnlyWhenLow: Boolean = false,
    /** ▲ HIGH nitrogen hurts the harvest (sweet potato: vines, not roots; ampalaya: leaves, not fruit). */
    val highNitrogenHurts: Boolean = false,
    /** Caption printed in the ▲ HIGH nitrogen box, if any. */
    val highNitrogenNote: String? = null,
    /** False for crops the app supported before the Kasaka Guidebook; they get the book's general amounts. */
    val inGuidebook: Boolean = true,
) {
    fun row(nutrient: Nutrient): ReadingRow = when (nutrient) {
        Nutrient.PH -> ph
        Nutrient.N -> n
        Nutrient.P -> p
        Nutrient.K -> k
    }
}

object CropProfiles {
    val all = listOf(
        CropProfile(
            "Kangkong", null, "🥬",
            ph = ReadingRow(5.0, 5.5, 7.0), bestPh = "5.5–6.5",
            n = ReadingRow(25.0, 40.0, 100.0), p = ReadingRow(8.0, 15.0, 40.0), k = ReadingRow(50.0, 80.0, 200.0),
            sourTolerance = SourTolerance.FAIR,
        ),
        CropProfile(
            "Pechay", "Petsay", "🥬",
            ph = ReadingRow(5.0, 5.5, 7.0), bestPh = "6.0–6.5",
            n = ReadingRow(30.0, 50.0, 110.0), p = ReadingRow(12.0, 20.0, 50.0), k = ReadingRow(60.0, 100.0, 220.0),
            sourTolerance = SourTolerance.POOR,
        ),
        CropProfile(
            "Sweet potato", "Kamote", "🍠",
            ph = ReadingRow(4.5, 5.5, 6.8), bestPh = "5.6–6.5",
            n = ReadingRow(15.0, 25.0, 60.0), p = ReadingRow(8.0, 15.0, 40.0), k = ReadingRow(75.0, 120.0, 250.0),
            sourTolerance = SourTolerance.BEST, limeOnlyWhenLow = true, highNitrogenHurts = true,
            highNitrogenNote = "all vines, few roots",
        ),
        CropProfile(
            "Okra", null, "🌿",
            ph = ReadingRow(5.0, 5.5, 7.0), bestPh = "6.0–6.8",
            n = ReadingRow(25.0, 40.0, 90.0), p = ReadingRow(12.0, 20.0, 50.0), k = ReadingRow(60.0, 100.0, 220.0),
            sourTolerance = SourTolerance.FAIR,
        ),
        CropProfile(
            "Eggplant", "Talong", "🍆",
            ph = ReadingRow(5.0, 5.5, 6.8), bestPh = "6.0–6.5",
            n = ReadingRow(30.0, 50.0, 110.0), p = ReadingRow(12.0, 20.0, 50.0), k = ReadingRow(70.0, 110.0, 230.0),
            sourTolerance = SourTolerance.FAIR,
        ),
        CropProfile(
            "Ampalaya", "Palia", "🥒",
            ph = ReadingRow(5.0, 5.5, 6.5), bestPh = "5.8–6.5",
            n = ReadingRow(25.0, 40.0, 100.0), p = ReadingRow(12.0, 20.0, 50.0), k = ReadingRow(60.0, 100.0, 200.0),
            sourTolerance = SourTolerance.FAIR, highNitrogenHurts = true,
        ),
        CropProfile(
            "Corn", "Mais", "🌽",
            ph = ReadingRow(5.0, 5.5, 7.0), bestPh = "5.8–6.8",
            n = ReadingRow(30.0, 50.0, 110.0), p = ReadingRow(10.0, 15.0, 40.0), k = ReadingRow(50.0, 80.0, 200.0),
            sourTolerance = SourTolerance.FAIR,
        ),
        // Crops of the earlier app versions that the book has no chapter for. Their targets are the old
        // app's (pH range; N, P, K target with the old 60 % / 85 % / 160 % bands), so readings judge as before.
        other("Rice", "🌾", 5.5, 7.0, 60, 30, 60, SourTolerance.FAIR),
        other("Tomato", "🍅", 6.0, 6.8, 70, 50, 80, SourTolerance.FAIR),
        other("Potato", "🥔", 5.0, 6.5, 60, 40, 80, SourTolerance.FAIR),
        other("Cassava", "🥔", 5.0, 6.5, 35, 25, 60, SourTolerance.BEST),
        other("Chili", "🌶️", 5.8, 6.8, 65, 45, 70, SourTolerance.FAIR),
        other("Lettuce", "🥬", 6.0, 7.0, 70, 40, 60, SourTolerance.POOR),
        other("Basil", "🌿", 6.0, 7.0, 60, 40, 60, SourTolerance.POOR),
    )

    private fun other(name: String, emoji: String, phMin: Double, phMax: Double, n: Int, p: Int, k: Int, sour: SourTolerance): CropProfile {
        fun row(t: Int) = ReadingRow(Math.round(t * 0.6).toDouble(), Math.round(t * 0.85).toDouble(), Math.round(t * 1.6).toDouble())
        return CropProfile(
            name, null, emoji,
            ph = ReadingRow(Math.round((phMin - 0.5) * 10) / 10.0, phMin, phMax), bestPh = "${phMin}–${phMax}",
            n = row(n), p = row(p), k = row(k),
            sourTolerance = sour, inGuidebook = false,
        )
    }

    val names = all.map { it.name }

    /** The crops with a chapter in the Kasaka Guidebook. */
    val inBook = all.filter { it.inGuidebook }

    fun find(name: String?): CropProfile? = all.firstOrNull {
        it.name.equals(name, ignoreCase = true) || it.localName.equals(name, ignoreCase = true)
    }

    fun emoji(name: String?): String = find(name)?.emoji ?: "🌱"
}
