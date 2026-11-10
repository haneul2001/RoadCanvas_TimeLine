package com.carnect.roadcanvas.trip

import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import com.carnect.roadcanvas.BuildConfig
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import com.carnect.roadcanvas.location.LocationSource
import com.carnect.roadcanvas.region.Level
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
    /** 단계별 방문 기록. */
    val visits: Map<Level, Map<String, VisitRecord>> = emptyMap(),
    /** 진행 중인 여행, 없으면 가장 최근 여행. */
    val trip: Trip? = null,
    val track: List<TrackPoint> = emptyList(),
    val events: List<TimelineEvent> = emptyList(),
    /** 단계별 현재 구역. */
    val current: Map<Level, Region> = emptyMap(),
) {
    fun visitsAt(level: Level): Map<String, VisitRecord> = visits[level].orEmpty()

    /** 이 여행에서 처음 방문한 구역 수. */
    fun newInTrip(level: Level): Int = trip?.let { t -> visitsAt(level).values.count { it.tripId == t.id } } ?: 0

    /** 단계와 상관없는 줄(출발·종료)과 해당 단계의 줄만. */
    fun eventsAt(level: Level): List<TimelineEvent> = events.filter { it.level == null || it.level == level }
}

/**
 * 위치 수신 → 세 단계(시·도/시·군·구/읍·면·동) 구역 판정 → 여행 기록 저장 → 음성 안내.
 * 단계는 화면에서 보는 기준만 바꾸고, 판정은 늘 세 단계를 함께 한다.
 */
class TripRecorder(
    context: Context,
    private val indexes: Map<Level, RegionIndex>,
    private val tts: TtsEngine,
) {
    private val store = TripStore(context)
    private val visits = Level.entries.associateWith { store.loadVisits(it).toMutableMap() }
    private val trips = store.loadTrips().toMutableList()
    private val trackers = Level.entries.associateWith { level ->
        TripTracker(
            indexes.getValue(level),
            visits.getValue(level).mapValues { it.value.state },
            // 시연(디버그 빌드)은 GPS 주입으로 시간을 압축하므로 체류 시간과 속도 상한을 풀어 둔다.
            stayMs = if (BuildConfig.DEBUG) 30_000L else 5 * 60_000L,
            maxSpeedMps = if (BuildConfig.DEBUG) 20_000.0 else 70.0,
        )
    }
    private val source = LocationSource(context, ::onLocation)
    private var lastPoint: TrackPoint? = null

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<TripState> = _state.asStateFlow()

    private fun initialState(): TripState {
        var trip = trips.lastOrNull()
        // 앱이 기록 중에 강제로 종료됐다면, 마지막으로 기록된 시각에 끝난 여행으로 정리한다.
        if (trip != null && trip.active) {
            val lastTime = store.loadTrack(trip.id).lastOrNull()?.timeMs ?: trip.startMs
            trip = trip.copy(endMs = lastTime)
            trips[trips.lastIndex] = trip
            store.saveTrips(trips)
            Log.i(TAG, "closed unfinished trip ${trip.id}")
        }
        val track = trip?.let { store.loadTrack(it.id) }.orEmpty()
        // 앱을 다시 켜도 마지막 위치 기준으로 진행률을 보여 준다.
        val current = track.lastOrNull()?.let { p ->
            indexes.mapNotNull { (level, index) -> index.find(p.lat, p.lon)?.let { level to it } }.toMap()
        }.orEmpty()
        return TripState(
            visits = snapshotVisits(),
            trip = trip,
            track = track,
            events = trip?.let { store.loadEvents(it.id) }.orEmpty(),
            current = current,
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
            val s = _state.value
            addEvent(
                EventKind.END, "여행 종료 요약",
                "총 ${formatKm(trip.distanceM / 1000)} · 새 시·군·구 ${s.newInTrip(Level.SGG)}곳 · 읍·면·동 ${s.newInTrip(Level.DONG)}곳",
            )
        }
        Log.i(TAG, "trip stop")
        _state.update { it.copy(recording = false, trip = trip) }
    }

    fun confirm(level: Level, code: String) {
        val region = indexes.getValue(level).byCode(code) ?: return
        val record = visits.getValue(level)[code] ?: return
        trackers.getValue(level).confirm(code)
        visits.getValue(level)[code] = record.copy(state = VisitState.CONFIRMED)
        store.saveVisits(level, visits.getValue(level))
        addEvent(EventKind.CONFIRM, "${region.name} 방문 확정", region.toString(), level)
        _state.update { it.copy(visits = snapshotVisits()) }
    }

    fun clear() {
        store.clear()
        visits.values.forEach { it.clear() }
        trips.clear()
        _state.update { TripState(recording = it.recording) }
    }

    private fun onLocation(data: LocationData) {
        val trip = _state.value.trip?.takeIf { it.active } ?: return
        val time = if (data.timestampMillis > 0) data.timestampMillis else System.currentTimeMillis()
        val point = TrackPoint(time, data.latitude, data.longitude)

        // 세 단계 모두 같은 기준으로 점을 거르므로 시·군·구 결과로 사용 여부를 정한다.
        val results = Level.entries.associateWith { trackers.getValue(it).feed(point) }
        if (!results.getValue(Level.SGG).first) {
            Log.d(TAG, "location skipped ${data.latitude},${data.longitude}")
            return
        }
        val prev = lastPoint
        lastPoint = point
        store.appendPoint(point, trip.id)
        val current = Level.entries.mapNotNull { l -> trackers.getValue(l).currentRegion?.let { l to it } }.toMap()
        val here = current[Level.SGG]

        if (prev == null) {
            addEvent(EventKind.START, "Day 1 출발", "${here?.let(::placeName) ?: "현재 위치"}에서 출발")
        } else if (dayOf(point.timeMs) != dayOf(prev.timeMs)) {
            val day = ChronoUnit.DAYS.between(dayOf(trip.startMs), dayOf(point.timeMs)) + 1
            addEvent(EventKind.DAY, "Day $day 출발", "${here?.let(::placeName) ?: "현재 위치"}에서 출발")
        }

        results.forEach { (level, result) ->
            val levelVisits = visits.getValue(level)
            result.second.forEach { event ->
                when (event) {
                    is TripEvent.FirstPass -> {
                        val r = event.region
                        levelVisits[r.code] = VisitRecord(VisitState.PASSED, point.timeMs, trip.id)
                        addEvent(EventKind.FIRST_VISIT, "${r.name} 첫 방문", "${r.province}${if (r.name != r.province) " " + r.name else ""} · 새 ${level.label}", level)
                        announce(level, r)
                    }
                    is TripEvent.StayCandidate -> {
                        val r = event.region
                        levelVisits[r.code]?.let { levelVisits[r.code] = it.copy(state = VisitState.STAY_CANDIDATE) }
                        addEvent(EventKind.STAY, "${r.name} 머묾", "${r.province}${if (r.name != r.province) " " + r.name else ""} · 방문 확인 대기", level)
                    }
                }
            }
            if (result.second.isNotEmpty()) store.saveVisits(level, levelVisits)
        }

        val distance = trip.distanceM + (prev?.let { TripTracker.distanceM(it, point) } ?: 0.0)
        val updated = trip.copy(distanceM = distance)
        replaceTrip(updated)
        _state.update { it.copy(visits = snapshotVisits(), trip = updated, track = it.track + point, current = current) }
    }

    /** 주행 중 안내는 짧게: 시·도에 처음 들어올 때와 시·군·구 첫 방문만 말한다(읍·면·동은 너무 잦다). */
    private fun announce(level: Level, r: Region) {
        when (level) {
            Level.SIDO -> tts.speak("${r.name}에 처음 왔어요.", flush = false)
            Level.SGG -> tts.speak("${r.name} 첫 방문이에요. 지도에 색이 칠해졌어요.", flush = false)
            Level.DONG -> Unit
        }
    }

    private fun addEvent(kind: EventKind, title: String, detail: String, level: Level? = null) {
        val trip = _state.value.trip ?: return
        val event = TimelineEvent(System.currentTimeMillis(), trip.id, kind, title, detail, level)
        store.appendEvent(event)
        if (level != Level.DONG) Log.i(TAG, "timeline $kind ${level?.name ?: ""} $title")
        _state.update { it.copy(events = it.events + event) }
    }

    private fun snapshotVisits() = visits.mapValues { it.value.toMap() }

    private fun replaceTrip(trip: Trip) {
        val i = trips.indexOfFirst { it.id == trip.id }
        if (i >= 0) trips[i] = trip else trips += trip
        // 앱이 꺼져도 거리가 남도록 매번 저장한다.
        store.saveTrips(trips)
    }

    private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    companion object {
        fun formatKm(km: Double) = if (km >= 10) "${km.toInt()} km" else "%.1f km".format(km)

        /** 지도 핀·타임라인용 짧은 지명. 특별·광역시는 시 이름, 나머지는 구역 이름에서 접미사를 뺀다. */
        fun placeName(region: Region): String {
            val p = region.province
            if (p.endsWith("특별시") || p.endsWith("광역시") || p.endsWith("특별자치시")) {
                return p.removeSuffix("특별자치시").removeSuffix("특별시").removeSuffix("광역시")
            }
            return shortName(region.name)
        }

        fun shortName(n: String) =
            if (n.length > 2 && (n.endsWith("시") || n.endsWith("군") || n.endsWith("구"))) n.dropLast(1) else n
    }
}
