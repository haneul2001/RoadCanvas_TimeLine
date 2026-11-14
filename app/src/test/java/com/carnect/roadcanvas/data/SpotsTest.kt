package com.carnect.roadcanvas.data

import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.sticker.StickerCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpotsTest {

    private val spots = File("src/main/assets/spots.json").inputStream().use(Spots::load)
    private val sgg = File("src/main/assets/${Level.SGG.asset}").inputStream().use { RegionIndex.load(Level.SGG, it) }

    @Test
    fun `모든 시·군·구에 추천 여행지가 있고 종류는 스티커와 맞다`() {
        val missing = sgg.regions.filter { spots.inRegion(it.code).isEmpty() }.map { it.name }
        assertEquals(emptyList<String>(), missing)
        assertTrue(spots.all.all { StickerCatalog.byKey(it.kind) != null })
    }

    @Test
    fun `추천 코스는 그 지역 여행지로만 이뤄진다`() {
        sgg.regions.forEach { r ->
            val course = spots.course(r.code)
            assertTrue(r.name, course.all { it.sggCode == r.code })
        }
    }
}
