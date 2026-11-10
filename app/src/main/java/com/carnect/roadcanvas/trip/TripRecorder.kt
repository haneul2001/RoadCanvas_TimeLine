package com.carnect.roadcanvas.trip

import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import com.carnect.roadcanvas.BuildConfig
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import com.carnect.roadcanvas.location.LocationSource
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.voice.TtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TripState(
    val recording: Boolean = false,
    val visits: Map<String, VisitState> = emptyMap(),
    val track: List<TrackPoint> = emptyList(),
    val currentRegion: String? = null,
    val lastFix: TrackPoint? = null,
)

/** 위치 수신 → 시·군·구 판정 → 저장 → 첫 방문 음성 안내. */
class TripRecorder(
    context: Context,
    private val index: RegionIndex,
    private val tts: TtsEngine,
) {
    private val store = TripStore(context)
    private val tracker = TripTracker(
        index,
        store.loadVisits(),
        // 시연(디버그 빌드)은 GPS 주입으로 시간을 압축하므로 체류 시간과 속도 상한을 풀어 둔다.
        stayMs = if (BuildConfig.DEBUG) 30_000L else 5 * 60_000L,
        maxSpeedMps = if (BuildConfig.DEBUG) 20_000.0 else 70.0,
    )
    private val source = LocationSource(context, ::onLocation)

    private val _state = MutableStateFlow(TripState(visits = tracker.visitStates.toMap(), track = store.loadTrack()))
    val state: StateFlow<TripState> = _state.asStateFlow()

    fun start() {
        if (_state.value.recording) return
        Log.i(TAG, "trip recording start")
        source.start()
        _state.update { it.copy(recording = true) }
    }

    fun stop() {
        if (!_state.value.recording) return
        Log.i(TAG, "trip recording stop")
        source.stop()
        _state.update { it.copy(recording = false) }
    }

    fun confirm(code: String) {
        tracker.confirm(code)
        store.saveVisits(tracker.visitStates)
        _state.update { it.copy(visits = tracker.visitStates.toMap()) }
    }

    fun clear() {
        store.clear()
        _state.update { TripState(recording = it.recording) }
    }

    private fun onLocation(data: LocationData) {
        val time = if (data.timestampMillis > 0) data.timestampMillis else System.currentTimeMillis()
        val point = TrackPoint(time, data.latitude, data.longitude)
        val (used, events) = tracker.feed(point)
        if (!used) {
            Log.d(TAG, "location skipped ${data.latitude},${data.longitude}")
            return
        }
        store.appendPoint(point)
        events.forEach(::announce)
        if (events.isNotEmpty()) store.saveVisits(tracker.visitStates)
        _state.update {
            it.copy(
                visits = tracker.visitStates.toMap(),
                track = it.track + point,
                currentRegion = tracker.currentRegion?.toString(),
                lastFix = point,
            )
        }
    }

    private fun announce(event: TripEvent) {
        when (event) {
            is TripEvent.FirstPass -> {
                Log.i(TAG, "first pass ${event.region}")
                // 주행 중 안내는 지역당 한 번, 한 문장.
                tts.speak("${event.region.name} 첫 방문이에요. 지도에 색이 칠해졌어요.", flush = false)
            }
            is TripEvent.StayCandidate -> Log.i(TAG, "stay candidate ${event.region}")
        }
    }
}
