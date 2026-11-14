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

    /** 화면용 현재 위치(저장 안 함)를 위한 위치 권한. */
    private val locationAllowed = androidx.compose.runtime.mutableStateOf(false)
    private val watchPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationAllowed.value = granted
        if (granted) app.tripRecorder?.watch(true)
    }

    override fun onStart() {
        super.onStart()
        locationAllowed.value = checkSelfPermission(RoadCanvasApp.FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (locationAllowed.value) app.tripRecorder?.watch(true)
    }

    override fun onStop() {
        // 화면이 안 보이면 화면용 위치는 끈다(기록 중이면 기록은 계속).
        app.tripRecorder?.watch(false)
        super.onStop()
    }

    /** 자동 기록을 켤 때 위치 권한. */
    private val autoPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationAllowed.value = granted
        if (granted) {
            app.setAutoRecord(true)
            app.tripRecorder?.watch(true)
        } else {
            toast("위치 권한이 있어야 자동 기록을 켤 수 있어요")
        }
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
            // 화면 모드: 설정(밝게/어둡게/시스템 따라)
            var themeMode by rememberSaveable {
                mutableStateOf(runCatching { com.carnect.roadcanvas.ui.home.ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "LIGHT") }
                    .getOrDefault(com.carnect.roadcanvas.ui.home.ThemeMode.LIGHT))
            }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (themeMode) {
                com.carnect.roadcanvas.ui.home.ThemeMode.LIGHT -> false
                com.carnect.roadcanvas.ui.home.ThemeMode.DARK -> true
                com.carnect.roadcanvas.ui.home.ThemeMode.SYSTEM -> systemDark
            }
            com.carnect.roadcanvas.ui.home.Palette.dark = dark
            MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
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
                    val autoRecord by app.autoRecord.collectAsStateWithLifecycle()
                    val mainTrips by ((if (loaded) app.tripRecorder?.tripList else null) ?: MutableStateFlow(emptyList())).collectAsStateWithLifecycle()
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

                    // 메인에서 주차를 감지하거나 '타임랩스'를 누르면 TimeLine 화면에서 바로 재생
                    var replayOnOpen by remember { mutableStateOf(false) }
                    // 앱이 한 말은 말풍선에 12초만 보이고 다시 '목적지를 말해 보세요'로 돌아간다.
                    var bubble by remember { mutableStateOf("") }
                    androidx.compose.runtime.LaunchedEffect(plan.messageSeq) {
                        if (plan.messageSeq == 0) { bubble = ""; return@LaunchedEffect }
                        bubble = plan.message
                        kotlinx.coroutines.delay(12_000)
                        bubble = ""
                    }
                    // 경계 데이터를 다 읽어 기록기가 생기면 화면용 위치를 켠다.
                    androidx.compose.runtime.LaunchedEffect(loaded) {
                        if (loaded && locationAllowed.value) app.tripRecorder?.watch(true)
                    }
                    val simLine: String? = if (drive.running) {
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
                            }
                    val simWaiting = drive.running && plan.target != null && plan.phase != PlanPhase.GUIDING
                    // 시작 화면(앱을 켤 때 한 번)
                    var started by rememberSaveable { mutableStateOf(false) }
                    // 새로 받은 스티커 팝업
                    var award by remember { mutableStateOf<List<com.carnect.roadcanvas.sticker.StickerDef>>(emptyList()) }
                    androidx.compose.runtime.LaunchedEffect(recorder) {
                        recorder?.newStickers?.collect { award = award + it }
                    }
                    val toPlace = { sp: com.carnect.roadcanvas.data.Spot -> com.carnect.roadcanvas.plan.Place(sp.name, sp.lat, sp.lon, sp.sggCode) }
                    if (!started) {
                        com.carnect.roadcanvas.ui.home.StartScreen(onStart = { started = true })
                        return@Surface
                    }
                    when (current) {
                        Screen.STATS -> {
                            val trips by recorder!!.tripList.collectAsStateWithLifecycle()
                            com.carnect.roadcanvas.ui.home.StatsScreen(indexes!!, trip, trips, onClose = home)
                        }
                        Screen.PLANNER -> PlannerScreen(
                            plan, listening, partial, sttStatus,
                            onMic = { if (listening) app.stt.cancel() else startListening() },
                            onSay = { planner!!.hear(it) },
                            onDepart = { planner!!.depart() },
                            onSkip = { planner!!.skip() },
                            onClear = { planner!!.clear(announce = true) },
                            onClose = home,
                        )
                        Screen.QUEST -> QuestScreen(
                            explorer!!, trip,
                            onGo = { p ->
                                planner!!.clear()
                                planner.add(p)
                                screen = Screen.HOME
                            },
                            onAdd = { p -> planner!!.add(p) },
                            onCourse = { name, course ->
                                planner!!.planCourse(course.map { sp -> com.carnect.roadcanvas.plan.Place(sp.name, sp.lat, sp.lon, sp.sggCode) }, name)
                                screen = Screen.HOME
                            },
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
                                autoRecord = autoRecord,
                                autoEndMin = app.autoEndMs / 60_000,
                                onAutoRecord = { on ->
                                    if (on && checkSelfPermission(RoadCanvasApp.FUSED_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                        autoPermission.launch(RoadCanvasApp.FUSED_LOCATION)
                                    } else {
                                        app.setAutoRecord(on)
                                    }
                                },
                                themeMode = themeMode,
                                onThemeMode = { m ->
                                    themeMode = m
                                    prefs.edit().putString("theme_mode", m.name).apply()
                                },
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
                            onWeekly = app.demoData?.let { demo ->
                                {
                                    demoStatus = "매주 여행 만드는 중…"
                                    scope.launch {
                                        val n = demo.generateWeekly(java.time.LocalDate.now().year) { i, total -> demoStatus = "매주 여행 만드는 중… ($i/$total)" }
                                        demoStatus = if (n > 0) "올해 매주 여행 ${n}개를 만들었어요. 메인의 여행 히스토리에서 확인하세요." else "기록 중에는 만들 수 없어요."
                                    }
                                }
                            },
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
                        Screen.COLLECTION -> com.carnect.roadcanvas.ui.home.CollectionScreen(indexes!!, trip, onClose = home)
                        Screen.DASHBOARD -> HomeScreen(
                            indexes = regions,
                            trip = trip,
                            parked = parked,
                            driving = vehicle.gear != null && !parked,
                            onToggleTrip = { toggleTrip(trip.recording) },
                            onFeature = { f -> screen = featureScreen(f, fromMain = false) },
                            voiceText = if (listening) partial else bubble,
                            listening = listening,
                            onVoice = { if (listening) app.stt.cancel() else startListening() },
                            simLine = simLine,
                            simWaiting = simWaiting,
                            onSimDepart = { planner?.depart() },
                            onSimStop = { testDrive?.stop() },
                            onTestDrive = { screen = Screen.TEST },
                            decorations = homeDecos,
                            onDecorate = { screen = Screen.DECORATE },
                            replayOnOpen = replayOnOpen,
                            onBack = { replayOnOpen = false; screen = Screen.HOME },
                        )
                        Screen.HOME -> com.carnect.roadcanvas.ui.home.MainScreen(
                            indexes = regions,
                            trip = trip,
                            plan = plan,
                            parked = parked,
                            driving = vehicle.gear != null && !parked,
                            onToggleTrip = { toggleTrip(trip.recording) },
                            onFeature = { f ->
                                if (f == Feature.ROUTE_REPLAY) replayOnOpen = true
                                screen = featureScreen(f, fromMain = true)
                            },
                            onDepart = { planner?.depart() },
                            onParkedAfterDrive = { replayOnOpen = true; screen = Screen.DASHBOARD },
                            voiceText = if (listening) partial else bubble,
                            listening = listening,
                            onVoice = { if (listening) app.stt.cancel() else startListening() },
                            simLine = simLine,
                            simWaiting = simWaiting,
                            onSimDepart = { planner?.depart() },
                            onSimStop = { testDrive?.stop() },
                            onTestDrive = { screen = Screen.TEST },
                            decorations = homeDecos,
                            onDecorate = { screen = Screen.DECORATE },
                            locationAllowed = locationAllowed.value,
                            onAllowLocation = { watchPermission.launch(RoadCanvasApp.FUSED_LOCATION) },
                            spots = app.spots,
                            onGoSpot = { sp ->
                                planner?.clear()
                                planner?.add(toPlace(sp))
                            },
                            onCourse = { name, course -> planner?.planCourse(course.map(toPlace), name) },
                            onStats = { screen = Screen.STATS },
                            trips = mainTrips,
                            loadPeriod = { ids -> recorder?.loadPeriod(ids) },
                            loadArea = { ids -> recorder?.loadArea(ids) },
                        )
                    }
                    // 정차 중에만 팝업(주행 중엔 음성 안내만)
                    if (award.isNotEmpty() && parked) {
                        com.carnect.roadcanvas.ui.home.StickerAwardDialog(award, onDismiss = { award = emptyList() })
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

    private enum class Screen { HOME, STATS, DASHBOARD, COLLECTION, PLANNER, QUEST, DETOUR, TRIPS, SETTINGS, TEST, DECORATE }

    /** 런처 타일 → 화면. 메인의 TimeLine은 여행 대시보드, 대시보드의 '기록 보기'는 여행 목록. */
    private fun featureScreen(f: Feature, fromMain: Boolean): Screen = when (f) {
        Feature.SETTINGS -> Screen.SETTINGS
        Feature.VOICE_PLANNER -> Screen.PLANNER
        Feature.CANVAS_QUEST -> Screen.QUEST
        Feature.SMART_DETOUR -> Screen.DETOUR
        Feature.COLLECTION -> Screen.COLLECTION
        Feature.TIMELINE -> if (fromMain) Screen.DASHBOARD else Screen.TRIPS
        Feature.ROUTE_REPLAY -> Screen.DASHBOARD
        Feature.AUTO_CANVAS -> Screen.HOME
    }

    private companion object {
        const val RECORD_AUDIO = android.Manifest.permission.RECORD_AUDIO
        const val STT_SERVICE = "pleos.car.permission.STT_SERVICE"
    }
}
