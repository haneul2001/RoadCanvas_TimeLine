package com.carnect.roadcanvas

import com.carnect.roadcanvas.ui.home.breakLabels
import com.carnect.roadcanvas.ui.home.countBreaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorBreaksTest {

    @Test
    fun `적게 다녔으면 1부터 5회 이상까지 그대로`() {
        assertEquals(listOf(1, 2, 3, 4, 5), countBreaks(listOf(1, 1, 2, 3)))
        assertEquals(listOf("1", "2", "3", "4", "5+"), breakLabels(listOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun `많이 다녔으면 분포에 맞춰 다섯 구간으로 나눈다`() {
        val counts = List(40) { 1 } + List(30) { 2 } + List(20) { 8 } + List(15) { 16 } + List(10) { 24 } + List(5) { 37 }
        val b = countBreaks(counts)
        assertEquals(1, b.first())
        assertTrue(b.size in 3..5)
        assertTrue(b.zipWithNext().all { (x, y) -> x < y })
    }
}
