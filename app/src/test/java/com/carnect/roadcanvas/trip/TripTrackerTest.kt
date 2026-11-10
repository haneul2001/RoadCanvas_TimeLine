package com.carnect.roadcanvas.trip

import com.carnect.roadcanvas.region.RegionIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class TripTrackerTest {

    companion object {
        lateinit var index: RegionIndex

        @BeforeClass
        @JvmStatic
        fun load() {
            index = File("src/main/assets/regions.json").inputStream().use(RegionIndex::load)
        }
    }

    /** a에서 b까지 [steps]개 점을 [intervalMs] 간격으로 만든다. */
    private fun line(startMs: Long, a: Pair<Double, Double>, b: Pair<Double, Double>, steps: Int, intervalMs: Long) =
        (0..steps).map { i ->
            val t = i.toDouble() / steps
            TrackPoint(startMs + i * intervalMs, a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t)
        }

    @Test
    fun `지나간 시군구는 처음 한 번만 통과로 기록된다`() {
        val tracker = TripTracker(index)
        // 홍천군청 → 인제군청, 약 50km를 30초 간격 100점(시속 60km 남짓)
        val points = line(0, 37.6970 to 127.8888, 38.0697 to 128.1704, 100, 30_000)
        val firsts = points.flatMap { tracker.feed(it).second }.filterIsInstance<TripEvent.FirstPass>().map { it.region.name }
        assertEquals("홍천군", firsts.first())
        assertEquals("인제군", firsts.last())
        assertEquals(firsts.size, firsts.toSet().size)
        assertTrue(tracker.visitStates.values.all { it == VisitState.PASSED })
    }

    @Test
    fun `한곳에 오래 머물면 체류 후보가 된다`() {
        val tracker = TripTracker(index, stayMs = 5 * 60_000L)
        val sokcho = 38.2070 to 128.5918
        val stay = (0..12).map { TrackPoint(it * 30_000L, sokcho.first + it * 0.00001, sokcho.second) }
        val events = stay.flatMap { tracker.feed(it).second }
        assertTrue(events.any { it is TripEvent.StayCandidate && it.region.name == "속초시" })
        assertEquals(VisitState.STAY_CANDIDATE, tracker.visitStates[tracker.currentRegion!!.code])
    }

    @Test
    fun `순간 이동한 점은 판정에 쓰지 않는다`() {
        val tracker = TripTracker(index)
        tracker.feed(TrackPoint(0, 37.5759, 126.9768)) // 서울
        val (used, events) = tracker.feed(TrackPoint(10_000, 33.4996, 126.5312)) // 10초 뒤 제주
        assertEquals(false, used)
        assertTrue(events.isEmpty())
        assertEquals("종로구", tracker.currentRegion?.name)
    }
}
