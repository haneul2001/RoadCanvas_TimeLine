package com.carnect.roadcanvas.region

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class RegionIndexTest {

    companion object {
        lateinit var index: RegionIndex

        @BeforeClass
        @JvmStatic
        fun load() {
            index = File("src/main/assets/regions.json").inputStream().use(RegionIndex::load)
        }
    }

    private fun nameAt(lat: Double, lon: Double) = index.find(lat, lon)?.name

    @Test
    fun `250개 시군구를 읽는다`() {
        assertEquals(250, index.regions.size)
        assertEquals(18, index.countByProvince()["32"])
    }

    @Test
    fun `대표 지점이 맞는 시군구로 판정된다`() {
        assertEquals("종로구", nameAt(37.5759, 126.9768)) // 광화문
        assertEquals("홍천군", nameAt(37.6970, 127.8888)) // 홍천군청
        assertEquals("속초시", nameAt(38.2070, 128.5918)) // 속초시청
        assertEquals("양양군", nameAt(38.0754, 128.6190)) // 양양군청
        assertEquals("강릉시", nameAt(37.7519, 128.8761)) // 강릉시청
        assertEquals("제주시", nameAt(33.4996, 126.5312)) // 제주시청
    }

    @Test
    fun `바다 위는 어느 지역도 아니다`() {
        assertNull(index.find(36.0, 131.5)) // 동해 한가운데
        assertNull(index.find(35.0, 125.0)) // 서해 한가운데
    }
}
