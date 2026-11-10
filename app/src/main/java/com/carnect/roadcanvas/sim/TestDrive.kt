package com.carnect.roadcanvas.sim

import android.util.Log
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import com.carnect.roadcanvas.plan.PlanPhase
import com.carnect.roadcanvas.plan.TripPlanner
import com.carnect.roadcanvas.plan.ro
import com.carnect.roadcanvas.trip.TrackPoint
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.trip.TripTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DriveState(
    val running: Boolean = false,
    /** 시간 배속(1 = 실제 속도). */
    val speedX: Int = 60,
    val kmh: Int = 80,
    val status: String = "",
    /** 다음 목적지까지 남은 거리(km). */
    val remainingKm: Double? = null,
    val drivenKm: Double = 0.0,
)

/**
 * 테스트 주행. 가상의 차가 Voice Planner의 지금 목적지로 달린다.
 * - 안내 중(출발 후)일 때만 움직이고, 도착해 '출발'을 기다리는 동안은 그 자리에 서 있다(머묾 판정도 그대로 일어남).
 * - 시간은 [DriveState.speedX]배로 흐른다. 위치 시각도 그만큼 앞당겨 넣어 체류 시간·속도 판정이 실제와 같은 기준으로 돈다.
 * - 길 모양은 [RoadBook](주요 도시 사이 실제 도로 좌표)에서 찾고, 없으면 직선으로 간다(내비 SDK는 경로 좌표를 주지 않음).
 */
class TestDrive(
    private val scope: CoroutineScope,
    private val recorder: TripRecorder,
    private val planner: TripPlanner,
    private val roads: RoadBook,
    private val ensureRecording: () -> Unit,
    private val stopRecording: () -> Unit,
) {
    private val _state = MutableStateFlow(DriveState())
    val state: StateFlow<DriveState> = _state.asStateFlow()
    private var job: Job? = null

    fun setSpeed(x: Int) {
        _state.value = _state.value.copy(speedX = x)
    }

    /** [from]을 주면 그 자리에서 출발한다(없으면 마지막 기록 위치). */
    fun start(from: Pair<Double, Double>?) {
        if (job?.isActive == true) return
        val origin = from ?: recorder.state.value.track.lastOrNull()?.let { it.lat to it.lon } ?: TripPlanner.DEFAULT_START
        recorder.simulating = true
        planner.simulated = true
        // 테스트 한 번 = 여행 하나. 기록 중이면 지금 여행을 닫고 새로 시작해 기록이 따로 쌓이게 한다.
        if (recorder.state.value.recording) {
            recorder.stop()
            recorder.start()
        } else {
            ensureRecording()
        }
        _state.value = _state.value.copy(running = true, status = "출발 준비", drivenKm = 0.0, remainingKm = null)
        job = scope.launch { loop(origin) }
    }

    fun stop() {
        job?.cancel()
        job = null
        recorder.simulating = false
        planner.simulated = false
        _state.value = _state.value.copy(running = false, status = "멈춤", remainingKm = null)
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.loop(origin: Pair<Double, Double>) {
        // 여행 기록이 켜질 때까지(권한·서비스) 잠깐 기다린다.
        repeat(20) { if (!recorder.state.value.recording) delay(250) }
        if (!recorder.state.value.recording) {
            _state.value = _state.value.copy(running = false, status = "위치 권한이 없어 기록을 시작하지 못했어요")
            recorder.simulating = false
            return
        }
        var pos = origin
        // 테스트마다 새 여행이므로 시계도 지금부터 다시 센다(이전 테스트의 배속 시각을 잇지 않음).
        var simTime = System.currentTimeMillis()
        var path: List<Pair<Double, Double>> = emptyList()
        var pathTarget: String? = null
        var pathIndex = 0
        var followsRoad = false
        var lastFeedSim = 0L
        Log.i(TAG, "test drive start at $pos")
        recorder.feed(pos.first, pos.second, simTime)

        while (isActive) {
            delay(TICK_MS)
            val s = _state.value
            val dtSim = TICK_MS * s.speedX
            simTime += dtSim
            val plan = planner.state.value
            val target = plan.target

            if (plan.phase == PlanPhase.DONE || (target == null && plan.stops.isNotEmpty())) {
                _state.value = s.copy(status = "모든 목적지에 도착", remainingKm = 0.0)
                stop()
                stopRecording()
                return
            }
            if (target == null || plan.phase != PlanPhase.GUIDING) {
                val here = plan.stops.getOrNull(plan.index - 1)?.name
                _state.value = s.copy(
                    status = when {
                        target == null -> "목적지를 기다리는 중"
                        here != null -> "${here}에 도착 · 출발을 누르면 ${target.name.ro}"
                        else -> "출발을 누르면 ${target.name.ro}"
                    },
                    remainingKm = null,
                )
                // 서 있는 동안도 가끔 위치를 넣어 '머문 곳' 판정이 실제처럼 일어나게 한다.
                if (simTime - lastFeedSim >= 10_000) {
                    recorder.feed(pos.first, pos.second, simTime)
                    lastFeedSim = simTime
                }
                continue
            }
            if (pathTarget != target.name) {
                val (p, onRoad) = roads.route(pos, target.lat to target.lon)
                path = p
                followsRoad = onRoad
                pathTarget = target.name
                pathIndex = 0
                Log.i(TAG, "test drive path to ${target.name}: ${path.size} pts, road=$followsRoad")
            }
            // 이번 틱에 갈 거리만큼 경로를 따라 이동한다.
            var budget = s.kmh / 3.6 * dtSim / 1000.0
            var driven = 0.0
            while (budget > 0 && pathIndex < path.size) {
                val next = path[pathIndex]
                val d = km(pos, next) * 1000
                if (d <= budget) {
                    pos = next
                    budget -= d
                    driven += d
                    pathIndex++
                } else {
                    val t = budget / d
                    pos = pos.first + (next.first - pos.first) * t to pos.second + (next.second - pos.second) * t
                    driven += budget
                    budget = 0.0
                }
            }
            recorder.feed(pos.first, pos.second, simTime)
            lastFeedSim = simTime
            val remaining = (path.getOrNull(pathIndex)?.let { km(pos, it) } ?: 0.0) + remainingPath(path, pathIndex)
            _state.value = s.copy(
                status = "${target.name.ro} 가는 중" + if (followsRoad) "" else " (이 구간은 도로 데이터가 없어 직선)",
                remainingKm = remaining,
                drivenKm = s.drivenKm + driven / 1000,
            )
        }
    }

    private fun remainingPath(path: List<Pair<Double, Double>>, from: Int): Double {
        var sum = 0.0
        for (i in from until path.size - 1) sum += km(path[i], path[i + 1])
        return sum
    }

    companion object {
        private const val TICK_MS = 250L
        val SPEEDS = listOf(1, 10, 30, 60, 120)

        fun km(a: Pair<Double, Double>, b: Pair<Double, Double>) =
            TripTracker.distanceM(TrackPoint(0, a.first, a.second), TrackPoint(0, b.first, b.second)) / 1000
    }
}
