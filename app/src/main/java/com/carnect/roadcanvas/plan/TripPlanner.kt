package com.carnect.roadcanvas.plan

import android.util.Log
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import com.carnect.roadcanvas.navi.NaviEvent
import com.carnect.roadcanvas.navi.NaviRepo
import com.carnect.roadcanvas.voice.TtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PlanPhase { IDLE, PROPOSED, GUIDING, ARRIVED, DONE }

data class PlanState(
    val stops: List<Place> = emptyList(),
    /** 지금(또는 다음) 목적지 순번. */
    val index: Int = 0,
    val phase: PlanPhase = PlanPhase.IDLE,
    /** 마지막으로 들은 말. */
    val heard: String = "",
    /** 앱이 마지막으로 한 말(화면에도 보여 준다). */
    val message: String = "",
) {
    val target: Place? get() = stops.getOrNull(index)
    val remaining: List<Place> get() = stops.drop(index)
}

/**
 * 음성 다구간 여행(Voice Planner). 말 → 규칙 해석 → 순서 계산 → 내비에 한 곳씩 등록 → 도착하면 다음 곳.
 * 내비 SDK는 경유지를 2곳까지만 받으므로, 목적지를 하나씩 순서대로 요청한다.
 * 도착은 내비 도착 이벤트와 차량 위치(목적지 [ARRIVE_KM] 안) 중 먼저 오는 것으로 판단한다.
 */
class TripPlanner(
    private val places: Places,
    private val navi: NaviRepo,
    private val tts: TtsEngine,
    /** 지금 차 위치(없으면 null). */
    private val position: () -> Pair<Double, Double>?,
    /** 길 안내를 시작할 때 여행 기록도 켜 둔다(도착 판단에 위치가 필요). */
    private val ensureRecording: () -> Unit,
) {
    private val _state = MutableStateFlow(PlanState())
    val state: StateFlow<PlanState> = _state.asStateFlow()
    private var announceEta = false

    /**
     * 테스트 주행 중이면 true. 에뮬레이터 내비는 자기 기본 위치에서 경로를 계산하므로
     * 그 예상 시간을 우리 차 기준인 것처럼 말하지 않는다.
     */
    @Volatile
    var simulated = false
    /** 테스트 주행처럼 다른 곳에서 출발할 때 순서 계산 기준. */
    private var startOverride: Pair<Double, Double>? = null

    /** 들은 말(또는 예시 문장)을 처리한다. */
    fun hear(text: String, from: Pair<Double, Double>? = null) {
        startOverride = from
        val cmd = PlanParser.parse(text, places, from ?: position())
        Log.i(TAG, "plan hear '$text' -> $cmd")
        _state.update { it.copy(heard = text) }
        when (cmd) {
            is PlanCommand.NewPlan -> propose(cmd)
            PlanCommand.Depart -> depart()
            is PlanCommand.Remove -> remove(cmd.place)
            is PlanCommand.Prioritize -> prioritize(cmd.place, cmd.instead)
            is PlanCommand.Ask -> say(brief(cmd.place))
            PlanCommand.Unknown -> say("잘 못 알아들었어요. 예를 들어 '속초, 양양, 강릉 들렀다가 서울로'처럼 말해 주세요.")
        }
    }

    /** 미방문 추천 등에서 목적지 하나를 바로 넣을 때. */
    fun add(place: Place) {
        val s = _state.value
        if (s.phase == PlanPhase.IDLE || s.phase == PlanPhase.DONE) {
            _state.update { PlanState(stops = listOf(place), phase = PlanPhase.PROPOSED, heard = it.heard) }
            say("${place.name}까지 안내할게요. 출발하실 때 출발이라고 말씀해 주세요.")
        } else {
            _state.update { it.copy(stops = it.stops + place) }
            say("${place.name.eul} 마지막 목적지로 넣었어요.")
        }
    }

    /**
     * Smart Detour: 다음 목적지 앞에 들를 곳을 끼워 넣는다. 안내 중이면 내비 경로에 경유지로 넣어
     * 원래 목적지는 그대로 둔다(내비 SDK 경유지는 최대 2곳).
     */
    fun detourVia(place: Place) {
        val s = _state.value
        if (s.target == null) return add(place)
        _state.update { it.copy(stops = it.stops.take(it.index) + place + it.stops.drop(it.index)) }
        if (s.phase == PlanPhase.GUIDING) navi.addWaypoint(place.name, place.lat, place.lon)
        say("${s.target!!.name} 가기 전에 ${place.name}에 들를게요.")
    }

    fun depart() {
        val s = _state.value
        val target = s.target
        when {
            target == null -> say("먼저 갈 곳을 말씀해 주세요.")
            s.phase == PlanPhase.GUIDING -> say("${target.name.ro} 가는 중이에요.")
            else -> {
                ensureRecording()
                announceEta = true
                navi.requestRoute(target.name, target.lat, target.lon)
                _state.update { it.copy(phase = PlanPhase.GUIDING) }
                say("${target.name.ro} 안내를 시작할게요.")
            }
        }
    }

    fun skip() {
        val s = _state.value
        if (s.target == null) return
        advance(arrivedAt = null)
    }

    fun clear() {
        if (_state.value.phase == PlanPhase.GUIDING) navi.cancelRoute()
        _state.value = PlanState(message = "여행 계획을 지웠어요.")
    }

    fun onNavi(event: NaviEvent) {
        val s = _state.value
        when (event) {
            is NaviEvent.RouteStarted -> if (announceEta && s.phase == PlanPhase.GUIDING) {
                announceEta = false
                if (simulated) {
                    say("내비에 ${s.target?.name ?: "목적지"} 경로를 넣었어요. 테스트 주행은 이 지도에서 움직여요.")
                    return
                }
                val min = event.durationSec / 60
                val time = if (min >= 60) "${min / 60}시간 ${min % 60}분" else "${min}분"
                say("${s.target?.name}까지 약 $time 걸려요.")
            }
            is NaviEvent.DestinationArrived -> if (s.phase == PlanPhase.GUIDING) arrived()
            else -> Unit
        }
    }

    fun onLocation(lat: Double, lon: Double) {
        val s = _state.value
        val target = s.target ?: return
        if (s.phase == PlanPhase.GUIDING && target.distanceKmTo(lat, lon) <= ARRIVE_KM) arrived()
    }

    /** 앱에 담긴 대표 명소로 짧게 소개한다(외부 관광 정보 없이, 있는 사실만). */
    private fun brief(place: Place): String {
        val near = com.carnect.roadcanvas.data.Landmarks.all
            .map { it to place.distanceKmTo(it.lat, it.lon) }
            .filter { it.second <= BRIEF_KM }
            .sortedBy { it.second }
            .take(3)
            .map { it.first }
        if (near.isEmpty()) return "${place.name} 근처는 앱에 담긴 명소 정보가 아직 없어요. Canvas Quest에서 주변 미방문 지역을 볼 수 있어요."
        return "${place.name} 근처 명소를 알려 드릴게요. " +
            near.joinToString(" ") { if (it.note.isNotEmpty()) "${it.name.eun} ${it.note.ieyo}" else "${it.name.iga} 있어요." }
    }

    private fun propose(cmd: PlanCommand.NewPlan) {
        val start = startOverride ?: position() ?: DEFAULT_START
        val ordered = RouteOrder.order(start, cmd.stops, cmd.finalStop)
        if (ordered.isEmpty()) return say("갈 곳을 찾지 못했어요.")
        if (_state.value.phase == PlanPhase.GUIDING) navi.cancelRoute()
        _state.update { PlanState(stops = ordered, phase = PlanPhase.PROPOSED, heard = it.heard) }
        val first = ordered.first()
        say(
            (if (ordered.size == 1) "${first.name}까지 안내할게요. " else "${ordered.joinToString(", ") { it.name }} 순서로 안내할게요. ") +
                "첫 목적지 ${first.name}까지 직선으로 약 ${first.distanceKmTo(start.first, start.second).toInt()}km예요. 출발이라고 말씀해 주세요."
        )
    }

    private fun remove(place: Place) {
        val s = _state.value
        val i = s.stops.indexOfFirst { it.name == place.name }
        if (i < s.index || i < 0) return say("${place.name.eun} 계획에 없어요.")
        val wasTarget = i == s.index && s.phase == PlanPhase.GUIDING
        _state.update { it.copy(stops = it.stops.filterIndexed { j, _ -> j != i }) }
        say("${place.name.eun} 빼고 갈게요.")
        if (wasTarget) reroute()
    }

    private fun prioritize(place: Place, instead: Place?) {
        _state.update { s ->
            val rest = s.stops.drop(s.index).filter { it.name != place.name && it.name != instead?.name }
            s.copy(stops = s.stops.take(s.index) + place + rest)
        }
        say(if (instead != null) "${instead.name} 대신 ${place.name} 먼저 갈게요." else "${place.name} 먼저 갈게요.")
        if (_state.value.phase == PlanPhase.GUIDING) reroute()
    }

    private fun reroute() {
        val target = _state.value.target ?: return finish()
        announceEta = true
        navi.requestRoute(target.name, target.lat, target.lon)
    }

    private fun arrived() {
        val s = _state.value
        val target = s.target ?: return
        val last = s.index >= s.stops.lastIndex
        if (last) {
            _state.update { it.copy(phase = PlanPhase.DONE, index = it.stops.size) }
            say("${target.name}에 도착했어요. 계획한 곳을 모두 들렀어요.")
        } else {
            _state.update { it.copy(phase = PlanPhase.ARRIVED) }
            advance(arrivedAt = target)
        }
    }

    private fun advance(arrivedAt: Place?) {
        _state.update { it.copy(index = it.index + 1, phase = if (it.phase == PlanPhase.GUIDING) PlanPhase.GUIDING else it.phase) }
        val next = _state.value.target
        when {
            next == null -> finish()
            arrivedAt != null -> say("${arrivedAt.name}에 도착했어요. 출발하실 때 출발이라고 말씀해 주세요. 다음은 ${next.name.ieyo}")
            _state.value.phase == PlanPhase.GUIDING -> {
                say("${next.name.ro} 바로 갈게요.")
                reroute()
            }
            else -> say("다음 목적지는 ${next.name.ieyo}")
        }
    }

    private fun finish() {
        _state.update { it.copy(phase = PlanPhase.DONE) }
        say("계획한 곳을 모두 들렀어요.")
    }

    private fun say(text: String) {
        Log.i(TAG, "plan say '$text'")
        _state.update { it.copy(message = text) }
        tts.speak(text, flush = true)
    }

    companion object {
        const val ARRIVE_KM = 1.0
        /** 명소 소개 범위(km). */
        const val BRIEF_KM = 25.0
        /** 위치를 아직 모를 때 순서 계산 기준(서울시청). */
        val DEFAULT_START = 37.5663 to 126.9779
    }
}

/** 받침에 맞춘 조사. 한글로 끝나지 않으면 받침 없는 쪽을 쓴다. */
private fun String.hasBatchim(): Boolean {
    val c = lastOrNull() ?: return false
    if (c !in '가'..'힣') return false
    return (c - '가') % 28 != 0
}

private fun String.batchimIsRieul(): Boolean {
    val c = lastOrNull() ?: return false
    return c in '가'..'힣' && (c - '가') % 28 == 8
}

internal val String.ro get() = this + if (hasBatchim() && !batchimIsRieul()) "으로" else "로"
internal val String.eun get() = this + if (hasBatchim()) "은" else "는"
internal val String.eul get() = this + if (hasBatchim()) "을" else "를"
internal val String.iga get() = if (hasBatchim()) "이" else "가"
internal val String.ieyo get() = this + if (hasBatchim()) "이에요." else "예요."
