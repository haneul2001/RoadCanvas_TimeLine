package com.carnect.roadcanvas

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carnect.roadcanvas.plan.PlanState
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.trip.ExcludedZone
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.trip.TripService
import com.carnect.roadcanvas.trip.TripState
import com.carnect.roadcanvas.ui.StatusScreen
import com.carnect.roadcanvas.ui.home.DetourScreen
import com.carnect.roadcanvas.ui.home.Feature
import com.carnect.roadcanvas.ui.home.HomeScreen
import com.carnect.roadcanvas.ui.home.PlannerScreen
import com.carnect.roadcanvas.ui.home.QuestScreen
import com.carnect.roadcanvas.ui.home.SettingsScreen
import com.carnect.roadcanvas.ui.home.TripsScreen
import com.carnect.roadcanvas.ui.home.TestDriveScreen
import com.carnect.roadcanvas.plan.PlanPhase
import com.carnect.roadcanvas.plan.TripPlanner
import com.carnect.roadcanvas.sim.DriveState
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val app get() = application as RoadCanvasApp

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) TripService.start(this)
    }

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) listen() else toast("마이크 권한이 있어야 음성으로 계획을 말할 수 있어요")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 지도 색 테마는 기기 안 설정에 저장해 두고 다시 켜도 유지한다.
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        com.carnect.roadcanvas.ui.home.MapColors.theme =
            runCatching { com.carnect.roadcanvas.ui.home.MapTheme.valueOf(prefs.getString("map_theme", null) ?: "RAINBOW") }
                .getOrDefault(com.carnect.roadcanvas.ui.home.MapTheme.RAINBOW)
        val empty = MutableStateFlow(TripState())
        val emptyPlan = MutableStateFlow(PlanState())
        val emptyDrive = MutableStateFlow(DriveState())
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                    var demoStatus by remember { mutableStateOf<String?>(null) }
                    val scope = rememberCoroutineScope()
                    val regions by app.regions.collectAsStateWithLifecycle()
                    val vehicle by app.vehicleRepo.state.collectAsStateWithLifecycle()
                    // 경계 데이터를 읽은 뒤에 기록기·계획기가 생긴다.
                    val loaded = regions != null
                    val trip by ((if (loaded) app.tripRecorder?.state else null) ?: empty).collectAsStateWithLifecycle()
                    val plan by ((if (loaded) app.planner?.state else null) ?: emptyPlan).collectAsStateWithLifecycle()
                    val drive by ((if (loaded) app.testDrive?.state else null) ?: emptyDrive).collectAsStateWithLifecycle()
                    val homeDecos by app.decor.items.collectAsStateWithLifecycle()
                    val testDrive = if (loaded) app.testDrive else null
                    val listening by app.stt.listening.collectAsStateWithLifecycle()
                    val partial by app.stt.partial.collectAsStateWithLifecycle()
                    val sttStatus by app.stt.status.collectAsStateWithLifecycle()
                    val parked = vehicle.parked
                    val indexes = regions
                    val recorder = if (loaded) app.tripRecorder else null
                    val planner = if (loaded) app.planner else null
                    val explorer = if (loaded) app.explorer else null
                    val home = { screen = Screen.HOME }
                    // 상세 화면은 정차 중에만 연다. 주행으로 바뀌면 지도(음성만)로 돌아간다.
                    val ready = parked && indexes != null && recorder != null && planner != null && explorer != null && testDrive != null
                    val current = if (ready) screen else Screen.HOME
                    if (current != Screen.HOME) BackHandler { screen = Screen.HOME }

                    when (current) {
                        Screen.PLANNER -> PlannerScreen(
                            plan, listening, partial, sttStatus,
                            onMic = { if (listening) app.stt.cancel() else startListening() },
                            onSay = { planner!!.hear(it) },
                            onDepart = { planner!!.depart() },
                            onSkip = { planner!!.skip() },
                            onClear = { planner!!.clear() },
                            onClose = home,
                        )
                        Screen.QUEST -> QuestScreen(
                            explorer!!, trip,
                            onGo = { p ->
                                planner!!.clear()
                                planner.add(p)
                                planner.depart()
                                screen = Screen.HOME
                            },
                            onAdd = { p -> planner!!.add(p) },
                            onClose = home,
                        )
                        Screen.DETOUR -> DetourScreen(
                            explorer!!, trip, plan,
                            onDetour = { p ->
                                planner!!.detourVia(p)
                                screen = Screen.PLANNER
                            },
                            onOpenPlanner = { screen = Screen.PLANNER },
                            onClose = home,
                        )
                        Screen.TRIPS -> {
                            val trips by recorder!!.tripList.collectAsStateWithLifecycle()
                            TripsScreen(
                                indexes!!, trips, trip,
                                load = recorder::loadTrip,
                                loadPeriod = recorder::loadPeriod,
                                onDelete = { recorder.deleteTrip(it); toast("여행 기록을 지웠어요") },
                                onClose = home,
                            )
                        }
                        Screen.SETTINGS -> {
                            val zones by recorder!!.zones.collectAsStateWithLifecycle()
                            val here = trip.track.lastOrNull()
                            val hereName = here?.let { trip.current[Level.SGG]?.let(TripRecorder::placeName) ?: "현재 위치" }
                            SettingsScreen(
                                zones, hereName, trip.recording,
                                onTheme = { t ->
                                    com.carnect.roadcanvas.ui.home.MapColors.theme = t
                                    prefs.edit().putString("map_theme", t.name).apply()
                                },
                                onAddZone = {
                                    if (here != null) {
                                        recorder.addZone(ExcludedZone("$hereName 근처", here.lat, here.lon))
                                        toast("이 근처 500m는 기록하지 않아요")
                                    }
                                },
                                onRemoveZone = recorder::removeZone,
                                onClearAll = {
                                    recorder.clear()
                                    app.decor.wipe()
                                    planner?.clear()
                                    toast("여행 기록을 모두 지웠어요")
                                },
                                onClose = home,
                            ) {
                                Column(Modifier.padding(8.dp)) { StatusScreen(app.vehicleRepo, app.naviRepo, app.tts) }
                            }
                        }
                        Screen.TEST -> TestDriveScreen(
                            plan, drive, trip.recording,
                            onDemo = app.demoData?.let { demo ->
                                {
                                    demoStatus = "샘플 여행 만드는 중…"
                                    scope.launch {
                                        val ok = demo.generate { n, total -> demoStatus = "샘플 여행 만드는 중… ($n/$total)" }
                                        demoStatus = if (ok) "샘플 여행 ${demo.tripCount}개를 만들었어요. 기록 보기에서 확인하세요." else "기록 중에는 만들 수 없어요."
                                    }
                                }
                            },
                            demoStatus = demoStatus,
                            onPlan = { text, fromSeoul -> planner!!.hear(text, if (fromSeoul) TripPlanner.DEFAULT_START else null) },
                            onSpeed = { testDrive!!.setSpeed(it) },
                            onStart = { fromSeoul ->
                                if (checkSelfPermission(RoadCanvasApp.FUSED_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                    locationPermission.launch(RoadCanvasApp.FUSED_LOCATION)
                                } else {
                                    testDrive!!.start(if (fromSeoul) TripPlanner.DEFAULT_START else null)
                                    planner!!.depart()
                                    screen = Screen.HOME
                                }
                            },
                            onStop = { testDrive!!.stop() },
                            onReset = {
                                testDrive!!.stop()
                                if (trip.recording) TripService.stop(this)
                                planner!!.clear()
                                recorder!!.clear()
                                app.decor.wipe()
                                toast("모든 기록을 지우고 처음 상태로 돌렸어요")
                            },
                            onClose = home,
                        )
                        Screen.DECORATE -> {
                            val decos by app.decor.items.collectAsStateWithLifecycle()
                            val custom by app.decor.custom.collectAsStateWithLifecycle()
                            val trips by recorder!!.tripList.collectAsStateWithLifecycle()
                            // 지금까지 모든 여행을 한 지도에(없으면 지금 상태)
                            val all = remember(trips) { recorder.loadPeriod(trips.filter { it.endMs != null }.map { it.id }) } ?: trip
                            com.carnect.roadcanvas.ui.home.DecorateScreen(indexes!!, all, app.decor, decos, custom, onClose = home)
                        }
                        Screen.HOME -> HomeScreen(
                            indexes = regions,
                            trip = trip,
                            parked = parked,
                            driving = vehicle.gear != null && !parked,
                            onToggleTrip = { toggleTrip(trip.recording) },
                            onConfirmStay = { code -> recorder?.confirmStay(code) },
                            onFeature = { f ->
                                screen = when (f) {
                                    Feature.SETTINGS -> Screen.SETTINGS
                                    Feature.VOICE_PLANNER -> Screen.PLANNER
                                    Feature.CANVAS_QUEST -> Screen.QUEST
                                    Feature.SMART_DETOUR -> Screen.DETOUR
                                    Feature.TIMELINE -> Screen.TRIPS
                                    else -> Screen.HOME
                                }
                            },
                            voiceText = if (listening) partial else plan.message,
                            listening = listening,
                            onVoice = { if (listening) app.stt.cancel() else startListening() },
                            simLine = if (drive.running) {
                                buildString {
                                    append("×${drive.speedX} · ${drive.status}")
                                    drive.remainingKm?.let { append(" · 남은 ${it.toInt()}km") }
                                    // 지금 위치를 실시간으로: 시·군·구 + 읍·면·동, 좌표, 속도
                                    val p = trip.track.lastOrNull()
                                    if (p != null) {
                                        val sgg = trip.current[Level.SGG]
                                        val dong = trip.current[Level.DONG]?.name
                                        append("\n지금 ")
                                        append(listOfNotNull(sgg?.province, sgg?.name, dong).distinct().joinToString(" ").ifEmpty { "위치 확인 중" })
                                        append(" (%.4f, %.4f)".format(p.lat, p.lon))
                                        append(if (drive.status.contains("가는 중")) " · 시속 ${drive.kmh}km" else " · 정차")
                                    }
                                }
                            } else {
                                null
                            },
                            simWaiting = drive.running && plan.target != null && plan.phase != PlanPhase.GUIDING,
                            onSimDepart = { planner?.depart() },
                            onSimStop = { testDrive?.stop() },
                            onTestDrive = { screen = Screen.TEST },
                            decorations = homeDecos,
                            onDecorate = { screen = Screen.DECORATE },
                        )
                    }
                }
            }
        }
    }

    private fun startListening() {
        val needed = listOf(RECORD_AUDIO, STT_SERVICE).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) listen() else micPermission.launch(needed.toTypedArray())
    }

    private fun listen() {
        val planner = app.planner ?: return
        app.stt.listen { text -> planner.hear(text) }
    }

    /** 위치 기록은 사용자가 시작을 누르고 위치 권한을 허용한 뒤에만 한다. */
    private fun toggleTrip(recording: Boolean) {
        if (recording) {
            TripService.stop(this)
            return
        }
        if (app.tripRecorder == null) return
        if (checkSelfPermission(RoadCanvasApp.FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            TripService.start(this)
        } else {
            locationPermission.launch(RoadCanvasApp.FUSED_LOCATION)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private enum class Screen { HOME, PLANNER, QUEST, DETOUR, TRIPS, SETTINGS, TEST, DECORATE }

    private companion object {
        const val RECORD_AUDIO = android.Manifest.permission.RECORD_AUDIO
        const val STT_SERVICE = "pleos.car.permission.STT_SERVICE"
    }
}
