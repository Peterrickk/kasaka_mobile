package com.sakasense.kasaka.domain

import com.sakasense.kasaka.data.local.FarmType
import com.sakasense.kasaka.data.local.PlotStatus
import org.junit.Assert.assertEquals
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

    @Test
    fun `acidic eggplant soil with low N and K maps to guidebook 4821`() {
        val a = SoilAnalyzer.analyze(SoilSample(5.2, 32, 38, 38), "Eggplant")
        assertEquals(SoilCondition.MULTI_LOW, a.condition)
        assertEquals("4821", a.guidebook.code)
        assertTrue(a.fertilizer.product.contains("lime", ignoreCase = true))
    }

    @Test
    fun `low phosphorus tomato maps to guidebook 6123`() {
        val a = SoilAnalyzer.analyze(SoilSample(6.3, 72, 22, 85), "Tomato")
        assertEquals(SoilCondition.P_LOW, a.condition)
        assertEquals("6123", a.guidebook.code)
        assertTrue(a.fertilizer.product.contains("16-20-0"))
    }

    @Test
    fun `container farms get per-pot doses`() {
        val a = SoilAnalyzer.analyze(SoilSample(6.3, 30, 45, 70), "Basil", FarmType.URBAN_CONTAINER)
        assertTrue(a.fertilizer.amount.contains("L"))
    }

    @Test
    fun `balanced soil needs only maintenance`() {
        val a = SoilAnalyzer.analyze(SoilSample(6.4, 75, 50, 85), "Tomato")
        assertEquals(SoilCondition.BALANCED, a.condition)
        assertEquals("3902", a.guidebook.code)
    }

    @Test
    fun `crop ranking returns three crops best first`() {
        val ranked = SoilAnalyzer.rankCrops(SoilSample(5.3, 35, 26, 62))
        assertEquals(3, ranked.size)
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertTrue(ranked.first().crop in listOf("Cassava", "Sweet potato", "Potato"))
    }

    @Test
    fun `every guidebook code is unique`() {
        val codes = Guidebook.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }
}
