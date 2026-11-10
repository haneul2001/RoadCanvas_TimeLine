package com.carnect.roadcanvas.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoadBookTest {

    private val book = File("src/main/assets/city_roads.json").inputStream().use(RoadBook::load)

    @Test
    fun `부산에서 강릉은 실제 도로를 따라간다`() {
        val busan = 35.1851 to 129.0703
        val gangneung = 37.6940 to 128.8198
        val (path, onRoad) = book.route(busan, gangneung)
        assertTrue(onRoad)
        assertEquals(gangneung, path.last())
        // 직선(약 280km)보다 길고, 바다로 빠지지 않게 동쪽 끝 경도를 넘지 않는다
        val length = path.zipWithNext { a, b -> RoadBook.km(a, b) }.sum()
        assertTrue(length > 290)
        assertTrue(path.all { it.second < 129.6 })
    }

    @Test
    fun `반대 방향도 같은 도로를 뒤집어 쓴다`() {
        val (path, onRoad) = book.route(37.7519 to 128.8761, 35.1798 to 129.0750)
        assertTrue(onRoad)
        assertTrue(RoadBook.km(path.first(), 37.7519 to 128.8761) < 5)
    }

    @Test
    fun `도시에서 먼 곳은 직선으로 간다`() {
        val (_, onRoad) = book.route(36.9 to 126.2, 35.1798 to 129.0750)
        assertFalse(onRoad)
    }
}
