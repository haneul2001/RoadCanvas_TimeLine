package com.carnect.roadcanvas.sim

import android.util.Log
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.trip.VisitState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 시연용 샘플 여행. 지난 몇 주 동안 다녀온 것처럼 여행 여러 개를 실제 기록과 같은 경로(위치 → 판정 → 저장)로 만든다.
 * 도로 모양은 [RoadBook]의 도시 간 도로, 시각은 시속 80km로 계산하고, 목적지에서는 머문 것으로 둔 뒤 일부는 방문을 확정한다.
 * 음성은 끄고 한꺼번에 만든다.
 */
class DemoData(private val recorder: TripRecorder, private val roads: RoadBook) {

    /** 여행 하나: 며칠 전 몇 시에 출발해 [cities] 순서로 들르고, [confirm]에 든 도시는 방문 확정. */
    private data class SampleTrip(val daysAgo: Int, val hour: Int, val cities: List<String>, val confirm: Set<String>)

    private val samples = listOf(
        SampleTrip(41, 9, listOf("서울", "춘천"), setOf("춘천")),
        SampleTrip(27, 8, listOf("서울", "대전", "전주"), setOf("전주")),
        SampleTrip(26, 10, listOf("전주", "광주", "여수"), setOf("여수")),
        SampleTrip(12, 9, listOf("대구", "경주", "부산"), setOf("경주", "부산")),
        SampleTrip(4, 8, listOf("서울", "홍천", "인제", "속초"), setOf("속초")),
        SampleTrip(3, 10, listOf("속초", "양양", "강릉"), setOf("강릉")),
    )

    val tripCount: Int get() = samples.size

    /** 샘플 여행을 만든다. [onProgress]는 (만든 수, 전체 수). 기록 중이면 만들지 않는다. */
    suspend fun generate(onProgress: (Int, Int) -> Unit): Boolean = withContext(Dispatchers.Default) {
        if (recorder.state.value.recording) return@withContext false
        recorder.quiet = true
        recorder.simulating = true
        try {
            val now = System.currentTimeMillis()
            samples.sortedByDescending { it.daysAgo }.forEachIndexed { n, s ->
                val day = java.time.LocalDate.now().minusDays(s.daysAgo.toLong())
                val start = day.atTime(s.hour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    .coerceAtMost(now - 60_000)
                drive(start, s)
                withContext(Dispatchers.Main) { onProgress(n + 1, samples.size) }
            }
            true
        } finally {
            recorder.simulating = false
            recorder.quiet = false
        }
    }

    /**
     * 시험용: [year]년 1월 첫 토요일부터 오늘까지 매주 토요일 여행 하나씩(도시 두세 곳, 실제 도로).
     * 월별·연도별 타임랩스와 히스토리를 시험하려는 것이라 도시는 주마다 정해진 무작위로 고른다.
     */
    suspend fun generateWeekly(year: Int, onProgress: (Int, Int) -> Unit): Int = withContext(Dispatchers.Default) {
        if (recorder.state.value.recording) return@withContext 0
        val cities = roads.cityNames
        val today = java.time.LocalDate.now()
        var day = java.time.LocalDate.of(year, 1, 1)
        while (day.dayOfWeek != java.time.DayOfWeek.SATURDAY) day = day.plusDays(1)
        val weeks = generateSequence(day) { it.plusWeeks(1) }.takeWhile { !it.isAfter(today) && it.year == year }.toList()
        recorder.quiet = true
        recorder.simulating = true
        try {
            weeks.forEachIndexed { n, d ->
                val rnd = java.util.Random(d.toEpochDay())
                val a = cities[rnd.nextInt(cities.size)]
                val stops = mutableListOf(a)
                repeat(1 + rnd.nextInt(2)) {
                    val near = cities.filter { it !in stops && roads.cityRoute(stops.last(), it) != null }
                    if (near.isNotEmpty()) stops += near[rnd.nextInt(near.size)]
                }
                val start = d.atTime(8 + rnd.nextInt(3), 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                drive(start, SampleTrip(0, 0, stops, setOf(stops.last())))
                withContext(Dispatchers.Main) { onProgress(n + 1, weeks.size) }
            }
            weeks.size
        } finally {
            recorder.simulating = false
            recorder.quiet = false
        }
    }

    private fun drive(startMs: Long, s: SampleTrip) {
        recorder.start(startMs, withLocation = false)
        var t = startMs
        var last: LatLon? = null
        s.cities.zipWithNext().forEach { (a, b) ->
            val path = roads.cityRoute(a, b) ?: return@forEach
            path.forEach { p ->
                last?.let { t += (RoadBook.km(it, p) / SPEED_KMH * 3_600_000).toLong().coerceAtLeast(1_000) }
                recorder.feed(p.first, p.second, t)
                last = p
            }
            // 방문을 확정할 도시면 근처(15km 안) 대표 명소까지 가서 머문다 → 장소 스티커
            if (b in s.confirm) {
                val from = last ?: return@forEach
                com.carnect.roadcanvas.data.Landmarks.all
                    .map { it to RoadBook.km(from, it.lat to it.lon) }
                    .filter { it.second <= LANDMARK_KM }
                    .minByOrNull { it.second }
                    ?.first?.let { lm ->
                        RoadBook.straight(from, lm.lat to lm.lon).forEach { p ->
                            t += (RoadBook.km(last!!, p) / CITY_KMH * 3_600_000).toLong().coerceAtLeast(1_000)
                            recorder.feed(p.first, p.second, t)
                            last = p
                        }
                    }
            }
            // 들른 곳에서 잠시 머문다(머문 곳 판정: 반경 300m 안에서 5분 이상)
            val here = last ?: return@forEach
            repeat(STAY_MIN) {
                t += 60_000
                recorder.feed(here.first + it * 0.00001, here.second, t)
            }
            if (b in s.confirm) {
                val code = recorder.state.value.current[Level.SGG]?.code
                val stay = code?.let { recorder.state.value.visitsAt(Level.SGG)[it] }
                if (stay?.state == VisitState.STAY_CANDIDATE) recorder.confirmStay(code, t)
            }
        }
        recorder.stop(t + 60_000)
        Log.i(TAG, "demo trip ${s.cities.joinToString("→")} done")
    }

    companion object {
        private const val SPEED_KMH = 80.0
        private const val STAY_MIN = 8
        private const val LANDMARK_KM = 15.0
        private const val CITY_KMH = 40.0
    }
}
