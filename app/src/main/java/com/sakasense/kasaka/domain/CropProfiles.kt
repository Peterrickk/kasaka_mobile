package com.sakasense.kasaka.domain

/**
 * Soil targets per crop. Nutrient targets are available N-P-K in mg/kg as reported by the
 * SakaSense NPK probe.
 *
 * These are general starting values for Philippine lowland/upland conditions. Have them
 * reviewed by an agronomist (or the Municipal Agriculture Office) before relying on them.
 */
data class CropProfile(
    val name: String,
    val phMin: Double,
    val phMax: Double,
    val nTarget: Int,
    val pTarget: Int,
    val kTarget: Int,
    val emoji: String,
)

object CropProfiles {
    val all = listOf(
        CropProfile("Rice", 5.5, 7.0, 60, 30, 60, "🌾"),
        CropProfile("Corn", 5.8, 7.0, 80, 45, 70, "🌽"),
        CropProfile("Eggplant", 5.5, 6.8, 70, 45, 75, "🍆"),
        CropProfile("Sweet potato", 5.2, 6.5, 40, 35, 70, "🍠"),
        CropProfile("Cassava", 5.0, 6.5, 35, 25, 60, "🥔"),
        CropProfile("Potato", 5.0, 6.5, 60, 40, 80, "🥔"),
        CropProfile("Okra", 6.0, 7.0, 55, 40, 60, "🌿"),
        CropProfile("Basil", 6.0, 7.0, 60, 40, 60, "🌿"),
        CropProfile("Lettuce", 6.0, 7.0, 70, 40, 60, "🥬"),
        CropProfile("Tomato", 6.0, 6.8, 70, 50, 80, "🍅"),
        CropProfile("Chili", 5.8, 6.8, 65, 45, 70, "🌶️"),
        CropProfile("Pechay", 6.0, 7.0, 70, 35, 55, "🥬"),
        CropProfile("Kangkong", 5.5, 7.0, 60, 30, 50, "🥬"),
    )

    val names = all.map { it.name }

    fun find(name: String?): CropProfile? = all.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun emoji(name: String?): String = find(name)?.emoji ?: "🌱"
}
