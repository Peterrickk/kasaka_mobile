package com.sakasense.kasaka.domain

/**
 * What the analyzer found, in the order of the Kasaka Guidebook's seven codes (page 27).
 * Every crop uses the same codes; each crop's Fix-it page says what to do for it.
 */
enum class SoilCondition(val code: Int, val title: String, val summary: String) {
    BALANCED(1, "All good", "Follow the standard plan. Nothing to add."),
    ACIDIC(2, "Too sour (acidic)", "Lime first (page 36), wait 2 weeks, then fertilize."),
    ALKALINE(3, "Alkaline", "No lime or ash. Use ammonium sulfate and compost."),
    N_LOW(4, "Nitrogen (N) low", "Add urea."),
    P_LOW(5, "Phosphorus (P) low", "Add ammonium phosphate or Solophos."),
    K_LOW(6, "Potassium (K) low", "Add muriate of potash (0-0-60)."),
    MULTI_LOW(7, "Several low", "Add more 14-14-14 and compost, then test again.");

    companion object {
        fun fromCode(code: Int): SoilCondition? = entries.firstOrNull { it.code == code }
        fun fromCode(code: String?): SoilCondition? = code?.trim()?.toIntOrNull()?.let(::fromCode)
    }
}

object Products {
    const val COMPLETE = "Complete 14-14-14"
    const val UREA = "Urea 46-0-0"
    const val AMMONIUM_SULFATE = "Ammonium sulfate 21-0-0"
    const val AMMONIUM_PHOSPHATE = "Ammonium phosphate 16-20-0"
    const val SOLOPHOS = "Solophos 0-18-0"
    const val POTASH = "Muriate of potash 0-0-60"
    const val LIME = "Agricultural lime or dolomite"
    const val DOLOMITE = "Dolomite"
    const val CHICKEN_MANURE = "Dried chicken manure"
    const val ORGANIC = "Chicken manure or other organic fertilizer"
    const val COMPOST = "Manure or compost"
}

/** An amount per bed (1 m × 20 m = 20 m²), the unit used throughout the book. */
data class Dose(val product: String, val grams: Int = 0, val sacks: Int = 0)

/** One code on a crop's Fix-it page. */
data class Fix(
    val condition: SoilCondition,
    /** The Fix-it text as printed. */
    val text: String,
    /** Short product line for the prescription card. */
    val product: String,
    /** Full dose per bed. For ! SLIGHTLY LOW the book says to use half. */
    val doses: List<Dose>,
    val timing: String,
    /** Same job, other product ("or 400 g Solophos"). */
    val alternative: Dose? = null,
)

data class PlanStep(val whenLabel: String, val perBed: String, val perHectare: String)

/** One crop chapter of the Kasaka Guidebook: growing, reading table, standard plan, Fix-it codes. */
data class CropGuide(
    val crop: String,
    val scientificName: String,
    /** Page the chapter starts on in the printed book; the four parts follow on the next pages. */
    val page: Int,
    val harvest: String,
    val spacing: String,
    val growing: List<String>,
    val pests: String,
    val growingSource: String,
    val readingNote: String,
    val example: List<String>,
    val why: String,
    val planIntro: String,
    val plan: List<PlanStep>,
    val planTips: List<String>,
    val planSource: String,
    val fixIntro: String,
    val fixes: List<Fix>,
    /** The "▲ Too high" line: what to change when a nutrient reads HIGH. */
    val tooHigh: Map<Nutrient, String>,
) {
    val profile: CropProfile get() = CropProfiles.find(crop)!!
    fun fix(condition: SoilCondition): Fix = fixes.first { it.condition == condition }
}

/** A general page of the book (soil reading, liming, fertilizers…). */
data class GuideTopic(val id: String, val section: String, val title: String, val page: Int, val blocks: List<GuideBlock>)

sealed interface GuideBlock {
    data class Heading(val text: String) : GuideBlock
    data class Para(val text: String) : GuideBlock
    data class Steps(val items: List<String>) : GuideBlock
    data class Bullets(val items: List<String>) : GuideBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : GuideBlock
    data class Note(val text: String) : GuideBlock
}

/** The Kasaka Guidebook, Region VIII Edition (2026). Text follows docs/guidebook/kasaka-guidebook.html. */
object Guidebook {
    private const val ALKALINE_FIX = "No lime or wood ash. Use ammonium sulfate 21-0-0 in place of urea (2 g for every 1 g urea). Add 1 extra sack of compost."
    private val alkalineDoses = listOf(Dose(Products.COMPOST, sacks = 1))
    private const val ALKALINE_PRODUCT = "Ammonium sulfate 21-0-0 instead of urea + compost"
    private const val ALKALINE_TIMING = "Use 2 g ammonium sulfate for every 1 g urea in the plan"
    private val standardTooHigh = mapOf(
        Nutrient.N to "Skip urea this round.",
        Nutrient.P to "Use urea + 0-0-60 instead of 14-14-14.",
        Nutrient.K to "Skip 0-0-60.",
    )

    val crops: List<CropGuide> = listOf(
        CropGuide(
            crop = "Kangkong",
            scientificName = "Ipomoea aquatica / I. reptans · upland water spinach",
            page = 8,
            harvest = "3–4 weeks",
            spacing = "20 × 20 cm",
            growing = listOf(
                "Soil. Grows in most soils; best in sandy loam with good drainage and lots of organic matter.",
                "Prepare the land. Plow and harrow 2–3 times, one week apart, 15–20 cm deep. Make beds 1 m wide × 20 m long. Mix in the basal fertilizer (page 10). Plastic mulch is optional.",
                "Plant. Sow 2–3 seeds per hill in 4 rows per bed, 20 cm between rows and between hills. Stem cuttings (25–30 cm, 2–3 nodes in the soil) also work. Kangkong can also be grown in containers.",
                "Water right after planting and every day after. Kangkong never wants to dry out.",
                "Weed often in the first 3 weeks, until the plants cover the bed.",
                "Harvest 3–4 weeks after planting by cutting the shoots about 5 cm above the soil, or pull whole plants. Cut plants regrow; cut again every 2–3 weeks and feed after each cut.",
            ),
            pests = "Caterpillars, whiteflies and aphids are the common pests. Keep the bed clean, remove weeds and damaged plants, and check under the leaves twice a week. Use resistant varieties and biological or chemical control only when needed, following the label.",
            growingSource = "National guide, not Region 8: DA RFU VII Guide on Vegetable Production / ATI-VII Kangkong Production Guide (2020); BPI Kangkong Production Guide.",
            readingNote = "Find each of your averaged numbers in its row. The box it falls in tells you what to do.",
            example = listOf(
                "Average of 5 readings: pH 5.8 · N 30 · P 18 · K 95",
                "pH 5.8 → ✓ GOOD",
                "N 30 → ! SLIGHTLY LOW",
                "P 18 and K 95 → ✓ GOOD",
                "Code 4 (nitrogen low). Follow the standard plan and add half the N fix: 50 g urea per bed (page 11).",
            ),
            why = "pH range from the DA RFU VII / ATI-VII kangkong guide (5.5–6.5 best); kangkong tolerates slightly higher pH. P and K boxes follow the BSWM soil ratings used for Leyte: \"high\" P starts at about 15 mg/kg (Olsen) and \"high\" K at about 75 mg/kg. Kangkong is a leafy crop, so it needs a good supply of nitrogen, but an economic-rate study on acid soils found it responds little to potassium.",
            planIntro = "Use this plan when all four readings are ✓ GOOD. If any reading is low or high, change it with the Fix-it page.",
            plan = listOf(
                PlanStep("Basal, at bed making, before planting", "1 sack dried chicken manure + 300 g complete 14-14-14 (about 1¼ cups). Mix into the top soil or cover with soil.", "3 bags 14-14-14 + manure"),
                PlanStep("Week 1–2 after the seedlings come up", "100 g urea 46-0-0 (½ cup) dissolved in 16 L water. Pour about 150 mL beside each hill, then water.", "1 bag urea"),
                PlanStep("After every cutting", "100 g urea dissolved in 16 L water, applied the same way.", "1 bag urea per cut"),
            ),
            planTips = listOf(
                "Apply urea in the late afternoon and water it in. Urea left on dry soil in the sun is lost to the air.",
                "Never let dry urea touch the leaves; it burns them.",
                "Organic option: replace the urea with compost tea or fermented plant juice every week.",
                "Kangkong grown in very wet or flooded beds still needs feeding after each cutting.",
                "Note on the DA leaflet: the ATI-VII leaflet lists 1 kg 14-14-14 and 4 kg urea per bed. That is more than 10 times the rate shown in research and is very costly. This book uses lower, research-based amounts. Ask your agriculture technician if you want to use the leaflet rate.",
            ),
            planSource = "Basal method and drench (16 L, 150 mL per hill): DA RFU VII / ATI-VII. Rates set near the economic optimum of about 40 kg N and 40 kg P₂O₅ per ha found for kangkong on acid Ultisols (SANREM/IPB study), with manure supplying extra P and K.",
            fixIntro = "Amounts are in addition to the standard plan. For ! SLIGHTLY LOW use half.",
            fixes = listOf(
                Fix(
                    SoilCondition.BALANCED, "Follow the standard plan (page 10). Test again in 2–3 weeks.",
                    "Standard plan: manure + 14-14-14", listOf(Dose(Products.CHICKEN_MANURE, sacks = 1), Dose(Products.COMPLETE, 300)),
                    "Basal at bed making; then 100 g urea at week 1–2 and after every cutting",
                ),
                Fix(
                    SoilCondition.ACIDIC, "Lime first. Spread 4 kg agricultural lime or dolomite per bed (2 kg if only slightly sour), mix into the top 15 cm and water. Wait 2 weeks before planting or putting urea. See page 36.",
                    Products.LIME, listOf(Dose(Products.LIME, 4000)), "Mix into the top 15 cm; wait 2 weeks before planting or urea",
                ),
                Fix(SoilCondition.ALKALINE, "Do not use lime or wood ash. Use ammonium sulfate 21-0-0 instead of urea (2 g for every 1 g urea) and work in 1 extra sack of compost.", ALKALINE_PRODUCT, alkalineDoses, ALKALINE_TIMING),
                Fix(
                    SoilCondition.N_LOW, "Add 100 g urea per bed, in two parts one week apart, dissolved in water. Check that leaves turn darker green within a week.",
                    Products.UREA, listOf(Dose(Products.UREA, 100)), "In two parts, one week apart, dissolved in water",
                ),
                Fix(
                    SoilCondition.P_LOW, "At planting, mix 300 g ammonium phosphate 16-20-0 (or 400 g Solophos 0-18-0) into the planting rows. On a growing crop, place it in a shallow furrow beside the rows, cover and water.",
                    Products.AMMONIUM_PHOSPHATE, listOf(Dose(Products.AMMONIUM_PHOSPHATE, 300)), "At planting, in the rows; on a growing crop, in a furrow beside the rows",
                    alternative = Dose(Products.SOLOPHOS, 400),
                ),
                Fix(
                    SoilCondition.K_LOW, "Add 100 g muriate of potash 0-0-60 per bed, spread between rows and watered in.",
                    Products.POTASH, listOf(Dose(Products.POTASH, 100)), "Spread between rows and water in",
                ),
                Fix(
                    SoilCondition.MULTI_LOW, "Add 300 g more 14-14-14 to the basal and 1 more sack of manure or compost. Test again after 2 weeks and fix whatever is still low. If pH is also low, do code 2 first.",
                    "Complete 14-14-14 + manure or compost", listOf(Dose(Products.COMPLETE, 300), Dose(Products.COMPOST, sacks = 1)),
                    "Add to the basal; test again after 2 weeks",
                ),
            ),
            tooHigh = standardTooHigh,
        ),
        CropGuide(
            crop = "Pechay",
            scientificName = "Brassica rapa L. · petsay",
            page = 12,
            harvest = "3–4 weeks after transplant",
            spacing = "10 × 20 cm",
            growing = listOf(
                "Soil. Sandy to clay loam with plenty of organic matter. Pechay grows best at pH 6.0–6.5.",
                "Prepare the land. Plow and harrow 2–3 times, one week apart, 15–20 cm deep. Beds 1 m × 20 m. Mix in basal fertilizer (page 14).",
                "Raise seedlings in a raised seedbed (1 m × 10 m, 15 cm high, fine sterilized soil) or in seedling trays with 2–3 seeds per hole. Cover lightly and sprinkle with water.",
                "Seedling care. Shade from strong sun and heavy rain. Water morning or afternoon. Harden for one week before transplanting by giving more sun each day.",
                "Transplant 2–3 weeks after sowing, in the late afternoon, 10 cm between plants and 20 cm between rows. Water right away and then daily.",
                "Harvest 3–4 weeks after transplanting, early morning or late afternoon. Cut with a sharp knife or pull with the roots and wash.",
            ),
            pests = "Pests: aphids, flea beetles and diamondback moth. Diseases: powdery mildew on seedlings, bacterial soft rot, and club root (swollen, knotted roots; plants wilt at midday). Pull and bury sick plants. Club root is worse in sour soil, so keeping pH near 6.5 and rotating crops helps prevent it.",
            growingSource = "National guide, not Region 8: DA RFU VII Guide on Vegetable Production / ATI-VII Pechay Production Guide (2020); DA Caraga pechay techno guide (pH 5.5–6.5).",
            readingNote = "Find each of your averaged numbers in its row.",
            example = listOf(
                "Average of 5 readings: pH 5.2 · N 55 · P 10 · K 120",
                "pH 5.2 → ! SLIGHTLY LOW (a bit sour)",
                "P 10 → ✕ LOW",
                "N 55 and K 120 → ✓ GOOD",
                "pH comes first: code 2. Lime with 2 kg per bed, wait 2 weeks, then plant using the P fix of code 5.",
            ),
            why = "pH: DA guides give 5.5–6.5; growers aim for 6.0–6.5, and liming to near 6.5 lowers club root. Pechay is a fast, leafy brassica that needs more nitrogen and phosphorus than kangkong. A Philippine recommended rate for pechay is about 60-40-60 kg N-P₂O₅-K₂O per hectare, so the P and K \"good\" boxes start higher than for kangkong.",
            planIntro = "Use this plan when all four readings are ✓ GOOD.",
            plan = listOf(
                PlanStep("Basal, at bed making", "1 sack dried chicken manure + 400 g complete 14-14-14 (about 1½ cups) + 50 g muriate of potash 0-0-60 (3 tbsp). Mix into the soil before transplanting.", "4 bags 14-14-14 + ½ bag 0-0-60 + manure"),
                PlanStep("1 week after transplanting", "50 g urea (5 tbsp) dissolved in 16 L water; about 150 mL beside each plant.", "½ bag urea"),
                PlanStep("2 weeks after transplanting", "50 g urea in 16 L water, same way.", "½ bag urea"),
                PlanStep("Seedlings", "Drench trays with a weak starter solution (1 tbsp 14-14-14 dissolved in 16 L water) a few days before transplanting.", "—"),
            ),
            planTips = listOf(
                "Stop urea at least 7 days before harvest. Late nitrogen makes leaves soft and lowers keeping quality.",
                "Water every day, but do not let water stand in the bed. In the rainy months use raised beds with canals.",
                "Organic option: 2 sacks of compost or vermicast in place of the 14-14-14, plus weekly compost tea.",
            ),
            planSource = "Basal method, drench and timing: DA RFU VII / ATI-VII. Total about 51-28-43 kg N-P₂O₅-K₂O per ha from fertilizer plus manure, close to the 60-40-60 recommended rate used in Philippine pechay research. The ATI leaflet's 4 kg urea per bed is far above this rate.",
            fixIntro = "Amounts are in addition to the standard plan. For ! SLIGHTLY LOW use half.",
            fixes = listOf(
                Fix(
                    SoilCondition.BALANCED, "Follow the standard plan (page 14). Test again after harvest.",
                    "Standard plan: manure + 14-14-14 + 0-0-60",
                    listOf(Dose(Products.CHICKEN_MANURE, sacks = 1), Dose(Products.COMPLETE, 400), Dose(Products.POTASH, 50)),
                    "Basal before transplanting; then 50 g urea at 1 and 2 weeks",
                ),
                Fix(
                    SoilCondition.ACIDIC, "Lime first. 4 kg agricultural lime or dolomite per bed (2 kg if only slightly sour), mixed into the top 15 cm and watered. Wait 2 weeks before transplanting. Liming also helps prevent club root. See page 36.",
                    Products.LIME, listOf(Dose(Products.LIME, 4000)), "Mix into the top 15 cm; wait 2 weeks before transplanting",
                ),
                Fix(SoilCondition.ALKALINE, ALKALINE_FIX, ALKALINE_PRODUCT, alkalineDoses, ALKALINE_TIMING),
                Fix(
                    SoilCondition.N_LOW, "Add 100 g urea per bed: 50 g at 1 week and 50 g at 2 weeks, on top of the standard doses, dissolved in water.",
                    Products.UREA, listOf(Dose(Products.UREA, 100)), "Split between week 1 and week 2 after transplanting, dissolved in water",
                ),
                Fix(
                    SoilCondition.P_LOW, "Mix 300 g ammonium phosphate 16-20-0 (or 400 g Solophos 0-18-0) into the bed before transplanting. Phosphorus barely moves in soil, so it must be near the roots.",
                    Products.AMMONIUM_PHOSPHATE, listOf(Dose(Products.AMMONIUM_PHOSPHATE, 300)), "Mix into the bed before transplanting",
                    alternative = Dose(Products.SOLOPHOS, 400),
                ),
                Fix(
                    SoilCondition.K_LOW, "Add 100 g muriate of potash 0-0-60 per bed, mixed in at basal.",
                    Products.POTASH, listOf(Dose(Products.POTASH, 100)), "Mix in at basal",
                ),
                Fix(
                    SoilCondition.MULTI_LOW, "Add 300 g more 14-14-14 and 1 more sack of manure or compost at basal. Test again 2 weeks after transplanting and fix what is still low. If pH is also low, do code 2 first.",
                    "Complete 14-14-14 + manure or compost", listOf(Dose(Products.COMPLETE, 300), Dose(Products.COMPOST, sacks = 1)),
                    "At basal; test again 2 weeks after transplanting",
                ),
            ),
            tooHigh = standardTooHigh,
        ),
        CropGuide(
            crop = "Sweet potato",
            scientificName = "Ipomoea batatas (L.) Lam. · kamote",
            page = 16,
            harvest = "3–4 months",
            spacing = "75 × 25 cm",
            growing = listOf(
                "Soil. Loose, well-drained sandy loam. It tolerates sour, poor soil better than the other three crops, but not standing water.",
                "Planting material. Use healthy tip cuttings 25–30 cm long from vigorous, pest-free plants. Clean planting material of improved varieties is available from VSU PhilRootcrops in Baybay City and DA RFO 8.",
                "Make ridges 75 cm apart and 25–30 cm high. Ridges keep roots dry in the rainy season.",
                "Plant one cutting per hill, 25 cm apart, with 2–3 nodes buried, slanting into the ridge.",
                "Weed and hill up at about 2–4 weeks and again at 1 month, pulling soil back up the ridge. Cover any soil cracks to keep weevils out.",
                "Lift the vines once a month so they do not root at the nodes.",
                "Harvest 3–4 months after planting for modern varieties (traditional varieties take up to 6 months).",
            ),
            pests = "The worst pest is the sweet potato weevil, which bores into the roots and makes them bitter. Prevent it by using clean cuttings, hilling up to close soil cracks, harvesting on time, and removing old roots and vines after harvest. Rotate with a non-Ipomoea crop; kangkong is a relative of kamote, so do not rotate kamote with kangkong.",
            growingSource = "Region 8: VSU research in Leyte (Asio et al., Tropentag 2018: 0.75 × 0.25 m spacing, ridges); PhilRootcrops, VSU. National guide, not Region 8: DA RFO 02 Sweet Potato Production Guide (2012).",
            readingNote = "Find each of your averaged numbers in its row. Note the nitrogen row: for kamote, too much nitrogen is a problem.",
            example = listOf(
                "Average of 5 readings: pH 5.1 · N 70 · P 16 · K 90",
                "pH 5.1 → ! SLIGHTLY LOW. Kamote can grow here; liming is optional.",
                "N 70 → ▲ HIGH: skip all extra nitrogen.",
                "K 90 → ! SLIGHTLY LOW",
                "Code 6. Use 0-0-60 only (half the K fix, 75 g per bed) and no urea.",
            ),
            why = "In a VSU trial in Leyte (sandy loam, pH 5.8, P 19 mg/kg, high K), root yield was highest at 80–160 kg N per ha and dropped sharply above that, because extra nitrogen grows vines instead of roots. The recommended rate there was 40-40-60 kg N-P₂O₅-K₂O per ha; phosphorus above 40 kg gave no gain. Kamote takes up more potassium than nitrogen, so its \"good\" K box is the highest of the four crops.",
            planIntro = "Use this plan when all four readings are ✓ GOOD. It supplies about 40-40-60 kg N-P₂O₅-K₂O per hectare, the rate used in VSU trials in Leyte.",
            plan = listOf(
                PlanStep("At planting", "200 g complete 14-14-14 (¾ cup) + 50 g muriate of potash 0-0-60 (3 tbsp). Put in a furrow along the ridge, cover with 3–5 cm soil, then plant. Do not let fertilizer touch the cuttings.", "2 bags 14-14-14 + ½ bag 0-0-60"),
                PlanStep("About 1 month after planting, at hilling-up", "200 g 14-14-14 + 50 g 0-0-60, placed beside the hills and covered when you hill up.", "2 bags 14-14-14 + ½ bag 0-0-60"),
            ),
            planTips = listOf(
                "Splitting the dose in two follows DA guidance: when the rate is more than 45 kg per ha, apply half at planting and half one month later.",
                "No urea in the standard plan. Kamote rarely needs it, and too much makes long vines and small roots.",
                "Do not fertilize after 2 months. Late fertilizer goes to the leaves, not the roots.",
                "Compost or rice-hull ash worked into the ridge adds potassium and loosens heavy soil.",
                "Where the soil is already rich (good N, P and K), many Leyte farmers grow kamote with manure only.",
            ),
            planSource = "Rates: Asio, Tamayo & dela Cruz (VSU/CLSU), Response of a Traditional Sweetpotato Variety to Fertilization in Leyte, Tropentag 2018. Split application: DA RFO 02 Sweet Potato Production Guide.",
            fixIntro = "Amounts are in addition to the standard plan, per 20 m². For ! SLIGHTLY LOW use half.",
            fixes = listOf(
                Fix(
                    SoilCondition.BALANCED, "Follow the standard plan (page 18).",
                    "Standard plan: 14-14-14 + 0-0-60", listOf(Dose(Products.COMPLETE, 200), Dose(Products.POTASH, 50)),
                    "At planting, and the same again at hilling-up (1 month)",
                ),
                Fix(
                    SoilCondition.ACIDIC, "Below pH 4.5: spread 2 kg agricultural lime or dolomite per 20 m², mix into the soil and wait 2 weeks before planting. Between 4.5 and 5.4 liming is optional; kamote tolerates it. See page 36.",
                    Products.LIME, listOf(Dose(Products.LIME, 2000)), "Mix into the soil; wait 2 weeks before planting",
                ),
                Fix(
                    SoilCondition.ALKALINE, "No lime or ash. Work in 1–2 sacks of compost. If you must add N, use ammonium sulfate 21-0-0.",
                    "Compost (ammonium sulfate 21-0-0 only if N is needed)", listOf(Dose(Products.COMPOST, sacks = 1)), "Work into the ridges before planting",
                ),
                Fix(
                    SoilCondition.N_LOW, "Add only 50 g urea at hilling-up (1 month), never more. Do not repeat it.",
                    Products.UREA, listOf(Dose(Products.UREA, 50)), "Once, at hilling-up (1 month); do not repeat",
                ),
                Fix(
                    SoilCondition.P_LOW, "Mix 200 g ammonium phosphate 16-20-0 (or 250 g Solophos 0-18-0) into the planting furrow.",
                    Products.AMMONIUM_PHOSPHATE, listOf(Dose(Products.AMMONIUM_PHOSPHATE, 200)), "Mix into the planting furrow",
                    alternative = Dose(Products.SOLOPHOS, 250),
                ),
                Fix(
                    SoilCondition.K_LOW, "Add 150 g muriate of potash 0-0-60: half at planting, half at hilling-up. Potassium makes bigger, better roots.",
                    Products.POTASH, listOf(Dose(Products.POTASH, 150)), "Half at planting, half at hilling-up",
                ),
                Fix(
                    SoilCondition.MULTI_LOW, "Add 200 g more 14-14-14 and 1 sack manure or compost at planting. Test again at 1 month and fix what is still low. If pH is below 4.5, do code 2 first.",
                    "Complete 14-14-14 + manure or compost", listOf(Dose(Products.COMPLETE, 200), Dose(Products.COMPOST, sacks = 1)),
                    "At planting; test again at 1 month",
                ),
            ),
            tooHigh = mapOf(
                Nutrient.N to "Above 60: use no 14-14-14 at all; feed with 0-0-60 only (100 g per 20 m²).",
                Nutrient.P to "Use 0-0-60 alone.",
                Nutrient.K to "Skip 0-0-60.",
            ),
        ),
        CropGuide(
            crop = "Okra",
            scientificName = "Abelmoschus esculentus (L.) Moench",
            page = 20,
            harvest = "40–75 days (first pods)",
            spacing = "20 × 25 cm",
            growing = listOf(
                "Soil. Grows in any soil but best in sandy loam, pH 5.5–7.0 (best 6.0–6.8). Choose a high-yielding, pest-resistant variety that buyers in your market like.",
                "Prepare the land. Plow and harrow 2–3 times, one week apart, 15–20 cm deep. Beds 1 m × 20 m. Mix in basal fertilizer (page 22). Plastic mulch is optional.",
                "Soak seeds in clean water overnight. Sow right away, or air-dry first.",
                "Sow 2–3 seeds per hill, 1 cm deep, 20 cm between hills and 25 cm between rows. Replant missing hills right away. Thin to 2 plants per hill 15 days after planting.",
                "Water whenever the soil is dry or plants wilt.",
                "Hill up one month after the plants come up, covering the base of the plants. Hand-pull the weeds between plants.",
                "Harvest young, tender pods 7–10 cm (3–4 inches) long, 4–6 days after the flower opens. Pick every 2–3 days; old pods get tough and slow down the plant.",
            ),
            pests = "Pests: cotton stainer and stink bugs. Diseases: cercospora leaf blight, powdery mildew, fruit rot and root-knot nematode. Uproot and bury sick plants, keep the field clean, and rotate. Root-knot nematodes build up when okra follows okra.",
            growingSource = "National guide, not Region 8: DA RFU VII Guide on Vegetable Production / ATI-VII Okra Production Guide (2020); BPI Okra Production Guide; DA RFO 02 Okra Production Guide.",
            readingNote = "Find each of your averaged numbers in its row.",
            example = listOf(
                "Average of 5 readings: pH 6.2 · N 32 · P 14 · K 150",
                "pH 6.2 and K 150 → ✓ GOOD",
                "N 32 → ! SLIGHTLY LOW",
                "P 14 → ! SLIGHTLY LOW",
                "Two nutrients are short, so the code is 7 (several low). Add the extra 14-14-14 at basal and test again 2 weeks after the seedlings come up.",
            ),
            why = "pH 5.5–7.0 from the DA RFU VII/ATI-VII and BPI okra guides. Okra flowers and fruits for many weeks, so it needs steady nitrogen and good phosphorus for pods. Since phosphorus is the most common shortage in Leyte soils (page 6), okra growers should watch the P row closely.",
            planIntro = "Use this plan when all four readings are ✓ GOOD.",
            plan = listOf(
                PlanStep("Basal, at bed making", "1 sack dried chicken manure + 400 g complete 14-14-14 (about 1½ cups). Mix into the soil or cover with soil before sowing.", "4 bags 14-14-14 + manure"),
                PlanStep("Side-dress 1, about 30 days (first flower buds)", "100 g urea (½ cup) in a shallow furrow 5–10 cm from the plants. Cover with soil and water. Do this when you hill up.", "1 bag urea"),
                PlanStep("Side-dress 2, about 55–60 days (during harvest)", "100 g urea + 50 g muriate of potash 0-0-60, same way.", "1 bag urea + ½ bag 0-0-60"),
            ),
            planTips = listOf(
                "If the harvest goes on longer than 2 months, give another 100 g urea every 3–4 weeks while the plants are still flowering.",
                "Instead of dry side-dressing, you may dissolve the fertilizer in 16 L water and pour about 150 mL beside each hill (DA drench method).",
                "Yellowing older leaves during harvest usually means the plant needs nitrogen. Test to be sure.",
            ),
            planSource = "Basal method, drench and side-dress timing: DA RFU VII / ATI-VII and BPI okra guides. Basal 2–3 bags 14-14-14 per ha plus organic fertilizer: DA RFO 02 Okra Production Guide. Plan supplies about 74-28-43 kg N-P₂O₅-K₂O per ha from fertilizer, plus manure.",
            fixIntro = "Amounts are in addition to the standard plan. For ! SLIGHTLY LOW use half.",
            fixes = listOf(
                Fix(
                    SoilCondition.BALANCED, "Follow the standard plan (page 22). Test again 2 weeks after side-dress 1.",
                    "Standard plan: manure + 14-14-14", listOf(Dose(Products.CHICKEN_MANURE, sacks = 1), Dose(Products.COMPLETE, 400)),
                    "Basal before sowing; urea side-dress at ~30 days, urea + 0-0-60 at 55–60 days",
                ),
                Fix(
                    SoilCondition.ACIDIC, "Lime first. 4 kg agricultural lime or dolomite per bed (2 kg if only slightly sour), mixed into the top 15 cm and watered. Wait 2 weeks before sowing. See page 36.",
                    Products.LIME, listOf(Dose(Products.LIME, 4000)), "Mix into the top 15 cm; wait 2 weeks before sowing",
                ),
                Fix(SoilCondition.ALKALINE, ALKALINE_FIX, ALKALINE_PRODUCT, alkalineDoses, ALKALINE_TIMING),
                Fix(
                    SoilCondition.N_LOW, "Add 100 g urea per bed, in two parts one week apart (or with each side-dress), covered and watered in.",
                    Products.UREA, listOf(Dose(Products.UREA, 100)), "In two parts one week apart, or with each side-dress",
                ),
                Fix(
                    SoilCondition.P_LOW, "Mix 300 g ammonium phosphate 16-20-0 (or 400 g Solophos 0-18-0) into the rows before sowing. On a growing crop, place it in a furrow beside the row, cover and water.",
                    Products.AMMONIUM_PHOSPHATE, listOf(Dose(Products.AMMONIUM_PHOSPHATE, 300)), "Into the rows before sowing; on a growing crop, in a furrow beside the row",
                    alternative = Dose(Products.SOLOPHOS, 400),
                ),
                Fix(
                    SoilCondition.K_LOW, "Add 100 g muriate of potash 0-0-60 per bed at basal, or split between the two side-dresses.",
                    Products.POTASH, listOf(Dose(Products.POTASH, 100)), "At basal, or split between the two side-dresses",
                ),
                Fix(
                    SoilCondition.MULTI_LOW, "Add 300 g more 14-14-14 and 1 more sack of manure or compost at basal. Test again 2 weeks after the seedlings come up and fix what is still low. If pH is also low, do code 2 first.",
                    "Complete 14-14-14 + manure or compost", listOf(Dose(Products.COMPLETE, 300), Dose(Products.COMPOST, sacks = 1)),
                    "At basal; test again 2 weeks after the seedlings come up",
                ),
            ),
            tooHigh = mapOf(
                Nutrient.N to "Skip the next urea side-dress.",
                Nutrient.P to "Use urea + 0-0-60 instead of 14-14-14.",
                Nutrient.K to "Skip 0-0-60.",
            ),
        ),
        CropGuide(
            crop = "Eggplant",
            scientificName = "Solanum melongena L. · talong",
            page = 24,
            harvest = "46–50 days after transplanting",
            spacing = "50 cm apart",
            growing = listOf(
                "Soil. Sandy loam or clay loam, pH 5.5–6.8. Eggplant needs about 35–40 mm of water a week.",
                "Seedlings. Sow in trays. Seedlings are ready 30–35 days after sowing, with 3–4 true leaves. About 10 days after they come up, water with 1 tablespoon urea or ammonium sulfate in 4 liters of water. Do not overwater (it causes damping-off).",
                "Prepare the land. Plow and harrow 2–3 times. Make beds 1 m wide and 20 cm high, 50 cm apart. Mix in the basal fertilizer (page 26). Plastic mulch is optional and keeps away insects that hide under the leaves.",
                "Transplant one seedling per hole, 50 cm apart along the bed and 10 cm in from the bed edge. Replant any missing or wilted plants after 3–5 days.",
                "Water every week, more in dry weather, but never let water stand around the plants. Prune a few leaves so air can move through.",
                "Harvest 46–50 days after transplanting. Pick young, tender fruit early in the morning, about twice a week, with pruning shears. Pick all the fruit so the plant keeps fruiting.",
            ),
            pests = "Main pest: the fruit and shoot borer. Cut off wilting shoots and fruit with a hole, and pick the borer out by hand. Bacterial wilt (plants wilt suddenly while still green): rotate, never plant after tomato, pepper or potato, and pull and bury sick plants.",
            growingSource = "National guide, not Region 8: ATI e-extension eggplant guide (as republished by Business Diary); BPI Organic Eggplant Production guide (borer control). No Region 8 eggplant guide was found. Confirm with your DA RFO 8 technician.",
            readingNote = "Find each of your averaged numbers in its row.",
            example = listOf(
                "Average of 5 readings: pH 6.1 · N 38 · P 28 · K 140",
                "pH 6.1, P 28 and K 140 → ✓ GOOD",
                "N 38 → ! SLIGHTLY LOW",
                "Only nitrogen is a little short, so the code is 4. Follow the standard plan and add half the N fix: 50 g urea per bed (page 27).",
            ),
            why = "pH range from the ATI e-extension eggplant guide (5.5–6.8). Eggplant fruits for many weeks and takes up a lot of nitrogen and potassium, so those \"good\" boxes start higher than for kangkong. The N, P and K boxes are SakaSense estimates for the probe, not figures from the guide. Check them with a DA laboratory test.",
            planIntro = "Use this plan when all four readings are ✓ GOOD. Amounts are for one bed of 1 × 20 m.",
            plan = listOf(
                PlanStep("Basal, at bed making, before transplanting", "1 kg chicken manure or other organic fertilizer + 800 g ammonium phosphate 16-20-0 (about 3 cups) + 800 g ammonium sulfate 21-0-0 (about 3 cups) + 200 g muriate of potash 0-0-60 (¾ cup). Spread on both sides of the bed and mix into the top soil.", "5 bags 16-20-0 + 5 bags 21-0-0 + 1 bag 0-0-60"),
                PlanStep("Side-dress 3 weeks after transplanting, and again at first harvest", "100 g urea + 50 g 0-0-60, placed in a shallow furrow about 20 cm from the plants. Cover with soil and water.", "¾ bag urea + ⅓ bag 0-0-60 each time"),
            ),
            planTips = listOf(
                "The basal amounts follow the ATI guide, which gives them per 10 m of bed. Doubled here for a 20 m bed.",
                "Keep side-dressing every 3–4 weeks while you are still picking fruit.",
                "Stop watering from above on cloudy days; wet leaves invite disease.",
                "Book estimate: the ATI guide shows how to side-dress but does not give the amounts. The side-dress here is the SakaSense estimate. Ask your agriculture technician to confirm it.",
            ),
            planSource = "Basal amounts and starter solution: ATI e-extension eggplant guide (national). Basal gives about 99-53-40 kg N-P₂O₅-K₂O per hectare of beds and paths.",
            fixIntro = "Amounts are in addition to the standard plan. For ! SLIGHTLY LOW use half.",
            fixes = listOf(
                Fix(
                    SoilCondition.BALANCED, "Follow the standard plan (page 26). Test again at the first flowers.",
                    "Standard plan: manure + 16-20-0 + 21-0-0 + 0-0-60",
                    listOf(Dose(Products.ORGANIC, 1000), Dose(Products.AMMONIUM_PHOSPHATE, 800), Dose(Products.AMMONIUM_SULFATE, 800), Dose(Products.POTASH, 200)),
                    "Basal before transplanting; urea + 0-0-60 at 3 weeks and at first harvest",
                ),
                Fix(
                    SoilCondition.ACIDIC, "Lime first. 4 kg agricultural lime or dolomite per bed (2 kg if only slightly sour), mixed into the top 15 cm and watered. Wait 2 weeks before transplanting. See page 36.",
                    Products.LIME, listOf(Dose(Products.LIME, 4000)), "Mix into the top 15 cm; wait 2 weeks before transplanting",
                ),
                Fix(
                    SoilCondition.ALKALINE, "No lime or wood ash. Keep the ammonium sulfate in the basal, use it instead of urea for side-dressing (2 g for every 1 g urea) and add 1 sack of compost.",
                    ALKALINE_PRODUCT, alkalineDoses, ALKALINE_TIMING,
                ),
                Fix(
                    SoilCondition.N_LOW, "Add 100 g urea per bed, in two parts one week apart, dissolved in water and placed beside the plants.",
                    Products.UREA, listOf(Dose(Products.UREA, 100)), "In two parts, one week apart, dissolved in water",
                ),
                Fix(
                    SoilCondition.P_LOW, "Mix 300 g more ammonium phosphate 16-20-0 (or 400 g Solophos 0-18-0) into the bed before transplanting. On a growing crop, place it in a furrow beside the row, cover and water.",
                    Products.AMMONIUM_PHOSPHATE, listOf(Dose(Products.AMMONIUM_PHOSPHATE, 300)), "Into the bed before transplanting; on a growing crop, in a furrow beside the row",
                    alternative = Dose(Products.SOLOPHOS, 400),
                ),
                Fix(
                    SoilCondition.K_LOW, "Add 100 g muriate of potash 0-0-60 per bed. Potassium helps fruit size and color.",
                    Products.POTASH, listOf(Dose(Products.POTASH, 100)), "Beside the plants, then water",
                ),
                Fix(
                    SoilCondition.MULTI_LOW, "Add 300 g more 14-14-14 and 1 more sack of manure or compost at basal. Test again 2 weeks after transplanting and fix what is still low. If pH is also low, do code 2 first.",
                    "Complete 14-14-14 + manure or compost", listOf(Dose(Products.COMPLETE, 300), Dose(Products.COMPOST, sacks = 1)),
                    "At basal; test again 2 weeks after transplanting",
                ),
            ),
            tooHigh = mapOf(
                Nutrient.N to "Skip the next urea.",
                Nutrient.P to "Skip the extra ammonium phosphate.",
                Nutrient.K to "Skip 0-0-60.",
            ),
        ),
        CropGuide(
            crop = "Ampalaya",
            scientificName = "Momordica charantia L. · palia (Bisaya)",
            page = 28,
            harvest = "45–75 days",
            spacing = "1.5 m × 75 cm",
            growing = listOf(
                "Soil. Well-drained sandy loam, pH 5.5–6.5, in full sun. Ampalaya can be planted any time of the year.",
                "Prepare the land. Plow and harrow 2–3 times until the soil is loose. Make beds 1–1.5 m wide. Plastic mulch is optional. Spread dolomite and manure 2 weeks before planting (page 30).",
                "Seeds. Soak overnight, or carefully crack the seed coat, then sow the next day in seedbeds or plug trays. Transplant 7–10 days after the seeds come up.",
                "Plant with 1.5 m between rows and 75 cm between hills.",
                "Trellis. Use an A-type or table-type trellis of bamboo poles. Remove the lower side shoots and thin some upper shoots to keep the fruits big.",
                "Harvest when fruits are still green and shiny but full size: 15–20 days after pollination, or 45–75 days after planting. Pick 2–3 times a week for 2–3 months.",
            ),
            pests = "Fruit fly: wrap fruits in clear plastic or paper and hang a fruit fly attractant. Downy mildew: remove infected leaves. Bacterial wilt: rotate crops and keep the field clean. Also watch for aphids, cutworm and leaf folders.",
            growingSource = "Region 8: BPI notes Eastern Visayas planted 284 ha and produced 1,180 tons in 2011 (PSA figures). National guide, not Region 8: BPI Ampalaya Production Guide (2013). No Region 8 ampalaya guide was found. Confirm with your DA RFO 8 technician.",
            readingNote = "Find each of your averaged numbers in its row. For ampalaya, too much nitrogen makes leaves instead of fruit.",
            example = listOf(
                "Average of 5 readings: pH 5.3 · N 45 · P 22 · K 110",
                "pH 5.3 → ! SLIGHTLY LOW (a bit sour)",
                "N 45, P 22 and K 110 → ✓ GOOD",
                "pH comes first: code 2. Lime with 2 kg per bed, wait 2 weeks, then plant and follow the standard plan.",
            ),
            why = "The pH range (5.5–6.5) is from the BPI ampalaya guide. Ampalaya is a climbing vine that fruits for 2–3 months, so it needs steady nitrogen and potassium; the BPI guide side-dresses with urea and muriate of potash. The N, P and K boxes are SakaSense estimates for the probe, not figures from the guide. Check them with a DA laboratory test.",
            planIntro = "Use this plan when all four readings are ✓ GOOD. A 20 m² bed holds about 18 hills.",
            plan = listOf(
                PlanStep("2 weeks before planting", "400 g dolomite and 4 kg processed chicken manure or vermicompost, mixed into the soil.", "4 bags dolomite + 2 tons manure"),
                PlanStep("Basal, at planting", "200 g complete 14-14-14 (¾ cup), about 1 tablespoon (10–15 g) in each hill.", "2 bags 14-14-14"),
                PlanStep("Side-dress once a month", "200 g urea (1 cup) + 200 g muriate of potash 0-0-60 (¾ cup): 10–15 g of each per hill, beside the plants. Cover and water.", "2 bags urea + 2 bags 0-0-60 each time"),
                PlanStep("Every week from planting to fruiting", "Spray fermented plant juice (FPJ), as in the BPI guide.", "—"),
            ),
            planTips = listOf(
                "In the rainy season rain washes fertilizer away. Split the side-dress into two half doses, two weeks apart.",
                "Do not put fertilizer on the leaves or touch the stems.",
                "Wrap fruits in clear plastic or paper to protect them from fruit flies.",
                "Book estimate: the BPI guide gives amounts per hill. The per-bed amounts here are those figures times 18 hills. The \"split in two in the rainy season\" tip is the SakaSense estimate.",
            ),
            planSource = "Dolomite, manure, basal and side-dress rates: BPI Ampalaya Production Guide (2013), a national guide. Spacing 1.5 m × 0.75 m is also from that guide.",
            fixIntro = "Amounts are in addition to the standard plan. For ! SLIGHTLY LOW use half.",
            fixes = listOf(
                Fix(
                    SoilCondition.BALANCED, "Follow the standard plan (page 30). Test again 2 weeks after the first flowers.",
                    "Standard plan: dolomite + manure, then 14-14-14",
                    listOf(Dose(Products.DOLOMITE, 400), Dose(Products.ORGANIC, 4000), Dose(Products.COMPLETE, 200)),
                    "Dolomite + manure 2 weeks before planting; 14-14-14 at planting; urea + 0-0-60 every month",
                ),
                Fix(
                    SoilCondition.ACIDIC, "Lime first. 4 kg agricultural lime or dolomite per bed (2 kg if only slightly sour) on top of the 400 g in the plan. Mix into the top 15 cm and water. Wait 2 weeks before planting. See page 36.",
                    Products.LIME, listOf(Dose(Products.LIME, 4000)), "Mix into the top 15 cm; wait 2 weeks before planting",
                ),
                Fix(SoilCondition.ALKALINE, "No lime or wood ash. Use ammonium sulfate 21-0-0 instead of urea (2 g for every 1 g urea) and add 1 extra sack of compost.", ALKALINE_PRODUCT, alkalineDoses, ALKALINE_TIMING),
                Fix(
                    SoilCondition.N_LOW, "Add 100 g urea per bed, in two parts two weeks apart. Do not add more; too much nitrogen gives leaves, not fruit.",
                    Products.UREA, listOf(Dose(Products.UREA, 100)), "In two parts, two weeks apart; never more",
                ),
                Fix(
                    SoilCondition.P_LOW, "Mix 300 g ammonium phosphate 16-20-0 (or 400 g Solophos 0-18-0) into the planting hills. On a growing crop, place it in a shallow furrow beside the row, cover and water.",
                    Products.AMMONIUM_PHOSPHATE, listOf(Dose(Products.AMMONIUM_PHOSPHATE, 300)), "Into the planting hills; on a growing crop, in a furrow beside the row",
                    alternative = Dose(Products.SOLOPHOS, 400),
                ),
                Fix(
                    SoilCondition.K_LOW, "Add 100 g muriate of potash 0-0-60 per bed, split between two side-dresses.",
                    Products.POTASH, listOf(Dose(Products.POTASH, 100)), "Split between two side-dresses",
                ),
                Fix(
                    SoilCondition.MULTI_LOW, "Add 200 g more 14-14-14 and 1 more sack of manure or compost at planting. Test again 2 weeks after the seedlings are planted and fix what is still low. If pH is also low, do code 2 first.",
                    "Complete 14-14-14 + manure or compost", listOf(Dose(Products.COMPLETE, 200), Dose(Products.COMPOST, sacks = 1)),
                    "At planting; test again 2 weeks after planting",
                ),
            ),
            tooHigh = mapOf(
                Nutrient.N to "Skip the next urea.",
                Nutrient.P to "Use urea + 0-0-60 instead of 14-14-14.",
                Nutrient.K to "Skip 0-0-60.",
            ),
        ),
        CropGuide(
            crop = "Corn",
            scientificName = "Zea mays L. · mais",
            page = 32,
            harvest = "95–110 days (dry grain)",
            spacing = "75 × 20–25 cm",
            growing = listOf(
                "Soil. Well-drained loam. In Leyte most corn is white corn, grown for food. Test the soil first: if pH is below 5.5, lime.",
                "Prepare the land. Plow and harrow 2–3 times. If lime is needed, spread it at least 4 weeks before planting (page 36).",
                "Make furrows 75 cm apart and about 8 cm deep on the day you plant. Put the basal fertilizer in the furrow and cover it with a little soil (page 34).",
                "Plant one seed per hill, 20–25 cm apart in the furrow. Do not let the seed touch the fertilizer.",
                "Side-dress 25–30 days after planting beside the rows. Weed and hill up at the same time. Water in dry weather, especially when the tassels and silks appear.",
                "Harvest dry grain 95–110 days after planting, when the husks are dry and the kernels are hard. For green (boiled) corn, pick earlier, when the silks are brown and the kernels are milky.",
            ),
            pests = "Check the center of each plant (the whorl) twice a week for corn borer and fall armyworm. Remove weeds, rotate with a legume or root crop, and ask your agriculture technician what to spray and when.",
            growingSource = "Region 8: Leyte makes about 60% of the region's corn (PSA RSSO 8, Q1 2025), and 93% of Leyte's corn is white corn (Q4 2024). National guide, not Region 8: spacing, lime and harvest days from a Philippine corn extension guide (undated, no agency named). Confirm with your DA RFO 8 technician.",
            readingNote = "Find each of your averaged numbers in its row.",
            example = listOf(
                "Average of 5 readings: pH 6.1 · N 28 · P 12 · K 95",
                "pH 6.1 and K 95 → ✓ GOOD",
                "N 28 → ! SLIGHTLY LOW",
                "P 12 → ! SLIGHTLY LOW",
                "Two nutrients are short, so the code is 7. Add the extra 14-14-14 at planting and test again 2 weeks after the plants come up.",
            ),
            why = "The BSWM maps of Leyte (page 6) were made for the key corn areas of the province, and phosphorus was the most common shortage. Corn takes up a lot of nitrogen. The standard plan is the national corn rate of 90-60-60 kg N-P₂O₅-K₂O per hectare, which a Visayas State University trial in Inopacan, Leyte used. The N, P and K boxes are SakaSense estimates for the probe. Check them with a DA laboratory test.",
            planIntro = "Use this plan when all four readings are ✓ GOOD. Amounts are for 20 m² (about 13 m of row).",
            plan = listOf(
                PlanStep("At planting, in the furrow", "800 g complete 14-14-14 (about 3 cups). Cover with soil before sowing.", "8 bags 14-14-14"),
                PlanStep("25–30 days after planting, side-dress", "150 g urea 46-0-0 (¾ cup) beside the rows. Cover with soil and water.", "1½ bags urea"),
            ),
            planTips = listOf(
                "This gives about 90-56-56 kg N-P₂O₅-K₂O per hectare, close to the national 90-60-60 rate.",
                "Organic option: a VSU trial in Inopacan, Leyte, found half the fertilizer plus chicken dung worked as well as the full rate. That is 400 g 14-14-14 + 75 g urea + about 1 sack of well-rotted chicken dung.",
                "Apply urea in the late afternoon and water it in. Never let it touch the leaves.",
                "Book estimate: the sack of chicken dung is the SakaSense estimate; the VSU summary does not give the amount. Ask your agriculture technician to confirm it.",
            ),
            planSource = "Rate: national corn recommendation (90-60-60) used by Lina et al., Annals of Tropical Research 36(Suppl), VSU, Inopacan, Leyte (2014). Organic option: same trial and Ratilla et al. (2014), Inopacan, Leyte.",
            fixIntro = "Amounts are in addition to the standard plan. For ! SLIGHTLY LOW use half.",
            fixes = listOf(
                Fix(
                    SoilCondition.BALANCED, "Follow the standard plan (page 34). Test again 2 weeks after side-dressing.",
                    "Standard plan: 14-14-14, then urea", listOf(Dose(Products.COMPLETE, 800)),
                    "In the furrow at planting; 150 g urea at 25–30 days",
                ),
                Fix(
                    SoilCondition.ACIDIC, "Lime first. 4 kg agricultural lime or dolomite per 20 m² (2 kg if only slightly sour), mixed into the top 15 cm and watered. Wait 4 weeks before planting. See page 36.",
                    Products.LIME, listOf(Dose(Products.LIME, 4000)), "Mix into the top 15 cm; wait 4 weeks before planting",
                ),
                Fix(SoilCondition.ALKALINE, "No lime or wood ash. Use ammonium sulfate 21-0-0 instead of urea (2 g for every 1 g urea) and work in 1 sack of compost.", ALKALINE_PRODUCT, alkalineDoses, ALKALINE_TIMING),
                Fix(
                    SoilCondition.N_LOW, "Add 100 g urea per 20 m², in two parts: at 25–30 days and again 2 weeks later, beside the rows and watered in.",
                    Products.UREA, listOf(Dose(Products.UREA, 100)), "At 25–30 days and again 2 weeks later",
                ),
                Fix(
                    SoilCondition.P_LOW, "Mix 300 g ammonium phosphate 16-20-0 (or 400 g Solophos 0-18-0) into the planting furrow. Phosphorus barely moves in soil, so keep it near the roots.",
                    Products.AMMONIUM_PHOSPHATE, listOf(Dose(Products.AMMONIUM_PHOSPHATE, 300)), "Into the planting furrow",
                    alternative = Dose(Products.SOLOPHOS, 400),
                ),
                Fix(
                    SoilCondition.K_LOW, "Add 100 g muriate of potash 0-0-60 per 20 m² at the side-dress, beside the rows.",
                    Products.POTASH, listOf(Dose(Products.POTASH, 100)), "At the side-dress, beside the rows",
                ),
                Fix(
                    SoilCondition.MULTI_LOW, "Add 300 g more 14-14-14 and 1 sack of manure or compost at planting. Test again 2 weeks after the plants come up and fix what is still low. If pH is also low, do code 2 first.",
                    "Complete 14-14-14 + manure or compost", listOf(Dose(Products.COMPLETE, 300), Dose(Products.COMPOST, sacks = 1)),
                    "At planting; test again 2 weeks after the plants come up",
                ),
            ),
            tooHigh = mapOf(
                Nutrient.N to "Skip the urea side-dress.",
                Nutrient.P to "Use urea + 0-0-60 instead of 14-14-14.",
                Nutrient.K to "Skip 0-0-60.",
            ),
        ),
    )

    val topics: List<GuideTopic> = listOf(
        GuideTopic(
            "how-to-use", "Start here", "How to use this guidebook", 2,
            listOf(
                GuideBlock.Para("Your SakaSense device checks four things in your soil: pH (how sour it is), N nitrogen, P phosphorus and K potassium. This book tells you if each number is right for your crop, and what to do if it is not."),
                GuideBlock.Steps(
                    listOf(
                        "Test. Take 5 readings in your bed (page 4).",
                        "Get your code. The app shows a number from 1 to 7.",
                        "Do the fix. Find that number on your crop's Fix-it page.",
                    )
                ),
                GuideBlock.Heading("Every number lands in one of four boxes"),
                GuideBlock.Table(
                    listOf("Box", "What it means"),
                    listOf(
                        listOf("✕ LOW", "Clearly not enough. Use the full fix."),
                        listOf("! SLIGHTLY LOW", "A little short. Use half the fix."),
                        listOf("✓ GOOD", "Just right. Follow the standard plan."),
                        listOf("▲ HIGH", "More than enough. Skip that fertilizer."),
                    ),
                ),
                GuideBlock.Heading("Amounts in this book"),
                GuideBlock.Para("All amounts are for one bed, 1 m × 20 m (20 m²). One 50-kg bag per hectare equals 100 g per bed."),
                GuideBlock.Note("The probe gives a quick field estimate, not a laboratory test. For a new field, or once a year, bring a soil sample to the DA Regional Field Office 8 Soils Laboratory (page 38). If the lab and your probe disagree, follow the lab. Always follow the label on fertilizer bags."),
                GuideBlock.Para("Soil facts come from BSWM maps of Leyte. Crop advice comes from Visayas State University research in Leyte and from national DA, BPI and ATI guides. Page 40 shows which sources are from Region 8 and which are not."),
            ),
        ),
        GuideTopic(
            "reading", "Start here", "Taking a good soil reading", 4,
            listOf(
                GuideBlock.Para("One reading from one spot can mislead you. Soil changes from one corner of a bed to the next, so always take five readings per bed and use the average."),
                GuideBlock.Steps(
                    listOf(
                        "Pick 5 spots in a zig-zag (W shape) across the bed. Stay away from the edges, compost piles and places where fertilizer was just put.",
                        "Clear the surface. Push away grass, mulch and stones.",
                        "Make sure the soil is moist, not dry and not flooded. If it is dry, water the spot and wait 30 minutes.",
                        "Push the probe in to the root zone (about 10–15 cm deep). The metal parts must touch the soil fully, with no air gaps.",
                        "Press Measure on the device or in the Kasaka app and keep the probe still until the numbers stop changing (up to 45 seconds).",
                        "Write down pH, N, P and K. Do the same at the other 4 spots, then add each number up and divide by 5. The Kasaka app does this for you when you measure all 5 spots.",
                        "Wipe the probe clean with clean water and a cloth. Store it dry.",
                    )
                ),
                GuideBlock.Heading("When to test"),
                GuideBlock.Bullets(
                    listOf(
                        "3 weeks before planting, so there is time to lime.",
                        "Mid-season, 2 weeks after side-dressing.",
                        "After harvest, to plan the next crop.",
                    )
                ),
                GuideBlock.Heading("Do not test"),
                GuideBlock.Bullets(
                    listOf(
                        "within 7 days after fertilizing",
                        "within 3–4 weeks after liming",
                        "in waterlogged soil, or right after heavy rain",
                    )
                ),
                GuideBlock.Note("The device may also show temperature and humidity. These are for your records only; the soil advice in this book uses pH, N, P and K."),
            ),
        ),
        GuideTopic(
            "npk", "Start here", "Understanding pH, N, P and K", 5,
            listOf(
                GuideBlock.Heading("pH: how sour or bitter the soil is"),
                GuideBlock.Para("Below 5.5 the soil is sour (acidic); above 7.0 it is bitter (alkaline). Best for most vegetables: 5.5–7.0."),
                GuideBlock.Para("pH is the key that unlocks food in the soil. When soil is too sour, the fertilizer you apply gets locked up and the plant cannot use it, so you waste money. Fix pH first, then fertilize."),
                GuideBlock.Table(
                    listOf("Nutrient", "What it does", "Signs it is low"),
                    listOf(
                        listOf("N Nitrogen", "Green leaves and fast growth. Leafy crops (kangkong, pechay) need the most.", "Pale or yellow older leaves, slow small plants"),
                        listOf("P Phosphorus", "Strong roots, flowers and pods. Very important at planting.", "Weak roots, dark or purplish leaves, few flowers"),
                        listOf("K Potassium", "Root size, pod quality, strong stems and resistance to disease. Kamote needs the most.", "Brown, burnt leaf edges; small roots; weak stems"),
                    ),
                ),
                GuideBlock.Heading("The four reading boxes"),
                GuideBlock.Table(
                    listOf("Box", "What to do"),
                    listOf(
                        listOf("✕ LOW", "Clearly not enough. Act now with the full fix dose."),
                        listOf("! SLIGHTLY LOW", "A little short. Use half the fix dose."),
                        listOf("✓ GOOD", "Right for the crop. Follow the standard plan only."),
                        listOf("▲ HIGH", "More than enough. Skip fertilizer with that nutrient; it is wasted money and can harm the crop."),
                    ),
                ),
                GuideBlock.Note("N, P and K are shown in mg/kg (the same as ppm). These are the units on your SakaSense screen and in the Kasaka app."),
            ),
        ),
        GuideTopic(
            "leyte-soils", "Start here", "The soils of Tacloban and Leyte", 6,
            listOf(
                GuideBlock.Para("The Bureau of Soils and Water Management, with DA Regional Field Office 8 and the LGUs, tested farm soils across 15,000 hectares of key farm areas in Leyte province (Soil Health Assessment, maps published 2018). Here is what they found."),
                GuideBlock.Table(
                    listOf("Soil pH", "Share of tested area"),
                    listOf(
                        listOf("5.6–6.8 (good)", "59%"),
                        listOf("5.1–5.5", "16%"),
                        listOf("4.6–5.0", "3%"),
                        listOf("4.5 or below, or 6.9 and above", "22%"),
                    ),
                ),
                GuideBlock.Table(
                    listOf("BSWM rating", "Phosphorus (P)", "Potassium (K)"),
                    listOf(
                        listOf("Low", "21%", "10%"),
                        listOf("Moderately low", "16%", "13%"),
                        listOf("Moderately high", "22%", "17%"),
                        listOf("High", "40%", "60%"),
                    ),
                ),
                GuideBlock.Heading("What this means for your farm"),
                GuideBlock.Bullets(
                    listOf(
                        "Most soils have a good pH (5.6–6.8), but about 1 in 5 farms is sour enough to need lime. Around Tacloban, Palo and Santa Fe the maps show mostly good pH with some strongly acidic patches.",
                        "Phosphorus is the most common shortage. Almost 4 in 10 farms are low or moderately low. Low-P patches are common between Tanauan, Tabontabon, Dagami and Tolosa.",
                        "Potassium is usually enough. 6 in 10 farms rate high, so many farmers can use less potash. Test first before buying.",
                        "The very high-pH (alkaline) soils are mostly on limestone in western Leyte, not around Tacloban.",
                    )
                ),
                GuideBlock.Note("Every farm is different. These are averages, not your result. Your own reading tells you about your bed."),
                GuideBlock.Para("Source: BSWM, Province of Leyte Soil pH Map and Nutrient Status Maps (P, K), Key Corn Areas, 1:130,000, Soil Health Assessment Phase III with DA-RFO 8 and LGUs, 2018. K percentages recalculated from the map's hectare figures."),
            ),
        ),
        GuideTopic(
            "choose-crop", "Start here", "Which crop suits my soil?", 7,
            listOf(
                GuideBlock.Table(
                    listOf("Crop", "Good pH", "Sour soil?", "Needs most", "Water", "First harvest"),
                    listOf(
                        listOf("Kangkong", "5.5–7.0", "Fair", "N", "A lot, daily", "3–4 weeks"),
                        listOf("Pechay", "5.5–7.0", "Poor", "N", "Daily", "3–4 weeks"),
                        listOf("Sweet potato", "5.5–6.8", "Best", "K", "Little", "3–4 months"),
                        listOf("Okra", "5.5–7.0", "Fair", "N, P", "When dry", "40–75 days"),
                        listOf("Eggplant", "5.5–6.8", "Fair", "N, K", "35–40 mm a week", "46–50 days*"),
                        listOf("Ampalaya", "5.5–6.5", "Fair", "N, K", "Regular", "45–75 days"),
                        listOf("Corn", "5.5–7.0", "Fair", "N, P", "When dry", "95–110 days"),
                    ),
                ),
                GuideBlock.Para("* after transplanting. Others: after planting."),
                GuideBlock.Heading("Quick choices"),
                GuideBlock.Bullets(
                    listOf(
                        "pH between 5.0 and 5.4 and no money for lime? Plant sweet potato. It tolerates sour soil best of the four.",
                        "Low spot that stays wet? Plant kangkong. Pechay, okra and sweet potato all suffer in waterlogged soil.",
                        "Need cash fast? Pechay and kangkong are ready in about a month.",
                        "Low nitrogen and little money for fertilizer? Sweet potato needs the least nitrogen. Too much N actually makes small roots.",
                        "Rotate. Do not plant pechay in the same bed season after season; it invites club root. Do not follow eggplant with another nightshade crop (it invites bacterial wilt).",
                    )
                ),
                GuideBlock.Note("Tacloban weather: rain falls all year, heaviest from November to January, and typhoons come often. Use raised beds (15–20 cm) and drainage canals for pechay and okra during the wet months."),
            ),
        ),
        GuideTopic(
            "lime", "Soil care", "Fixing sour (acidic) and alkaline soil", 36,
            listOf(
                GuideBlock.Para("Lime is not fertilizer. It does not feed the plant; it unlocks the food already in the soil and the fertilizer you add later. Many Leyte soils are sour because of heavy rain and years of chemical fertilizer."),
                GuideBlock.Heading("How much lime?"),
                GuideBlock.Table(
                    listOf("Your pH reading", "Per bed (20 m²)", "Per hectare"),
                    listOf(
                        listOf("! SLIGHTLY LOW (a bit sour)", "2 kg (about 7 cups)", "1 ton"),
                        listOf("✕ LOW (too sour)", "4 kg (about 13 cups)", "2 tons"),
                        listOf("Too sour, heavy clay soil", "up to 6 kg", "up to 3 tons"),
                    ),
                ),
                GuideBlock.Para("Sandy soils need less lime than clay soils. These are starting amounts that raise pH by roughly half to one unit. A DA soil lab test gives the exact lime requirement for your field."),
                GuideBlock.Heading("How to lime"),
                GuideBlock.Steps(
                    listOf(
                        "Use agricultural lime (ground limestone) or dolomite. Dolomite also adds magnesium.",
                        "Spread it evenly over the bed 2–4 weeks before planting.",
                        "Mix it into the top 15 cm of soil and water it in. Lime only works when it is mixed with moist soil.",
                        "Wait at least 2 weeks before applying urea or ammonium fertilizer. Lime and urea together lose nitrogen to the air.",
                        "Test pH again after 3–4 weeks. Do not lime again within the same season unless the reading is still low.",
                    )
                ),
                GuideBlock.Heading("Alkaline soil (pH too high)"),
                GuideBlock.Bullets(
                    listOf(
                        "Do not add lime, dolomite or wood ash.",
                        "Work in compost or well-rotted manure every season.",
                        "Use ammonium sulfate 21-0-0 as your nitrogen source; it slowly lowers pH. Use 2 g of ammonium sulfate for every 1 g urea in the plan.",
                    )
                ),
            ),
        ),
        GuideTopic(
            "fertilizers", "Soil care", "Fertilizers and how to measure them", 37,
            listOf(
                GuideBlock.Table(
                    listOf("Product", "N-P-K", "Use it for"),
                    listOf(
                        listOf("Complete", "14-14-14", "Basal feeding of all crops; \"several low\""),
                        listOf("Urea", "46-0-0", "Nitrogen low; side-dressing leafy crops"),
                        listOf("Ammonium sulfate", "21-0-0", "Nitrogen on alkaline soil"),
                        listOf("Ammonium phosphate", "16-20-0", "Phosphorus low (also gives some N)"),
                        listOf("Solophos", "0-18-0", "Phosphorus low"),
                        listOf("Muriate of potash (MOP)", "0-0-60", "Potassium low; kamote roots"),
                        listOf("Agricultural lime / dolomite", "—", "Sour soil (raises pH)"),
                    ),
                ),
                GuideBlock.Para("The three numbers are the percent of N, P₂O₅ and K₂O in the bag. One 50-kg bag of 14-14-14 has 7 kg of each."),
                GuideBlock.Heading("No scale? Measuring by spoon and cup"),
                GuideBlock.Table(
                    listOf("Product", "1 level tablespoon", "1 level cup (250 mL)"),
                    listOf(
                        listOf("Urea 46-0-0", "≈ 10 g", "≈ 190 g"),
                        listOf("14-14-14, 16-20-0, 21-0-0", "≈ 15 g", "≈ 260 g"),
                        listOf("Muriate of potash 0-0-60", "≈ 15 g", "≈ 260 g"),
                        listOf("Lime / dolomite powder", "≈ 12 g", "≈ 300 g"),
                    ),
                ),
                GuideBlock.Para("Granule sizes differ between brands. Best practice: weigh one cup of your fertilizer on a store scale once, and mark the level on your scoop."),
                GuideBlock.Note("Quick facts: 1 bed = 1 m × 20 m = 20 m². One 50-kg bag per hectare = 100 g per bed."),
                GuideBlock.Note("Safety: wear gloves; wash hands before eating; keep fertilizer away from children, animals and drinking water; never store it with food or seeds."),
            ),
        ),
        GuideTopic(
            "compost-lab", "Soil care", "Compost and the DA soil laboratory", 38,
            listOf(
                GuideBlock.Heading("Feed the soil, not only the plant"),
                GuideBlock.Para("Soil tests in Leyte and Southern Leyte keep finding low organic matter and sour soil, linked to years of chemical-only fertilizing. Municipal agriculture officers in Region VIII recommend locally available organic amendments such as poultry manure and rice hull."),
                GuideBlock.Bullets(
                    listOf(
                        "Add 1–2 sacks of compost or manure per bed every season. It holds water and fertilizer so less is washed away by the rain.",
                        "Rice-hull ash and carbonized rice hull loosen clay and add some potassium.",
                        "Use well-rotted manure only. Fresh manure burns roots.",
                        "Mulch with rice straw to protect the soil from heavy rain.",
                    )
                ),
                GuideBlock.Heading("Getting a laboratory soil test"),
                GuideBlock.Para("The SakaSense probe is for quick checks. A lab test is more complete (it includes organic matter and the exact lime requirement). Get one for every new field and once a year."),
                GuideBlock.Steps(
                    listOf(
                        "Take soil from 10–15 spots in a zig-zag across the field, 0–20 cm deep, using a clean spade.",
                        "Mix it all in a clean pail. Take about 1 kg of the mixed soil.",
                        "Dry it in the shade (not in the sun) and remove stones and roots.",
                        "Put it in a clean plastic bag and label it: your name, barangay, field, crop planned, date.",
                        "Bring it to the DA RFO 8 Regional Soils Laboratory, or ask your City/Municipal Agriculture Office about the DA Mobile Soil Laboratory schedule.",
                    )
                ),
                GuideBlock.Note("Reading your lab report: the lab rates phosphorus (P) and potassium (K) as Low, Moderately low, Moderately high or High. Lab and probe use different methods, so compare the rating, not the number."),
            ),
        ),
        GuideTopic(
            "codes", "Look-up", "Code finder", 39,
            listOf(
                GuideBlock.Para("Every crop uses the same seven codes. Find your number, then open your crop's Fix-it page: Kangkong p. 11 · Pechay p. 15 · Sweet potato p. 19 · Okra p. 23 · Eggplant p. 27 · Ampalaya p. 31 · Corn p. 35."),
                GuideBlock.Table(listOf("Code", "Meaning", "What to do"), SoilCondition.entries.map { listOf("${it.code}", it.title, it.summary) }),
                GuideBlock.Heading("How the code is chosen"),
                GuideBlock.Para("pH comes first. If pH is low or slightly low the code is 2 (for sweet potato, only when pH is below 4.5), and if pH is too high it is 3. If pH is fine and two or more of N, P and K are short, the code is 7. If only one is short, the code is 4, 5 or 6."),
                GuideBlock.Note("Your soil record: the Kasaka app keeps every reading, its code and the fix for each plot. Open a plot to see its history."),
            ),
        ),
        GuideTopic(
            "sources", "Look-up", "Sources and contacts", 40,
            listOf(
                GuideBlock.Heading("From Region 8 (Eastern Visayas)"),
                GuideBlock.Bullets(
                    listOf(
                        "BSWM. Province of Leyte Soil pH Map; Nutrient Status Maps for P and K (Key Corn Areas), 1:130,000. Soil Health Assessment Phase III with DA-RFO 8 and LGUs, 2018.",
                        "DA RFO 8 Mobile Soil Laboratory results, Southern Leyte (Daily Tribune, 29 May 2026).",
                        "Asio, Tamayo and dela Cruz. Response of a traditional sweetpotato variety to fertilization in Leyte. Tropentag 2018 (VSU). PhilRootcrops, VSU, Baybay City.",
                        "Lina, Maranguit, Asio, Sabijon, Demain and Bolledo. Growth performance of corn with organic and inorganic fertilizers in a marginal upland soil. Annals of Tropical Research 36(Suppl):16–29, VSU, 2014 (Inopacan, Leyte).",
                        "Ratilla, Toledo and Capuno. Organic-based fertilizers for corn in the marginal uplands of Inopacan, Leyte. Annals of Tropical Research 36(Suppl):107–123, 2014.",
                        "PSA Regional Statistics Office 8. Corn Situationer, Leyte, 4th Quarter 2024 and 1st Quarter 2025.",
                    )
                ),
                GuideBlock.Heading("National guides, NOT from Region 8"),
                GuideBlock.Para("No Region 8 growing guide was found for these crops, so national guides are used. Ask your agriculture technician to confirm the amounts."),
                GuideBlock.Bullets(
                    listOf(
                        "Kangkong, pechay, okra: DA RFU VII Guide on Vegetable Production; ATI-VII production guides (2020); BPI Kangkong and Okra guides; DA Caraga pechay guide; DA RFO 02 Okra and Sweet Potato guides.",
                        "Eggplant: ATI e-extension eggplant guide; BPI Organic Eggplant Production guide.",
                        "Ampalaya: BPI Ampalaya Production Guide (2013), including PSA 2011 figures for Eastern Visayas.",
                        "Corn: a Philippine extension guide for spacing, lime and harvest days (undated, no agency named).",
                        "Kangkong rate study on acid Ultisols (SANREM CRSP / IPB); Philippine pechay 60-40-60 kg/ha study.",
                    )
                ),
                GuideBlock.Heading("Where to get help"),
                GuideBlock.Bullets(
                    listOf(
                        "City / Municipal Agriculture Office: technicians, seeds, Mobile Soil Laboratory schedule",
                        "DA Regional Field Office 8, Tacloban City: Regional Soils Laboratory",
                        "VSU PhilRootcrops, Baybay City: clean sweet potato planting material",
                        "SakaSense Venture Solutions, Tacloban City: device and Kasaka app help · sakasense.com",
                    )
                ),
                GuideBlock.Note("The N, P and K reading boxes are SakaSense estimates for the probe, set using BSWM ratings and the research above. They are a guide, not a guarantee. Check them with a DA laboratory test."),
            ),
        ),
    )

    /**
     * For crops the app knew before the Kasaka Guidebook (tomato, rice…) but the book has no chapter for.
     * The amounts are the ones the book uses for most of its crops; the advice says it is general.
     */
    val generalFixes: List<Fix> = listOf(
        Fix(
            SoilCondition.BALANCED, "All readings are good. Keep your usual plan for this crop, or use 300 g complete 14-14-14 and 1 sack of manure per bed at planting. Test again in 2–3 weeks.",
            "Your usual plan (or 14-14-14 + manure)", listOf(Dose(Products.COMPLETE, 300), Dose(Products.COMPOST, sacks = 1)), "At planting",
        ),
        Fix(
            SoilCondition.ACIDIC, "Lime first. 4 kg agricultural lime or dolomite per bed (2 kg if only slightly sour), mixed into the top 15 cm and watered. Wait 2 weeks before planting or putting urea. See page 36.",
            Products.LIME, listOf(Dose(Products.LIME, 4000)), "Mix into the top 15 cm; wait 2 weeks before planting or urea",
        ),
        Fix(SoilCondition.ALKALINE, ALKALINE_FIX, ALKALINE_PRODUCT, alkalineDoses, ALKALINE_TIMING),
        Fix(
            SoilCondition.N_LOW, "Add 100 g urea per bed, in two parts one week apart, dissolved in water.",
            Products.UREA, listOf(Dose(Products.UREA, 100)), "In two parts, one week apart, dissolved in water",
        ),
        Fix(
            SoilCondition.P_LOW, "Mix 300 g ammonium phosphate 16-20-0 (or 400 g Solophos 0-18-0) into the rows at planting. On a growing crop, place it in a shallow furrow beside the rows, cover and water.",
            Products.AMMONIUM_PHOSPHATE, listOf(Dose(Products.AMMONIUM_PHOSPHATE, 300)), "At planting, in the rows; on a growing crop, in a furrow beside the rows",
            alternative = Dose(Products.SOLOPHOS, 400),
        ),
        Fix(
            SoilCondition.K_LOW, "Add 100 g muriate of potash 0-0-60 per bed, spread between rows and watered in.",
            Products.POTASH, listOf(Dose(Products.POTASH, 100)), "Spread between rows and water in",
        ),
        Fix(
            SoilCondition.MULTI_LOW, "Add 300 g more 14-14-14 and 1 more sack of manure or compost. Test again after 2 weeks and fix whatever is still low. If pH is also low, do code 2 first.",
            "Complete 14-14-14 + manure or compost", listOf(Dose(Products.COMPLETE, 300), Dose(Products.COMPOST, sacks = 1)),
            "At planting or as a side-dress; test again after 2 weeks",
        ),
    )
    val generalTooHigh: Map<Nutrient, String> = standardTooHigh

    fun crop(name: String?): CropGuide? {
        val profile = CropProfiles.find(name) ?: return null
        return crops.firstOrNull { it.crop == profile.name }
    }

    /** The crop's Fix-it entry, or the general one when the book has no chapter for it. */
    fun fix(crop: String, condition: SoilCondition): Fix =
        crop(crop)?.fix(condition) ?: generalFixes.first { it.condition == condition }

    fun tooHigh(crop: String): Map<Nutrient, String> = crop(crop)?.tooHigh ?: generalTooHigh

    fun topic(id: String): GuideTopic? = topics.firstOrNull { it.id == id }

    /** True for a code this edition of the book has (1–7). Older 4-digit device codes are not. */
    fun isCode(code: String?): Boolean = SoilCondition.fromCode(code) != null
}
