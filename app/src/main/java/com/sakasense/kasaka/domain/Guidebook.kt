package com.sakasense.kasaka.domain

/** What the analyzer found wrong with the soil, used to pick the matching Guidebook page. */
enum class SoilCondition { BALANCED, ACIDIC, ALKALINE, N_LOW, P_LOW, K_LOW, MULTI_LOW }

data class GuidebookEntry(
    val code: String,
    /** null = applies to any crop */
    val crop: String?,
    val condition: SoilCondition,
    val title: String,
    val soilCondition: String,
    val cultivationSteps: List<String>,
    val fertilizerNotes: String,
)

/**
 * The SakaSense Guidebook. Codes 2147, 3902, 4799, 4810, 4821, 5510 and 6123 match the codes
 * shown on the device and on the web dashboard; the rest extend coverage to every supported crop.
 */
object Guidebook {
    val entries: List<GuidebookEntry> = listOf(
        GuidebookEntry(
            "1101", "Rice", SoilCondition.BALANCED, "Rice — healthy paddy",
            "pH 5.5–7.0 · balanced N-P-K",
            listOf(
                "Level the field and keep 2–5 cm of standing water after transplanting.",
                "Transplant 20–25 day old seedlings at 20 × 20 cm spacing.",
                "Keep the field weed-free for the first 40 days.",
            ),
            "Basal complete fertilizer (14-14-14), then topdress Urea at tillering and panicle initiation.",
        ),
        GuidebookEntry(
            "1102", "Rice", SoilCondition.N_LOW, "Rice — nitrogen low",
            "N below target · yellowing lower leaves",
            listOf(
                "Drain the field to a few centimetres before applying fertilizer.",
                "Split nitrogen into 2–3 applications instead of one heavy dose.",
                "Re-flood 2–3 days after application.",
            ),
            "Topdress Urea (46-0-0) at active tillering. Use a leaf colour chart to time the next dose.",
        ),
        GuidebookEntry(
            "1201", "Corn", SoilCondition.BALANCED, "Corn — ready to plant",
            "pH 5.8–7.0 · N-hungry crop",
            listOf(
                "Plant 2–3 cm deep, 25 cm between hills, 75 cm between rows.",
                "Hill up soil around the base 3–4 weeks after emergence.",
                "Keep the field moist during tasseling and silking.",
            ),
            "Basal 14-14-14 at planting; sidedress Urea 25–30 days after emergence.",
        ),
        GuidebookEntry(
            "1202", "Corn", SoilCondition.N_LOW, "Corn — nitrogen low",
            "N below target · V-shaped yellowing on leaves",
            listOf(
                "Sidedress fertilizer 5–10 cm from the plant row, then cover with soil.",
                "Apply when soil is moist, not right before heavy rain.",
                "Re-test soil two weeks after application.",
            ),
            "Urea (46-0-0) sidedress; corn needs most of its nitrogen before tasseling.",
        ),
        GuidebookEntry(
            "2147", "Potato", SoilCondition.BALANCED, "Potato — tuber development",
            "pH 5.5–6.5 · K for tubers",
            listOf(
                "Plant seed potatoes 10–15 cm deep, 30 cm apart.",
                "Hill up soil around stems as plants grow.",
                "Harvest after foliage dies back.",
            ),
            "Apply fertilizer higher in potassium (e.g., 12-12-17) at planting and tuber set.",
        ),
        GuidebookEntry(
            "2148", "Potato", SoilCondition.K_LOW, "Potato — potassium low",
            "K below target · small tubers, weak stems",
            listOf(
                "Apply potash in a band beside the row at hilling.",
                "Avoid excess nitrogen — it grows leaves at the expense of tubers.",
                "Keep soil evenly moist during tuber bulking.",
            ),
            "Muriate of Potash (0-0-60) or 12-12-17 at hilling.",
        ),
        GuidebookEntry(
            "2301", "Sweet potato", SoilCondition.BALANCED, "Sweet potato — vine cuttings",
            "pH 5.2–6.5 · moderate N, high K",
            listOf(
                "Plant 25–30 cm vine cuttings on ridges, 25 cm apart.",
                "Lift vines once a month to stop them rooting at the nodes.",
                "Harvest 3–4 months after planting.",
            ),
            "Light nitrogen only; favour potassium (0-0-60 or 12-12-17) for root size.",
        ),
        GuidebookEntry(
            "2401", "Cassava", SoilCondition.BALANCED, "Cassava — low-input crop",
            "pH 5.0–6.5 · tolerates poor soil",
            listOf(
                "Plant 20–25 cm stem cuttings at an angle, 1 m × 1 m spacing.",
                "Weed during the first 3 months while the canopy closes.",
                "Harvest 8–12 months after planting.",
            ),
            "Complete fertilizer at planting; add potash on depleted soil.",
        ),
        GuidebookEntry(
            "3101", "Okra", SoilCondition.BALANCED, "Okra — steady harvests",
            "pH 6.0–7.0 · balanced nutrients",
            listOf(
                "Soak seeds overnight, then sow 2–3 cm deep, 30 cm apart.",
                "Harvest pods every 2 days while they are 7–10 cm long.",
                "Water deeply once or twice a week in dry spells.",
            ),
            "Basal complete fertilizer, then a light Urea sidedress at first flowering.",
        ),
        GuidebookEntry(
            "3902", "Tomato", SoilCondition.BALANCED, "Tomato — flowering and fruit",
            "pH 6.0–6.8 · P for fruiting",
            listOf(
                "Start seeds 4–5 weeks before transplanting.",
                "Transplant in the late afternoon and water immediately.",
                "Stake or cage vines early; water at the base.",
            ),
            "Use balanced fertilizer with higher phosphorus (e.g., 10-20-10) at flowering.",
        ),
        GuidebookEntry(
            "4799", "Eggplant", SoilCondition.BALANCED, "Eggplant — borderline nutrients",
            "Borderline nutrients",
            listOf(
                "Continue regular irrigation schedule.",
                "Re-test soil in one week.",
            ),
            "Maintenance dose of complete fertilizer.",
        ),
        GuidebookEntry(
            "4810", "Eggplant", SoilCondition.ACIDIC, "Eggplant — rising acidity",
            "Rising acidity",
            listOf(
                "Broadcast lime and incorporate lightly.",
                "Delay nitrogen-heavy fertilizers until pH recovers.",
            ),
            "Lime first, then resume complete fertilizer program.",
        ),
        GuidebookEntry(
            "4821", "Eggplant", SoilCondition.MULTI_LOW, "Eggplant — acidic, N & K deficient",
            "pH acidic · N & K deficient",
            listOf(
                "Correct pH with agricultural lime 1–2 weeks before planting.",
                "Stake plants early; prune lower leaves.",
                "Harvest fruits regularly to encourage more flowering.",
            ),
            "After liming, apply Urea + MOP. Re-test soil in 7–10 days.",
        ),
        GuidebookEntry(
            "5510", "Basil", SoilCondition.BALANCED, "Basil — container growing",
            "pH 6.0–7.0 · container-friendly",
            listOf(
                "Keep potting mix moist but not waterlogged.",
                "Pinch tips to promote bushy growth.",
                "Provide 4–6 hours of sun daily.",
            ),
            "Light weekly compost tea or dilute organic liquid fertilizer.",
        ),
        GuidebookEntry(
            "5520", "Lettuce", SoilCondition.BALANCED, "Lettuce — leafy growth",
            "pH 6.0–7.0 · N for leaves",
            listOf(
                "Sow in trays and transplant at 3–4 true leaves, 20 cm apart.",
                "Give afternoon shade in hot months to prevent bolting.",
                "Harvest outer leaves or the whole head at 30–45 days.",
            ),
            "Light, frequent nitrogen feeding; compost or diluted Urea every 2 weeks.",
        ),
        GuidebookEntry(
            "6123", "Tomato", SoilCondition.P_LOW, "Tomato — phosphorus low",
            "pH 6.0–6.8 · P low for fruiting",
            listOf(
                "Stake or cage vines early.",
                "Water at the base — avoid wetting leaves.",
                "Remove suckers below the first flower cluster.",
            ),
            "Boost phosphorus with soluble 14-14-14 during flowering/fruit set.",
        ),
        GuidebookEntry(
            "6201", "Chili", SoilCondition.BALANCED, "Chili — fruiting",
            "pH 5.8–6.8 · steady K for fruit",
            listOf(
                "Transplant 30–40 day old seedlings, 50 cm apart.",
                "Mulch to keep soil moist and roots cool.",
                "Pick fruits regularly to keep the plant producing.",
            ),
            "Complete fertilizer at transplant; potash-rich feed once fruiting starts.",
        ),
        GuidebookEntry(
            "6202", "Chili", SoilCondition.P_LOW, "Chili — phosphorus low",
            "P below target · few flowers, slow roots",
            listOf(
                "Place phosphorus fertilizer near the roots — it moves little in soil.",
                "Avoid waterlogging, which locks up phosphorus.",
                "Re-test soil 2 weeks after application.",
            ),
            "Ammonium phosphate (16-20-0) or 14-14-14 banded beside plants.",
        ),
        GuidebookEntry(
            "7101", "Pechay", SoilCondition.BALANCED, "Pechay — quick leafy crop",
            "pH 6.0–7.0 · N for leaves",
            listOf(
                "Direct sow or transplant at 15 × 15 cm spacing.",
                "Water lightly every morning.",
                "Harvest 25–30 days after transplanting.",
            ),
            "Compost at planting plus a light Urea side-dress after 2 weeks.",
        ),
        GuidebookEntry(
            "7201", "Kangkong", SoilCondition.BALANCED, "Kangkong — continuous harvest",
            "pH 5.5–7.0 · moist soil",
            listOf(
                "Plant cuttings or seeds in constantly moist soil.",
                "Harvest tops every 2–3 weeks to keep new shoots coming.",
                "Keep beds weeded and well watered.",
            ),
            "Light nitrogen after each harvest — compost tea or diluted Urea.",
        ),
        GuidebookEntry(
            "9001", null, SoilCondition.ACIDIC, "Acidic soil — lime first",
            "pH below crop range · nutrients locked up",
            listOf(
                "Broadcast agricultural lime or dolomite evenly over the plot.",
                "Mix it into the top 15 cm of soil and water it in.",
                "Wait 1–2 weeks before applying nitrogen fertilizer.",
                "Re-test pH after 2–4 weeks.",
            ),
            "Lime does not feed the crop — it unlocks the fertilizer you apply afterwards.",
        ),
        GuidebookEntry(
            "9002", null, SoilCondition.ALKALINE, "Alkaline soil — lower pH gently",
            "pH above crop range · iron and P less available",
            listOf(
                "Work in compost or well-rotted manure.",
                "Use acid-forming fertilizer such as ammonium sulfate (21-0-0).",
                "Avoid adding lime or wood ash.",
            ),
            "Switch nitrogen source to ammonium sulfate until pH returns to range.",
        ),
        GuidebookEntry(
            "9003", null, SoilCondition.MULTI_LOW, "Several nutrients low",
            "Two or more of N, P, K below target",
            listOf(
                "Apply a complete fertilizer as a basal dose.",
                "Add compost to improve how well soil holds nutrients.",
                "Re-test after 2 weeks and top up the nutrient that is still low.",
            ),
            "Complete 14-14-14 basal, then targeted top-dressing.",
        ),
        GuidebookEntry(
            "9010", null, SoilCondition.N_LOW, "Nitrogen low",
            "N below target · pale, slow growth",
            listOf(
                "Apply nitrogen in split doses, not one heavy application.",
                "Water in after application to reduce losses.",
            ),
            "Urea (46-0-0) or ammonium sulfate (21-0-0).",
        ),
        GuidebookEntry(
            "9011", null, SoilCondition.P_LOW, "Phosphorus low",
            "P below target · weak roots, few flowers",
            listOf(
                "Place phosphorus near the root zone at planting.",
                "Keep pH in range — phosphorus locks up in acidic soil.",
            ),
            "Ammonium phosphate (16-20-0) or Solophos (0-18-0).",
        ),
        GuidebookEntry(
            "9012", null, SoilCondition.K_LOW, "Potassium low",
            "K below target · scorched leaf edges, poor fruit",
            listOf(
                "Apply potash beside the row and work into the soil.",
                "Mulch to reduce potassium leaching in heavy rain.",
            ),
            "Muriate of Potash (0-0-60).",
        ),
        GuidebookEntry(
            "9000", null, SoilCondition.BALANCED, "Balanced soil — maintain",
            "pH and nutrients within range",
            listOf(
                "Keep a regular watering schedule.",
                "Add compost each season to maintain soil structure.",
                "Re-test every 1–2 weeks during the growing season.",
            ),
            "Maintenance dose of complete fertilizer only if the crop needs it.",
        ),
    )

    fun find(code: String): GuidebookEntry? = entries.firstOrNull { it.code == code }

    /** Most specific page for a crop + condition, falling back to a general page. */
    fun match(crop: String?, condition: SoilCondition): GuidebookEntry =
        entries.firstOrNull { it.crop.equals(crop, true) && it.condition == condition }
            ?: entries.firstOrNull { it.crop == null && it.condition == condition }
            ?: entries.firstOrNull { it.crop.equals(crop, true) && it.condition == SoilCondition.BALANCED }
            ?: entries.first { it.code == "9000" }
}
