package com.carnect.roadcanvas.trip

import com.carnect.roadcanvas.region.Level

/** 구역 하나의 방문 기록. [firstMs] 순서로 지도 색(무지개)을 정한다. */
data class VisitRecord(val state: VisitState, val firstMs: Long, val tripId: Long)

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
