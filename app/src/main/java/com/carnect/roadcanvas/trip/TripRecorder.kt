package com.carnect.roadcanvas.trip

import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import com.carnect.roadcanvas.BuildConfig
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import com.carnect.roadcanvas.data.Landmarks
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
    /** 지금까지 받은 스티커(모든 여행). */
    val stickers: List<Sticker> = emptyList(),
    /** 화면에 보여 줄 지금 위치. 기록 중이 아니어도 앱이 열려 있으면 받지만 저장하지 않는다. */
    val here: TrackPoint? = null,
) {
    fun visitsAt(level: Level): Map<String, VisitRecord> = visits[level].orEmpty()

    /** 머물렀지만 아직 방문 확인을 하지 않은 시·군·구 코드(먼저 머문 순). */
    val pendingStays: List<String>
        get() = visitsAt(Level.SGG).filterValues { it.state == VisitState.STAY_CANDIDATE }.entries.sortedBy { it.value.firstMs }.map { it.key }

    fun stickersInTrip(): Int = trip?.let { t -> stickers.count { it.tripId == t.id } } ?: 0

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
    private val stickers = store.loadStickers().toMutableList()
    private var trackers = buildTrackers()
    private val zoneList = store.loadZones().toMutableList()

    private val _trips = MutableStateFlow(trips.toList())
    /** 저장된 모든 여행(오래된 순). */
    val tripList: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _zones = MutableStateFlow(zoneList.toList())
    val zones: StateFlow<List<ExcludedZone>> = _zones.asStateFlow()

    private fun buildTrackers() = Level.entries.associateWith { level ->
        TripTracker(
            indexes.getValue(level),
            visits.getValue(level).mapValues { it.value.state },
        ).also { applyLimits(it) }
    }

    /** 지난 여행 하나를 그 여행이 끝난 시점 기준으로 다시 본다(타임랩스용). */
    fun loadTrip(tripId: Long): TripState? {
        val trip = trips.firstOrNull { it.id == tripId } ?: return null
        if (trip.id == _state.value.trip?.id) return _state.value
        return TripState(
            visits = snapshotVisits().mapValues { (_, byCode) -> byCode.filterValues { it.tripId <= tripId } },
            trip = trip,
            track = store.loadTrack(tripId),
            events = store.loadEvents(tripId),
            stickers = stickers.filter { it.tripId <= tripId },
        )
    }

    /**
     * 여러 여행(주간·월별·연도별)을 하나로 이어 본다. 여행 사이의 빈 시간은 빼서(1분 간격으로 붙여)
     * 타임랩스가 이동한 시간 위주로 흐르게 한다. 그 기간 이전에 칠한 곳은 처음부터 칠해 둔다.
     */
    fun loadPeriod(tripIds: List<Long>): TripState? {
        val list = trips.filter { it.id in tripIds }.sortedBy { it.id }
        if (list.isEmpty()) return null
        if (list.size == 1) return loadTrip(list.first().id)
        val shift = HashMap<Long, Long>()
        val track = mutableListOf<TrackPoint>()
        val events = mutableListOf<TimelineEvent>()
        var cursor = list.first().startMs
        list.forEach { t ->
            val pts = if (t.id == _state.value.trip?.id) _state.value.track else store.loadTrack(t.id)
            val start = pts.firstOrNull()?.timeMs ?: t.startMs
            val d = start - cursor
            shift[t.id] = d
            pts.forEachIndexed { i, p -> track += p.copy(timeMs = p.timeMs - d, newSegment = i == 0 && track.isNotEmpty()) }
            val evs = if (t.id == _state.value.trip?.id) _state.value.events else store.loadEvents(t.id)
            evs.forEach { e -> events += e.copy(timeMs = e.timeMs - d) }
            cursor = (track.lastOrNull()?.timeMs ?: cursor) + 60_000
        }
        val last = list.last().id
        val visits = snapshotVisits().mapValues { (_, byCode) ->
            byCode.filterValues { it.tripId <= last }.mapValues { (_, v) -> shift[v.tripId]?.let { v.copy(firstMs = v.firstMs - it) } ?: v }
        }
        val combined = Trip(list.first().id, endMs = track.lastOrNull()?.timeMs ?: list.last().endMs, distanceM = list.sumOf { it.distanceM })
        return TripState(
            visits = visits,
            trip = combined,
            track = track,
            events = events.sortedBy { it.timeMs },
            stickers = stickers.filter { it.tripId <= last },
        )
    }

    /** 여행 하나를 지운다. 그 여행에서 처음 칠한 곳과 받은 스티커도 함께 지운다. */
    fun deleteTrip(tripId: Long) {
        if (_state.value.recording && _state.value.trip?.id == tripId) return
        store.deleteTrip(tripId)
        trips.removeAll { it.id == tripId }
        store.saveTrips(trips)
        visits.forEach { (level, byCode) ->
            // 그 여행에서만 간 곳은 지우고, 다른 여행에서도 간 곳은 횟수만 줄인다.
            val touched = byCode.filterValues { tripId in it.tripIds || it.tripId == tripId }
            touched.forEach { (code, v) ->
                val rest = v.tripIds - tripId
                if (rest.isEmpty()) byCode.remove(code)
                else byCode[code] = v.copy(tripId = if (v.tripId == tripId) rest.first() else v.tripId, tripIds = rest)
            }
            if (touched.isNotEmpty()) store.saveVisits(level, byCode)
        }
        if (stickers.removeAll { it.tripId == tripId }) store.saveStickers(stickers)
        trackers = buildTrackers()
        _trips.value = trips.toList()
        Log.i(TAG, "trip deleted $tripId")
        if (_state.value.trip?.id == tripId) {
            _state.value = initialState().copy(recording = _state.value.recording)
        } else {
            _state.update { it.copy(visits = snapshotVisits(), stickers = stickers.toList()) }
        }
    }

    fun addZone(zone: ExcludedZone) {
        zoneList += zone
        store.saveZones(zoneList)
        _zones.value = zoneList.toList()
    }

    fun removeZone(zone: ExcludedZone) {
        zoneList.remove(zone)
        store.saveZones(zoneList)
        _zones.value = zoneList.toList()
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
            _trips.value = trips.toList()
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
            stickers = stickers.toList(),
        )
    }

    /** 음성 안내를 끈다(시연용 샘플 기록을 한꺼번에 만들 때). */
    @Volatile
    var quiet = false

    /** 추천 여행지(앱 시작 때 읽어 넣음). 경로가 근처를 지나간 여행지의 종류 스티커를 준다. */
    @Volatile
    var spots: com.carnect.roadcanvas.data.Spots = com.carnect.roadcanvas.data.Spots.EMPTY

    private val _newStickers = kotlinx.coroutines.flow.MutableSharedFlow<List<com.carnect.roadcanvas.sticker.StickerDef>>(extraBufferCapacity = 8)
    /** 새로 받은 도감 스티커(획득 팝업용). 샘플 기록을 만들 때(quiet)는 알리지 않는다. */
    val newStickers: kotlinx.coroutines.flow.SharedFlow<List<com.carnect.roadcanvas.sticker.StickerDef>> = _newStickers

    /** 도감 스티커를 준다(이미 있으면 무시). 타임라인에 남기고 음성·팝업으로 알린다. */
    fun award(keys: Collection<String>, tripId: Long? = _state.value.trip?.id, atMs: Long = eventTime ?: System.currentTimeMillis()) {
        val owned = stickers.map { it.id }.toSet()
        val defs = keys.distinct().mapNotNull { com.carnect.roadcanvas.sticker.StickerCatalog.byKey(it) }
            .filter { it.category.collectible && com.carnect.roadcanvas.sticker.StickerCatalog.PREFIX + it.key !in owned }
        if (defs.isEmpty()) return
        val trip = tripId ?: trips.lastOrNull()?.id ?: 0L
        defs.forEach { d ->
            stickers += Sticker(com.carnect.roadcanvas.sticker.StickerCatalog.PREFIX + d.key, d.label, atMs, trip)
            if (_state.value.trip != null) addEvent(EventKind.STICKER, "${d.label} 스티커 획득", "${d.category.label} 스티커")
        }
        store.saveStickers(stickers)
        _state.update { it.copy(stickers = stickers.toList()) }
        Log.i(TAG, "award ${defs.map { it.key }}")
        if (!quiet) _newStickers.tryEmit(defs)
        // 모은 개수로 받는 업적
        if (stickers.count { it.id.startsWith(com.carnect.roadcanvas.sticker.StickerCatalog.PREFIX) } >= 10) {
            if ("${com.carnect.roadcanvas.sticker.StickerCatalog.PREFIX}collector" !in stickers.map { it.id }) award(listOf("collector"), trip, atMs)
        }
    }

    /** 방문 확정·여행 종료 때 계절·업적 조건을 살핀다. */
    private fun seasonalAndAchievements(atMs: Long): List<String> {
        val keys = mutableListOf<String>()
        val d = Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()).toLocalDate()
        when (d.monthValue) {
            3, 4, 5 -> keys += "spring"
            6, 7, 8 -> keys += "summer"
            9, 10, 11 -> keys += "autumn"
            else -> keys += "winter"
        }
        if (d.monthValue in 10..11) keys += "silvergrass"
        if (d.monthValue == 12) keys += "santa"
        if (d.monthValue == 12 && d.dayOfMonth in 24..25) keys += "christmas"
        if (d.monthValue == 10 && d.dayOfMonth == 31) keys += "halloween"
        if (d.monthValue == 1 && d.dayOfMonth == 1) keys += "newyear"
        val sgg = visits.getValue(Level.SGG)
        if (sgg.values.count { it.state == VisitState.CONFIRMED } >= 1) keys += "first_visit"
        if (sgg.values.count { it.state == VisitState.CONFIRMED } >= 10) keys += "explorer"
        if (sgg.values.any { it.tripIds.size >= 2 }) keys += "revisit"
        val sido = visits.getValue(Level.SIDO)
        if (sido.size >= 5) keys += "local_explorer"
        if (sido.size >= indexes.getValue(Level.SIDO).regions.size) keys += "nation_complete"
        val totalKm = trips.sumOf { it.distanceM } / 1000
        if (totalKm >= 1000) keys += "space"
        if (levelOf(totalKm).first >= 3) keys += "level_up"
        // 사흘 연속 여행
        val days = trips.map { Instant.ofEpochMilli(it.startMs).atZone(ZoneId.systemDefault()).toLocalDate() }.toSortedSet()
        if (days.any { day -> days.contains(day.plusDays(1)) && days.contains(day.plusDays(2)) }) keys += "streak"
        return keys
    }

    private fun speak(text: String, flush: Boolean) {
        if (!quiet) tts.speak(text, flush)
    }

    /** [ms] 시각으로 타임라인 줄을 남기며 [block]을 실행한다(지난 기록을 만들 때). */
    private inline fun <T> at(ms: Long?, block: () -> T): T {
        val prev = eventTime
        if (ms != null) eventTime = ms
        try {
            return block()
        } finally {
            eventTime = prev
        }
    }

    /** 여행 시작. [atMs]는 시작 시각(기본은 지금, 시연용 샘플 기록은 지난 날짜). */
    fun start(atMs: Long = System.currentTimeMillis(), withLocation: Boolean = true) {
        if (_state.value.recording) return
        val trip = Trip(atMs)
        trips += trip
        store.saveTrips(trips)
        _trips.value = trips.toList()
        lastPoint = null
        trackers.values.forEach { it.resetPosition() }
        Log.i(TAG, "trip start ${trip.id}")
        if (withLocation) source.start()
        _state.update { it.copy(recording = true, trip = trip, track = emptyList(), events = emptyList()) }
    }

    fun stop(atMs: Long? = null) = at(atMs) { stopNow(atMs) }

    private fun stopNow(atMs: Long?) {
        if (!_state.value.recording) return
        if (!watching) source.stop()
        val trip = _state.value.trip?.copy(endMs = atMs ?: System.currentTimeMillis())
        if (trip != null) {
            replaceTrip(trip)
            val s = _state.value
            val newSgg = s.newInTrip(Level.SGG)
            val stickersNow = s.stickersInTrip()
            // 끝난 곳의 시·도 진행률(그 시·도 시·군·구 중 다녀온 곳)
            val sggIndex = indexes.getValue(Level.SGG)
            val sido = s.current[Level.SIDO]
            val progress = sido?.let { p ->
                Triple(p.name, s.visitsAt(Level.SGG).keys.count { sggIndex.byCode(it)?.parent == p.code }, sggIndex.countIn(p.code))
            }
            addEvent(
                EventKind.END, "여행 종료 요약",
                "총 ${formatKm(trip.distanceM / 1000)} · 새 시·군·구 ${newSgg}곳 · 스티커 ${stickersNow}개" +
                    (progress?.let { " · ${it.first} ${it.second}/${it.third}" } ?: ""),
            )
            award(seasonalAndAchievements(trip.endMs ?: System.currentTimeMillis()).filter { it in setOf("space", "level_up", "streak", "local_explorer", "nation_complete") }, trip.id)
            // 여행을 마치면 저장된 사실만으로 정해진 문장 틀에 넣어 들려준다.
            speak(summaryText(trip.distanceM / 1000, newSgg, stickersNow, progress), flush = true)
        }
        Log.i(TAG, "trip stop")
        _state.update { it.copy(recording = false, trip = trip) }
    }

    /**
     * 머문 시·군·구의 방문을 사용자가 확인했을 때. 그 안에서 머문 읍·면·동과 상위 시·도도 함께 확정하고,
     * 지역 스티커와(경로가 근처를 지난) 장소 스티커를 준다. 통과만 한 곳은 확정 대상이 아니다.
     */
    fun confirmStay(sggCode: String, atMs: Long? = null) = at(atMs) { confirmNow(sggCode, atMs) }

    private fun confirmNow(sggCode: String, atMs: Long?) {
        val sggIndex = indexes.getValue(Level.SGG)
        val region = sggIndex.byCode(sggCode) ?: return
        val record = visits.getValue(Level.SGG)[sggCode] ?: return
        val now = atMs ?: System.currentTimeMillis()

        markConfirmed(Level.SGG, sggCode)
        markConfirmed(Level.SIDO, region.parent)
        val dongIndex = indexes.getValue(Level.DONG)
        visits.getValue(Level.DONG).filter { (code, v) -> v.state == VisitState.STAY_CANDIDATE && dongIndex.byCode(code)?.parent == sggCode }
            .keys.forEach { markConfirmed(Level.DONG, it) }
        addEvent(EventKind.CONFIRM, "${region.name} 방문 확정", region.toString().substringBefore("("), Level.SGG)

        // 스티커: 이 지역을 처음 방문했던 여행의 경로 기준
        val track = if (record.tripId == _state.value.trip?.id) _state.value.track else store.loadTrack(record.tripId)
        val earned = mutableListOf<Sticker>()
        earned += Sticker("region:$sggCode", region.name, now, record.tripId)
        Landmarks.all
            .filter { sggIndex.find(it.lat, it.lon)?.code == sggCode }
            .filter { lm -> track.any { TripTracker.distanceM(it, TrackPoint(0, lm.lat, lm.lon)) <= lm.radiusM } }
            .forEach { earned += Sticker("place:${it.id}", it.name, now, record.tripId) }
        // 추천 여행지: 경로가 근처(1km)를 지난 곳은 장소 스티커, 그 종류(산·바다·사찰…)는 도감 스티커
        val passed = spots.inRegion(sggCode).filter { sp -> track.any { TripTracker.distanceM(it, TrackPoint(0, sp.lat, sp.lon)) <= com.carnect.roadcanvas.data.Spots.VISIT_RADIUS_M } }
        passed.forEach { earned += Sticker("spot:${it.id}", it.name, now, record.tripId) }
        val fresh = earned.filter { s -> stickers.none { it.id == s.id } }
        if (fresh.isNotEmpty()) {
            stickers += fresh
            store.saveStickers(stickers)
            fresh.forEach { s ->
                addEvent(EventKind.STICKER, "${s.title} 스티커 획득", if (s.isPlace) "${region.name} · 장소 스티커" else "${region.province} · 지역 스티커")
            }
            // 정차 중에만 확정할 수 있으므로 음성 안내를 덧붙여도 운전에 방해되지 않는다.
            speak("${fresh.joinToString(", ") { it.title }} 스티커를 받았어요.", flush = false)
        }
        Log.i(TAG, "confirm $sggCode stickers=${fresh.map { it.id }}")
        _state.update { it.copy(visits = snapshotVisits(), stickers = stickers.toList()) }
        award(passed.map { it.kind } + listOfNotNull(regionStickerKey(region)) + seasonalAndAchievements(now), record.tripId, now)
    }

    private fun markConfirmed(level: Level, code: String) {
        val levelVisits = visits.getValue(level)
        val record = levelVisits[code] ?: return
        trackers.getValue(level).confirm(code)
        levelVisits[code] = record.copy(state = VisitState.CONFIRMED)
        store.saveVisits(level, levelVisits)
    }

    fun clear() {
        store.clear()
        visits.values.forEach { it.clear() }
        trips.clear()
        stickers.clear()
        zoneList.clear()
        trackers = buildTrackers()
        _trips.value = emptyList()
        _zones.value = emptyList()
        _state.update { TripState(recording = it.recording, here = it.here, current = it.current) }
    }

    /** 테스트 주행 중에는 실제 위치 대신 [feed]로 넣은 점만 쓴다. */
    @Volatile
    var simulating = false
        set(value) {
            field = value
            trackers.values.forEach(::applyLimits)
        }

    /**
     * 체류 시간·속도 상한. 디버그 빌드의 GPS 주입 시연은 시간을 압축하므로 풀어 두고,
     * 테스트 주행은 위치 시각 자체를 배속해 넣으므로 실제 주행 기준을 그대로 쓴다.
     */
    private fun applyLimits(t: TripTracker) {
        val relaxed = BuildConfig.DEBUG && !simulating
        t.stayMs = if (relaxed) 30_000L else 5 * 60_000L
        t.maxSpeedMps = if (relaxed) 20_000.0 else 70.0
    }

    private fun onLocation(data: LocationData) {
        if (simulating) return
        val time = if (data.timestampMillis > 0) data.timestampMillis else System.currentTimeMillis()
        val point = TrackPoint(time, data.latitude, data.longitude)
        if (_state.value.trip?.active == true && _state.value.recording) handle(point) else showHere(point)
    }

    /** 앱 화면이 열려 있는 동안 지금 위치를 받는다(기록 중이 아니면 화면에만 쓰고 저장하지 않음). */
    @Volatile
    private var watching = false

    fun watch(on: Boolean) {
        watching = on
        if (on) source.start() else if (!_state.value.recording) source.stop()
    }

    /** 기록 없이 화면용 위치만 바꾼다. 30m 넘게 움직였을 때만 지역을 다시 찾는다. */
    private fun showHere(point: TrackPoint) {
        val prev = _state.value.here
        if (prev != null && TripTracker.distanceM(prev, point) < 30) return
        val current = indexes.mapNotNull { (level, index) -> index.find(point.lat, point.lon)?.let { level to it } }.toMap()
        _state.update { it.copy(here = point, current = current) }
    }

    /** 테스트 주행: 가상의 차 위치를 실제 위치와 같은 경로로 처리한다. */
    fun feed(lat: Double, lon: Double, timeMs: Long) = handle(TrackPoint(timeMs, lat, lon))

    /** 위치 때문에 생긴 타임라인 줄은 그 위치의 시각으로 남긴다(테스트 주행의 배속 시각과 맞춤). */
    private var eventTime: Long? = null

    private fun handle(point: TrackPoint) {
        eventTime = point.timeMs
        try {
            handlePoint(point)
        } finally {
            eventTime = null
        }
    }

    private fun handlePoint(point: TrackPoint) {
        val trip = _state.value.trip?.takeIf { it.active } ?: return
        // 저장량을 줄이려고 50m 이상 움직였거나 10초가 지난 점만 쓴다(서 있어도 10초마다 남아 머문 곳 판정은 그대로).
        val before = lastPoint
        if (before != null && point.timeMs - before.timeMs < MIN_GAP_MS && TripTracker.distanceM(before, point) < MIN_MOVE_M) {
            _state.update { it.copy(here = point) }
            return
        }
        // 기록 제외 구역 안의 위치는 판정도 저장도 하지 않는다.
        if (zoneList.any { it.contains(point) }) {
            Log.d(TAG, "location in excluded zone")
            return
        }

        // 세 단계 모두 같은 기준으로 점을 거르므로 시·군·구 결과로 사용 여부를 정한다.
        val results = Level.entries.associateWith { trackers.getValue(it).feed(point) }
        if (!results.getValue(Level.SGG).first) {
            Log.d(TAG, "location skipped ${point.lat},${point.lon}")
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

        val stayed = mutableListOf<String>()
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
                        addEvent(EventKind.STAY, "${r.name} 머묾", "${r.province}${if (r.name != r.province) " " + r.name else ""} · 머문 곳", level)
                        if (level == Level.SGG) stayed += r.code
                    }
                }
            }
            // 다녀온 횟수: 이번 여행에서 처음 들어온 구역이면 한 번 센다.
            var counted = false
            trackers.getValue(level).currentRegion?.code?.let { code ->
                val v = levelVisits[code]
                if (v != null && trip.id !in v.tripIds) {
                    levelVisits[code] = v.copy(tripIds = v.tripIds + trip.id)
                    counted = true
                }
            }
            if (result.second.isNotEmpty() || counted) store.saveVisits(level, levelVisits)
        }

        val distance = trip.distanceM + (prev?.let { TripTracker.distanceM(it, point) } ?: 0.0)
        val updated = trip.copy(distanceM = distance)
        replaceTrip(updated)
        _state.update { it.copy(visits = snapshotVisits(), trip = updated, track = it.track + point, current = current, here = point) }
        // 한곳에 머물렀으면(반경 300m 안 5분 이상) 묻지 않고 바로 방문 확정하고 스티커를 준다.
        stayed.forEach { confirmStay(it, point.timeMs) }
    }

    /** 주행 중 안내는 짧게: 시·도에 처음 들어올 때와 시·군·구 첫 방문만 말한다(읍·면·동은 너무 잦다). */
    private fun announce(level: Level, r: Region) {
        when (level) {
            Level.SIDO -> speak("${r.name}에 처음 왔어요.", flush = false)
            Level.SGG -> speak("${r.name} 첫 방문이에요. 지도에 색이 칠해졌어요.", flush = false)
            Level.DONG -> Unit
        }
    }

    private fun addEvent(kind: EventKind, title: String, detail: String, level: Level? = null) {
        val trip = _state.value.trip ?: return
        val time = eventTime ?: maxOf(System.currentTimeMillis(), _state.value.track.lastOrNull()?.timeMs ?: 0L)
        val event = TimelineEvent(time, trip.id, kind, title, detail, level)
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
        _trips.value = trips.toList()
    }

    private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    init {
        // 머물면 자동 확정하기 전에 쌓인 '확인 대기'는 앱을 켤 때 조용히 확정한다.
        val pending = _state.value.pendingStays
        if (pending.isNotEmpty()) {
            quiet = true
            val at = _state.value.track.lastOrNull()?.timeMs
            pending.forEach { confirmStay(it, at) }
            quiet = false
            Log.i(TAG, "auto-confirmed pending stays $pending")
        }
    }

    companion object {
        /** 여행 레벨: 누적 거리(km) 기준. (레벨, 다음 레벨까지 남은 km, 이번 레벨 진행 0~1) */
        private val LEVEL_KM = listOf(0.0, 100.0, 300.0, 600.0, 1000.0, 1500.0, 2500.0, 4000.0, 6000.0, 10000.0)

        fun levelOf(km: Double): Pair<Int, Double> {
            val lv = LEVEL_KM.indexOfLast { km >= it } + 1
            val next = LEVEL_KM.getOrNull(lv)
            return lv to (next?.minus(km) ?: 0.0)
        }

        fun levelProgress(km: Double): Float {
            val lv = LEVEL_KM.indexOfLast { km >= it }
            val from = LEVEL_KM[lv]
            val to = LEVEL_KM.getOrNull(lv + 1) ?: return 1f
            return ((km - from) / (to - from)).toFloat()
        }

        /** 시·도 지역 스티커 키. 전남광주통합특별시는 옛 광주시 구(동·서·남·북·광산구)면 광주, 아니면 전남. */
        fun regionStickerKey(sgg: Region): String? {
            val p = sgg.province
            return when {
                p.startsWith("서울") -> "region_seoul"
                p.startsWith("부산") -> "region_busan"
                p.startsWith("대구") -> "region_daegu"
                p.startsWith("인천") -> "region_incheon"
                p.startsWith("대전") -> "region_daejeon"
                p.startsWith("울산") -> "region_ulsan"
                p.startsWith("세종") -> "region_sejong"
                p.startsWith("경기") -> "region_gyeonggi"
                p.startsWith("강원") -> "region_gangwon"
                p.startsWith("충청북") || p.startsWith("충북") -> "region_chungbuk"
                p.startsWith("충청남") || p.startsWith("충남") -> "region_chungnam"
                p.startsWith("전북") || p.startsWith("전라북") -> "region_jeonbuk"
                p.contains("광주") && sgg.name in setOf("동구", "서구", "남구", "북구", "광산구") -> "region_gwangju"
                p.contains("광주") || p.startsWith("전라남") || p.startsWith("전남") -> "region_jeonnam"
                p.startsWith("경상북") || p.startsWith("경북") -> "region_gyeongbuk"
                p.startsWith("경상남") || p.startsWith("경남") -> "region_gyeongnam"
                p.startsWith("제주") -> "region_jeju"
                else -> null
            }
        }

        /** 기록할 점 사이 최소 거리(m)·최소 시간(ms). 둘 중 하나만 넘어도 기록한다. */
        const val MIN_MOVE_M = 50.0
        const val MIN_GAP_MS = 10_000L

        /** 여행 요약 문장(기록된 값만 사용). [province]는 (시·도 이름, 다녀온 시·군·구 수, 전체 수). */
        fun summaryText(km: Double, newSgg: Int, stickers: Int, province: Triple<String, Int, Int>?): String = buildString {
            append("이번 여행은 ${if (km >= 10) km.toInt().toString() else "%.1f".format(km)}킬로미터를 달렸어요.")
            when {
                newSgg > 0 && stickers > 0 -> append(" 새로 방문한 시·군·구 ${newSgg}곳, 스티커 ${stickers}개를 모았어요.")
                newSgg > 0 -> append(" 새로 방문한 시·군·구는 ${newSgg}곳이에요.")
                stickers > 0 -> append(" 스티커 ${stickers}개를 모았어요.")
                else -> append(" 새로 칠한 곳은 없어요.")
            }
            province?.let { (name, done, total) -> append(" ${name}는 이제 ${total}곳 중 ${done}곳을 칠했어요.") }
        }

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
