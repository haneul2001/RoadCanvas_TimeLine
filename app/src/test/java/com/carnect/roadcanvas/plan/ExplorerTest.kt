package com.carnect.roadcanvas.plan

import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class ExplorerTest {

    companion object {
        lateinit var sgg: RegionIndex
        lateinit var explorer: Explorer

        @BeforeClass
        @JvmStatic
        fun load() {
            sgg = File("src/main/assets/${Level.SGG.asset}").inputStream().use { RegionIndex.load(Level.SGG, it) }
            explorer = Explorer(sgg)
        }
    }

    private fun code(name: String) = sgg.regions.first { it.name == name && it.province.startsWith("강원") }.code

    @Test
    fun `강원도에서 아직 안 간 곳만 가까운 순으로 추천한다`() {
        val visited = setOf(code("속초시"), code("양양군"), code("강릉시"))
        val list = explorer.quest(visited, 38.19 to 128.60, province = "강원")
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.region.province.startsWith("강원") && it.region.code !in visited })
        // 속초 바로 위 고성군이 가장 가깝다
        assertEquals("고성군", list.first().region.name)
    }

    @Test
    fun `목적지로 가는 길에서 조금만 돌아가는 곳만 고른다`() {
        val dest = Place("강릉시", 37.7519, 128.8761, code("강릉시"))
        val list = explorer.detour(emptySet(), 38.19 to 128.60, dest, maxExtraKm = 20.0)
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { (it.extraKm ?: 99.0) <= 20.0 })
        assertFalse(list.any { it.region.code == dest.regionCode })
        // 속초 → 강릉 사이 양양이 들어 있다
        assertTrue(list.any { it.region.name == "양양군" })
    }
}
