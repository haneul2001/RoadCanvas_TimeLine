package com.carnect.roadcanvas.trip

import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import com.carnect.roadcanvas.BuildConfig
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import com.carnect.roadcanvas.location.LocationSource
import com.carnect.roadcanvas.region.Region
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.voice.TtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class TripState(
    val recording: Boolean = false,
    val visits: Map<String, VisitRecord> = emptyMap(),
    /** 진행 중인 여행, 없으면 가장 최근 여행. */
    val trip: Trip? = null,
    val track: List<TrackPoint> = emptyList(),
    val events: List<TimelineEvent> = emptyList(),
    val currentRegion: Region? = null,
) {
    /** 이 여행에서 처음 방문한 시·군·구 수. */
    val newRegionsInTrip: Int get() = trip?.let { t -> visits.values.count { it.tripId == t.id } } ?: 0
}

/** 위치 수신 → 시·군·구 판정 → 여행 기록 저장 → 첫 방문 음성 안내. */
class TripRecorder(
    context: Context,
    private val index: RegionIndex,
    private val tts: TtsEngine,
) {
    private val store = TripStore(context)
    private val visits = store.loadVisits().toMutableMap()
    private val trips = store.loadTrips().toMutableList()
    private val tracker = TripTracker(
        index,
        visits.mapValues { it.value.state },
        // 시연(디버그 빌드)은 GPS 주입으로 시간을 압축하므로 체류 시간과 속도 상한을 풀어 둔다.
        stayMs = if (BuildConfig.DEBUG) 30_000L else 5 * 60_000L,
        maxSpeedMps = if (BuildConfig.DEBUG) 20_000.0 else 70.0,
    )
    private val source = LocationSource(context, ::onLocation)
    private var lastPoint: TrackPoint? = null

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<TripState> = _state.asStateFlow()

    private fun initialState(): TripState {
        val trip = trips.lastOrNull()
        return TripState(
            visits = visits.toMap(),
            trip = trip,
            track = trip?.let { store.loadTrack(it.id) }.orEmpty(),
            events = trip?.let { store.loadEvents(it.id) }.orEmpty(),
        )
    }

    fun start() {
        if (_state.value.recording) return
        val trip = Trip(System.currentTimeMillis())
        trips += trip
        store.saveTrips(trips)
        lastPoint = null
        Log.i(TAG, "trip start ${trip.id}")
        source.start()
        _state.update { it.copy(recording = true, trip = trip, track = emptyList(), events = emptyList()) }
    }

    fun stop() {
        if (!_state.value.recording) return
        source.stop()
        val trip = _state.value.trip?.copy(endMs = System.currentTimeMillis())
        if (trip != null) {
            replaceTrip(trip)
            val km = trip.distanceM / 1000
            addEvent(EventKind.END, "여행 종료 요약", "총 ${formatKm(km)} · 새 방문 ${_state.value.newRegionsInTrip}곳")
        }
        Log.i(TAG, "trip stop")
        _state.update { it.copy(recording = false, trip = trip) }
    }

    fun confirm(code: String) {
        val region = index.byCode(code) ?: return
        val record = visits[code] ?: return
        tracker.confirm(code)
        visits[code] = record.copy(state = VisitState.CONFIRMED)
        store.saveVisits(visits)
        addEvent(EventKind.CONFIRM, "${region.name} 방문 확정", "${region.province} ${region.name}")
        _state.update { it.copy(visits = visits.toMap()) }
    }

    fun clear() {
        store.clear()
        visits.clear()
        trips.clear()
        _state.update { TripState(recording = it.recording) }
    }

    private fun onLocation(data: LocationData) {
        val trip = _state.value.trip?.takeIf { it.active } ?: return
        val time = if (data.timestampMillis > 0) data.timestampMillis else System.currentTimeMillis()
        val point = TrackPoint(time, data.latitude, data.longitude)
        val (used, events) = tracker.feed(point)
        if (!used) {
            Log.d(TAG, "location skipped ${data.latitude},${data.longitude}")
            return
        }
        val prev = lastPoint
        lastPoint = point
        store.appendPoint(point, trip.id)
        val region = tracker.currentRegion

        if (prev == null) {
            addEvent(EventKind.START, "Day 1 출발", "${region?.let(::placeName) ?: "현재 위치"}에서 출발")
        } else if (dayOf(point.timeMs) != dayOf(prev.timeMs)) {
            val day = ChronoUnit.DAYS.between(dayOf(trip.startMs), dayOf(point.timeMs)) + 1
            addEvent(EventKind.DAY, "Day $day 출발", "${region?.let(::placeName) ?: "현재 위치"}에서 출발")
        }

        events.forEach { event ->
            when (event) {
                is TripEvent.FirstPass -> {
                    val r = event.region
                    visits[r.code] = VisitRecord(VisitState.PASSED, point.timeMs, trip.id)
                    addEvent(EventKind.FIRST_VISIT, "${r.name} 첫 방문", "${r.province} ${r.name} · 새 방문 지역")
                    // 주행 중 안내는 지역당 한 번, 한 문장.
                    tts.speak("${r.name} 첫 방문이에요. 지도에 색이 칠해졌어요.", flush = false)
                }
                is TripEvent.StayCandidate -> {
                    val r = event.region
                    visits[r.code]?.let { visits[r.code] = it.copy(state = VisitState.STAY_CANDIDATE) }
                    addEvent(EventKind.STAY, "${r.name} 머묾", "${r.province} ${r.name} · 방문 확인 대기")
                }
            }
        }
        if (events.isNotEmpty()) store.saveVisits(visits)

        val distance = trip.distanceM + (prev?.let { TripTracker.distanceM(it, point) } ?: 0.0)
        val updated = trip.copy(distanceM = distance)
        replaceTrip(updated)
        _state.update {
            it.copy(visits = visits.toMap(), trip = updated, track = it.track + point, currentRegion = region)
        }
    }

    private fun addEvent(kind: EventKind, title: String, detail: String) {
        val trip = _state.value.trip ?: return
        val event = TimelineEvent(System.currentTimeMillis(), trip.id, kind, title, detail)
        store.appendEvent(event)
        Log.i(TAG, "timeline $kind $title")
        _state.update { it.copy(events = it.events + event) }
    }

    private fun replaceTrip(trip: Trip) {
        val i = trips.indexOfFirst { it.id == trip.id }
        if (i >= 0) trips[i] = trip else trips += trip
        // 거리는 자주 바뀌므로 저장은 종료 시와 이벤트가 생길 때로 충분하지만, 앱이 꺼져도 남도록 매번 저장한다.
        store.saveTrips(trips)
    }

    private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    companion object {
        fun formatKm(km: Double) = if (km >= 10) "${km.toInt()} km" else "%.1f km".format(km)

        /** 지도 핀·타임라인용 짧은 지명. 특별·광역시는 시 이름, 나머지는 시·군·구 이름에서 접미사를 뺀다. */
        fun placeName(region: Region): String {
            val metro = mapOf(
                "11" to "서울", "21" to "부산", "22" to "대구", "23" to "인천",
                "24" to "광주", "25" to "대전", "26" to "울산", "29" to "세종",
            )
            metro[region.provinceCode]?.let { return it }
            val n = region.name
            return if (n.length > 2 && (n.endsWith("시") || n.endsWith("군") || n.endsWith("구"))) n.dropLast(1) else n
        }
    }
}
