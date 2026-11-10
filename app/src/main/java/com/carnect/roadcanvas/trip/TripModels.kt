package com.carnect.roadcanvas.trip

import com.carnect.roadcanvas.region.Level

/** 구역 하나의 방문 기록. [firstMs] 순서로 지도 색(무지개)을 정한다. */
data class VisitRecord(
    val state: VisitState,
    val firstMs: Long,
    /** 처음 방문한 여행. */
    val tripId: Long,
    /** 이 구역을 지난 모든 여행(같은 여행에서 여러 번 드나들어도 한 번). */
    val tripIds: List<Long> = listOf(tripId),
) {
    /** 다녀온 횟수(여행 수 기준). */
    val count: Int get() = tripIds.size
}

/** '여행 시작'부터 '여행 마치기'까지 하나의 여행. id는 시작 시각(ms). */
data class Trip(val id: Long, val endMs: Long? = null, val distanceM: Double = 0.0) {
    val startMs: Long get() = id
    val active: Boolean get() = endMs == null
}

enum class EventKind { START, DAY, FIRST_VISIT, STAY, CONFIRM, STICKER, END }

/**
 * 받은 스티커. 지역 스티커는 "region:<시·군·구 코드>", 장소 스티커는 "place:<장소 id>".
 */
data class Sticker(val id: String, val title: String, val timeMs: Long, val tripId: Long) {
    val isPlace: Boolean get() = id.startsWith("place:")
}

/** 여행 타임라인의 한 줄. [level]이 null이면 단계와 상관없는 줄(출발·종료). */
data class TimelineEvent(
    val timeMs: Long,
    val tripId: Long,
    val kind: EventKind,
    val title: String,
    val detail: String,
    val level: Level? = null,
)

/** 기록 제외 구역. 중심에서 [radiusM] 안의 위치는 기록하지 않는다. */
data class ExcludedZone(val name: String, val lat: Double, val lon: Double, val radiusM: Double = 500.0) {
    fun contains(p: TrackPoint) = TripTracker.distanceM(TrackPoint(0, lat, lon), p) <= radiusM
}
