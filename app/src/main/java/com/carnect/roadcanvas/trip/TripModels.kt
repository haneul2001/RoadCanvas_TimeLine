package com.carnect.roadcanvas.trip

/** 시·군·구 하나의 방문 기록. [firstMs] 순서로 지도 색(무지개)을 정한다. */
data class VisitRecord(val state: VisitState, val firstMs: Long, val tripId: Long)

/** '여행 시작'부터 '멈추기'까지 하나의 여행. id는 시작 시각(ms). */
data class Trip(val id: Long, val endMs: Long? = null, val distanceM: Double = 0.0) {
    val startMs: Long get() = id
    val active: Boolean get() = endMs == null
}

enum class EventKind { START, DAY, FIRST_VISIT, STAY, CONFIRM, END }

/** 여행 타임라인의 한 줄. */
data class TimelineEvent(
    val timeMs: Long,
    val tripId: Long,
    val kind: EventKind,
    val title: String,
    val detail: String,
)
