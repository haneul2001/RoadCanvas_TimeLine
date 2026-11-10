package com.carnect.roadcanvas.ui.home

import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.EventKind
import com.carnect.roadcanvas.trip.TrackPoint
import com.carnect.roadcanvas.trip.TripState
import com.carnect.roadcanvas.trip.TripTracker

/**
 * 타임랩스(Route Replay) 계산. 여행의 기록을 [timeMs] 시점까지만 남긴 [TripState]를 만든다.
 * 이번 여행 이전에 다녀온 곳은 처음부터 칠해 두고, 이번 여행에서 처음 간 곳은 그 시각이 되어야 칠한다.
 */
class Replay(private val trip: TripState, private val indexes: Map<Level, RegionIndex>) {

    private val track = trip.track
    val startMs: Long = track.firstOrNull()?.timeMs ?: 0L
    val endMs: Long = track.lastOrNull()?.timeMs ?: 0L
    val playable: Boolean get() = track.size >= 2 && endMs > startMs

    /** 각 점까지 누적 거리(m). */
    private val distances: DoubleArray = DoubleArray(track.size).also { d ->
        for (i in 1 until track.size) d[i] = d[i - 1] + TripTracker.distanceM(track[i - 1], track[i])
    }

    fun timeAt(progress: Float): Long = startMs + ((endMs - startMs) * progress.coerceIn(0f, 1f)).toLong()

    /** [timeMs]까지 지난 점의 수. */
    private fun countUntil(timeMs: Long): Int {
        var lo = 0
        var hi = track.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (track[mid].timeMs <= timeMs) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun distanceAt(timeMs: Long): Double = countUntil(timeMs).let { if (it == 0) 0.0 else distances[it - 1] }

    fun frameAt(timeMs: Long): TripState {
        val tripId = trip.trip?.id
        val points: List<TrackPoint> = track.subList(0, countUntil(timeMs))
        val last = points.lastOrNull()
        val current = last?.let { p -> indexes.mapNotNull { (level, idx) -> idx.find(p.lat, p.lon)?.let { level to it } }.toMap() }.orEmpty()
        return trip.copy(
            trip = trip.trip?.copy(distanceM = distanceAt(timeMs)),
            visits = trip.visits.mapValues { (_, byCode) ->
                byCode.filterValues { it.tripId != tripId || it.firstMs <= timeMs }
            },
            track = points,
            // 출발 줄은 첫 점과 같은 시각이라 늘 보이게, 종료 요약은 재생이 끝난 뒤에만.
            events = trip.events.filter { it.kind == EventKind.START || (it.kind != EventKind.END && it.timeMs <= timeMs) },
            current = current,
        )
    }
}
