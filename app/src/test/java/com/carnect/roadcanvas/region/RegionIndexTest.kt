package com.carnect.roadcanvas.region

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class RegionIndexTest {

    companion object {
        lateinit var sido: RegionIndex
        lateinit var sgg: RegionIndex
        lateinit var dong: RegionIndex

        fun load(level: Level) = File("src/main/assets/${level.asset}").inputStream().use { RegionIndex.load(level, it) }

        @BeforeClass
        @JvmStatic
        fun loadAll() {
            sido = load(Level.SIDO)
            sgg = load(Level.SGG)
            dong = load(Level.DONG)
        }
    }

    @Test
    fun `세 단계 데이터를 읽는다`() {
        assertEquals(16, sido.regions.size)
        assertEquals(256, sgg.regions.size)
        assertEquals(3559, dong.regions.size)
        assertEquals(18, sgg.countByProvince()["51"]) // 강원특별자치도
    }

    @Test
    fun `대표 지점이 세 단계 모두 맞게 판정된다`() {
        // 광화문
        assertEquals("서울특별시", sido.find(37.5759, 126.9768)?.name)
        assertEquals("종로구", sgg.find(37.5759, 126.9768)?.name)
        // 읍·면·동은 경계 가까운 점이 많아 확실히 한가운데인 곳으로 본다(인천공항 → 운서1동).
        assertEquals("운서1동", dong.find(37.4602, 126.4407)?.name)
        // 강릉시청
        assertEquals("강원특별자치도", sido.find(37.7519, 128.8761)?.name)
        assertEquals("강릉시", sgg.find(37.7519, 128.8761)?.name)
        // 홍천·속초·제주
        assertEquals("홍천군", sgg.find(37.6970, 127.8888)?.name)
        assertEquals("속초시", sgg.find(38.2070, 128.5918)?.name)
        assertEquals("제주시", sgg.find(33.4996, 126.5312)?.name)
    }

    @Test
    fun `하위 단계는 상위 단계 코드를 부모로 가진다`() {
        val d = dong.find(37.5759, 126.9768)!!
        val s = sgg.find(37.5759, 126.9768)!!
        assertEquals(s.code, d.parent)
        assertEquals(sido.find(37.5759, 126.9768)!!.code, s.parent)
    }

    @Test
    fun `바다 위는 어느 지역도 아니다`() {
        assertNull(sgg.find(36.0, 131.5)) // 동해 한가운데
        assertNull(dong.find(35.0, 125.0)) // 서해 한가운데
    }

    @Test
    fun `해변처럼 경계 바로 바깥 점은 가까운 구역으로 본다`() {
        val sgg = File("src/main/assets/${Level.SGG.asset}").inputStream().use { RegionIndex.load(Level.SGG, it) }
        // 경포해변(바닷가)
        org.junit.Assert.assertEquals("강릉시", sgg.find(37.8055, 128.9087)?.name)
        // 바다 한가운데는 여전히 없음
        org.junit.Assert.assertNull(sgg.find(37.8, 129.6))
    }
}
