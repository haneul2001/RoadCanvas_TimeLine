package com.carnect.roadcanvas.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTextTest {

    @Test
    fun `기획서 시나리오처럼 거리·새 지역·스티커·시도 진행률을 말한다`() {
        assertEquals(
            "이번 여행은 587킬로미터를 달렸어요. 새로 방문한 시·군·구 6곳, 스티커 4개를 모았어요. 강원특별자치도는 이제 18곳 중 11곳을 칠했어요.",
            TripRecorder.summaryText(587.4, 6, 4, Triple("강원특별자치도", 11, 18)),
        )
    }

    @Test
    fun `새로 칠한 곳이 없으면 그렇게 말한다`() {
        assertEquals("이번 여행은 3.2킬로미터를 달렸어요. 새로 칠한 곳은 없어요.", TripRecorder.summaryText(3.21, 0, 0, null))
    }
}
