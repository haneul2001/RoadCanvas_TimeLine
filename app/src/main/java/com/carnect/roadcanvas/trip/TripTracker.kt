package com.carnect.roadcanvas.trip

import com.carnect.roadcanvas.region.Region
import com.carnect.roadcanvas.region.RegionIndex
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 방문 상태. 통과만으로는 방문으로 치지 않고, 머문 뒤 사용자가 확인해야 확정된다. */
enum class VisitState { PASSED, STAY_CANDIDATE, CONFIRMED }

/** 위치 점. [newSegment]는 여러 여행을 이어 볼 때 앞 점과 선을 잇지 않는 첫 점(저장하지 않음). */
data class TrackPoint(val timeMs: Long, val lat: Double, val lon: Double, val newSegment: Boolean = false)

sealed interface TripEvent {
    /** 처음 지나간 시·군·구. */
    data class FirstPass(val region: Region) : TripEvent
    /** 한곳에 일정 시간 머물러 방문 확인을 물어볼 수 있는 상태. */
    data class StayCandidate(val region: Region) : TripEvent
}

/**
 * 위치 좌표열을 받아 시·군·구 방문 상태를 판정한다.
 *
 * - 정확도가 낮거나 바로 앞 점과 비현실적으로 먼 점(순간 이동)은 판정에 쓰지 않는다.
 * - 체류: [stayRadiusM] 안에서 [stayMs] 이상 머문 경우.
 */
class TripTracker(
    private val index: RegionIndex,
    initialStates: Map<String, VisitState> = emptyMap(),
    var stayMs: Long = 5 * 60_000L,
    private val stayRadiusM: Double = 300.0,
    var maxSpeedMps: Double = 70.0,
) {
    private val states = initialStates.toMutableMap()
    val visitStates: Map<String, VisitState> get() = states

    var currentRegion: Region? = null
        private set

    private var last: TrackPoint? = null
    private var anchor: TrackPoint? = null

    /** 판정에 쓸 수 있는 점이면 true와 함께 생긴 이벤트를 돌려준다. */
    fun feed(point: TrackPoint): Pair<Boolean, List<TripEvent>> {
        val prev = last
        if (prev != null) {
            val dt = (point.timeMs - prev.timeMs) / 1000.0
            if (dt <= 0) return false to emptyList()
            if (distanceM(prev, point) / dt > maxSpeedMps) return false to emptyList()
        }
        last = point

        val events = mutableListOf<TripEvent>()
        val region = index.find(point.lat, point.lon)
        currentRegion = region
        if (region != null && region.code !in states) {
            states[region.code] = VisitState.PASSED
            events += TripEvent.FirstPass(region)
        }

        val a = anchor
        if (a == null || distanceM(a, point) > stayRadiusM) {
            anchor = point
        } else if (region != null && point.timeMs - a.timeMs >= stayMs && states[region.code] == VisitState.PASSED) {
            states[region.code] = VisitState.STAY_CANDIDATE
            events += TripEvent.StayCandidate(region)
        }
        return true to events
    }

    /** 새 여행을 시작할 때: 이전 여행의 마지막 점과 비교하지 않는다. */
    fun resetPosition() {
        last = null
        anchor = null
        currentRegion = null
    }

    /** 사용자가 방문을 확인했을 때. */
    fun confirm(code: String) {
        states[code] = VisitState.CONFIRMED
    }

    companion object {
        fun distanceM(a: TrackPoint, b: TrackPoint): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLon = Math.toRadians(b.lon - a.lon)
            val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(h))
        }
    }
}
