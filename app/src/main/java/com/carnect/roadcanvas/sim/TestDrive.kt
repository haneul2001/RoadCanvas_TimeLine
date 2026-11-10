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
import org.json.JSONObject
import java.io.InputStream

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
 * - 길 모양은 앱에 넣어 둔 시연 도로(서울~강릉 실제 도로 좌표) 위에 출발지와 목적지가 모두 가까우면 그 도로를 따르고,
 *   아니면 직선으로 간다(내비 SDK는 경로 좌표를 주지 않음).
 */
class TestDrive(
    private val scope: CoroutineScope,
    private val recorder: TripRecorder,
    private val planner: TripPlanner,
    private val road: List<Pair<Double, Double>>,
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
        var simTime = maxOf(System.currentTimeMillis(), (recorder.state.value.track.lastOrNull()?.timeMs ?: 0L) + 1_000)
        var path: List<Pair<Double, Double>> = emptyList()
        var pathTarget: String? = null
        var pathIndex = 0
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
                path = route(pos, target.lat to target.lon)
                pathTarget = target.name
                pathIndex = 0
                Log.i(TAG, "test drive path to ${target.name}: ${path.size} pts")
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
                status = "${target.name}로 가는 중",
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

    /** 시연 도로 위로 갈 수 있으면 그 도로를 따라가는 점들, 아니면 300m 간격 직선. 마지막 점은 목적지. */
    private fun route(from: Pair<Double, Double>, to: Pair<Double, Double>): List<Pair<Double, Double>> {
        if (road.size > 1) {
            val a = nearest(from)
            val b = nearest(to)
            if (a.second <= SNAP_KM && b.second <= SNAP_KM && a.first != b.first) {
                val section = if (a.first < b.first) road.subList(a.first, b.first + 1) else road.subList(b.first, a.first + 1).reversed()
                return section + to
            }
        }
        val n = (km(from, to) / 0.3).toInt().coerceAtLeast(1)
        return (1..n).map { i -> val t = i.toDouble() / n; from.first + (to.first - from.first) * t to from.second + (to.second - from.second) * t }
    }

    private fun nearest(p: Pair<Double, Double>): Pair<Int, Double> {
        var best = 0
        var bestKm = Double.MAX_VALUE
        road.forEachIndexed { i, q ->
            val d = km(p, q)
            if (d < bestKm) {
                bestKm = d
                best = i
            }
        }
        return best to bestKm
    }

    companion object {
        private const val TICK_MS = 250L
        /** 출발지·목적지가 시연 도로에서 이만큼 안이면 도로를 따른다. */
        private const val SNAP_KM = 15.0
        val SPEEDS = listOf(1, 10, 30, 60, 120)

        fun km(a: Pair<Double, Double>, b: Pair<Double, Double>) =
            TripTracker.distanceM(TrackPoint(0, a.first, a.second), TrackPoint(0, b.first, b.second)) / 1000

        fun loadRoad(input: InputStream): List<Pair<Double, Double>> {
            val pts = JSONObject(input.bufferedReader().readText()).getJSONArray("points")
            return (0 until pts.length()).map { pts.getJSONArray(it).let { p -> p.getDouble(0) to p.getDouble(1) } }
        }
    }
}
