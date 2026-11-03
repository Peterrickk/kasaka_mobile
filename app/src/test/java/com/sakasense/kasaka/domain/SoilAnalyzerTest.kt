package com.sakasense.kasaka.domain

import com.sakasense.kasaka.data.DeviceVerdict
import com.sakasense.kasaka.data.local.FarmType
import com.sakasense.kasaka.data.toRecommendation
import com.sakasense.kasaka.data.local.PlotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoilAnalyzerTest {

    @Test
    fun `health index matches the web dashboard formula`() {
        // Demo plots from sakasense.com/app and the status the dashboard shows for them.
        assertEquals(82, SoilAnalyzer.healthIndex(SoilSample(5.8, 58, 42, 82)))
        assertEquals(PlotStatus.HEALTHY, SoilAnalyzer.statusFor(82))
        assertEquals(PlotStatus.NEEDS_ATTENTION, SoilAnalyzer.statusFor(SoilAnalyzer.healthIndex(SoilSample(6.2, 48, 40, 58))))
        assertEquals(PlotStatus.ACTION_REQUIRED, SoilAnalyzer.statusFor(SoilAnalyzer.healthIndex(SoilSample(5.2, 32, 38, 38))))
        assertEquals(PlotStatus.HEALTHY, SoilAnalyzer.statusFor(SoilAnalyzer.healthIndex(SoilSample(6.6, 65, 42, 70))))
        assertEquals(PlotStatus.NEEDS_ATTENTION, SoilAnalyzer.statusFor(SoilAnalyzer.healthIndex(SoilSample(6.1, 48, 22, 50))))
    }

    @Test
    fun `perfect soil scores 100`() {
        assertEquals(100, SoilAnalyzer.healthIndex(SoilSample(6.5, 80, 50, 80)))
    }

    // The four worked examples printed in the Kasaka Guidebook.

    @Test
    fun `kangkong example is code 4 with half the N fix`() {
        val a = SoilAnalyzer.analyze(SoilSample(5.8, 30, 18, 95), "Kangkong")
        assertEquals(Level.GOOD, a.bands[0].level)
        assertEquals(Level.SLIGHTLY_LOW, a.bands[1].level)
        assertEquals(4, a.code)
        assertTrue(a.halfDose)
        assertTrue(a.fertilizer.amount, a.fertilizer.amount.startsWith("50 g"))
    }

    @Test
    fun `pechay example puts pH first - code 2 with 2 kg lime`() {
        val a = SoilAnalyzer.analyze(SoilSample(5.2, 55, 10, 120), "Pechay")
        assertEquals(Level.SLIGHTLY_LOW, a.bands[0].level)
        assertEquals(Level.LOW, a.bands[2].level)
        assertEquals(2, a.code)
        assertTrue(a.fertilizer.amount, a.fertilizer.amount.startsWith("2 kg"))
        assertTrue(a.fertilizer.notes.contains("phosphorus"))
    }

    @Test
    fun `sweet potato example tolerates a bit sour soil - code 6 with 75 g potash`() {
        val a = SoilAnalyzer.analyze(SoilSample(5.1, 70, 16, 90), "Sweet potato")
        assertEquals(Level.SLIGHTLY_LOW, a.bands[0].level)
        assertEquals(Level.HIGH, a.bands[1].level)
        assertEquals(Level.SLIGHTLY_LOW, a.bands[3].level)
        assertEquals(6, a.code)
        assertTrue(a.fertilizer.amount, a.fertilizer.amount.startsWith("75 g"))
        assertTrue(a.fertilizer.notes.contains("N high"))
    }

    @Test
    fun `okra example with two short nutrients is code 7`() {
        val a = SoilAnalyzer.analyze(SoilSample(6.2, 32, 14, 150), "Okra")
        assertEquals(7, a.code)
        assertFalse(a.halfDose)
    }

    @Test
    fun `sweet potato is limed only below pH 4_5`() {
        val kamote = CropProfiles.find("Kamote")!!
        assertEquals(SoilCondition.ACIDIC, SoilAnalyzer.condition(SoilSample(4.4, 40, 20, 150), kamote))
        assertEquals(SoilCondition.BALANCED, SoilAnalyzer.condition(SoilSample(4.5, 40, 20, 150), kamote))
    }

    @Test
    fun `high pH is code 3`() {
        assertEquals(3, SoilAnalyzer.analyze(SoilSample(7.4, 60, 25, 120), "Okra").code)
    }

    @Test
    fun `pH is read to one decimal like the book table`() {
        val kangkong = CropProfiles.find("Kangkong")!!
        assertEquals(Level.GOOD, SoilAnalyzer.level(SoilSample(5.46, 50, 20, 100), Nutrient.PH, kangkong))
        assertEquals(Level.SLIGHTLY_LOW, SoilAnalyzer.level(SoilSample(5.44, 50, 20, 100), Nutrient.PH, kangkong))
    }

    @Test
    fun `balanced soil follows the standard plan`() {
        val a = SoilAnalyzer.analyze(SoilSample(6.3, 60, 25, 120), "Kangkong")
        assertEquals(SoilCondition.BALANCED, a.condition)
        assertTrue(a.fertilizer.product.startsWith("Standard plan"))
    }

    @Test
    fun `eggplant example is code 4 with half the N fix`() {
        val a = SoilAnalyzer.analyze(SoilSample(6.1, 38, 28, 140), "Eggplant")
        assertEquals(4, a.code)
        assertTrue(a.fertilizer.amount, a.fertilizer.amount.startsWith("50 g"))
    }

    @Test
    fun `ampalaya example puts pH first - code 2 with 2 kg lime`() {
        val a = SoilAnalyzer.analyze(SoilSample(5.3, 45, 22, 110), "Ampalaya")
        assertEquals(2, a.code)
        assertTrue(a.fertilizer.amount, a.fertilizer.amount.startsWith("2 kg"))
    }

    @Test
    fun `corn example with two short nutrients is code 7`() {
        assertEquals(7, SoilAnalyzer.analyze(SoilSample(6.1, 28, 12, 95), "Corn").code)
    }

    @Test
    fun `crops from earlier versions still get their own analysis with general amounts`() {
        // Old app: tomato pH 6.0–6.8, P target 50 -> low below 30. Same soil as the old 6123 test.
        val a = SoilAnalyzer.analyze(SoilSample(6.3, 72, 22, 85), "Tomato")
        assertEquals("Tomato", a.targetCrop)
        assertEquals(SoilCondition.P_LOW, a.condition)
        assertTrue(a.fertilizer.product.contains("16-20-0"))
        assertTrue(a.insight.startsWith("Tomato has no chapter in the Kasaka Guidebook"))
    }

    @Test
    fun `unknown crops get advice for the best book crop`() {
        val a = SoilAnalyzer.analyze(SoilSample(6.3, 60, 25, 120), "Mango")
        assertTrue(CropProfiles.find(a.targetCrop)!!.inGuidebook)
        assertTrue(a.insight.startsWith("Mango is not in the Kasaka Guidebook"))
    }

    @Test
    fun `the device code is kept only when it was for the same crop`() {
        val a = SoilAnalyzer.analyze(SoilSample(6.1, 38, 28, 140), "Eggplant")
        val same = a.toRecommendation("p", null, 0, DeviceVerdict(null, "4", "Eggplant"))
        val other = a.toRecommendation("p", null, 0, DeviceVerdict(null, "1", "Okra"))
        val old = a.toRecommendation("p", null, 0, DeviceVerdict(null, "4799"))
        assertEquals("4", same.guidebookCode)
        assertEquals("4", other.guidebookCode)
        assertEquals("4", old.guidebookCode)
        assertEquals("Eggplant", same.crop)
    }

    @Test
    fun `field amounts include the whole plot`() {
        val a = SoilAnalyzer.analyze(SoilSample(5.8, 15, 18, 95), "Kangkong", areaM2 = 5_000.0)
        assertEquals(4, a.code)
        assertTrue(a.fertilizer.amount, a.fertilizer.amount.contains("25 kg for this plot"))
    }

    @Test
    fun `container farms get per square metre doses`() {
        val a = SoilAnalyzer.analyze(SoilSample(6.3, 15, 25, 120), "Kangkong", FarmType.URBAN_CONTAINER)
        assertTrue(a.fertilizer.amount, a.fertilizer.amount.contains("per m²"))
    }

    @Test
    fun `sour soil suits sweet potato best`() {
        assertEquals("Sweet potato", SoilAnalyzer.rankCrops(SoilSample(5.2, 35, 15, 130)).first().crop)
    }

    @Test
    fun `plot sizes are read from the area label`() {
        assertEquals(5_000.0, Doses.parseAreaM2("0.5 ha")!!, 0.01)
        assertEquals(200.0, Doses.parseAreaM2("200 m²")!!, 0.01)
        assertNull(Doses.parseAreaM2("20 L pot"))
    }

    @Test
    fun `spoon measures match the book`() {
        assertEquals("≈ ½ cup", Doses.measure(Products.UREA, 100))
        assertEquals("≈ 5 tbsp", Doses.measure(Products.UREA, 50))
        assertEquals("≈ 1¼ cups", Doses.measure(Products.COMPLETE, 300))
        assertEquals("≈ 7 cups", Doses.measure(Products.LIME, 2000))
    }

    @Test
    fun `every book crop has all seven fix-it codes`() {
        Guidebook.crops.forEach { g -> assertEquals(g.crop, SoilCondition.entries.toSet(), g.fixes.map { it.condition }.toSet()) }
        assertEquals(SoilCondition.entries.toSet(), Guidebook.generalFixes.map { it.condition }.toSet())
        assertEquals(CropProfiles.inBook.map { it.name }.toSet(), Guidebook.crops.map { it.crop }.toSet())
        assertEquals(7, Guidebook.crops.size)
        assertTrue(Guidebook.isCode("4"))
        assertFalse(Guidebook.isCode("4821"))
    }
}
