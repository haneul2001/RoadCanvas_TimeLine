package com.carnect.roadcanvas

import android.app.Application
import android.content.pm.PackageManager
import com.carnect.roadcanvas.plan.Explorer
import com.carnect.roadcanvas.plan.Places
import com.carnect.roadcanvas.plan.TripPlanner
import com.carnect.roadcanvas.sim.TestDrive
import com.carnect.roadcanvas.trip.TripService
import com.carnect.roadcanvas.voice.SttEngine
import android.util.Log
import com.carnect.roadcanvas.navi.NaviRepo
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.vehicle.VehicleRepo
import com.carnect.roadcanvas.voice.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 프로세스 초기화. Pleos SDK 세션(Vehicle·NaviHelper·TTS)은 여기서 한 번만 연다. */
class RoadCanvasApp : Application() {

    private val scope = MainScope()

    lateinit var vehicleRepo: VehicleRepo
        private set
    lateinit var naviRepo: NaviRepo
        private set
    lateinit var tts: TtsEngine
        private set
    lateinit var stt: SttEngine
        private set

    private val _regions = MutableStateFlow<Map<Level, RegionIndex>?>(null)
    /** 단계별 경계 데이터(합쳐 5MB 가까이)는 백그라운드에서 읽는다. */
    val regions: StateFlow<Map<Level, RegionIndex>?> = _regions.asStateFlow()

    var tripRecorder: TripRecorder? = null
        private set
    var planner: TripPlanner? = null
        private set
    var explorer: Explorer? = null
        private set
    var testDrive: TestDrive? = null
        private set
    var demoData: com.carnect.roadcanvas.sim.DemoData? = null
        private set

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private val _autoRecord = MutableStateFlow(false)
    /** 자동 기록(설정에서 동의하고 켬): 기어 D가 되면 여행을 시작하고, 주차한 채 [autoEndMs]가 지나면 끝낸다. */
    val autoRecord: StateFlow<Boolean> = _autoRecord.asStateFlow()

    /** 자동 기록으로 시작한 여행인지(직접 '여행 시작'한 여행은 자동으로 끝내지 않는다). */
    private var autoStarted = false
    private var autoEndJob: kotlinx.coroutines.Job? = null

    /** 주차 후 자동 종료까지 기다리는 시간. 시험하기 쉽게 디버그 빌드는 1분. */
    val autoEndMs: Long = if (BuildConfig.DEBUG) 60_000L else 30 * 60_000L

    private fun locationGranted() = checkSelfPermission(FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun setAutoRecord(on: Boolean) {
        _autoRecord.value = on
        prefs.edit().putBoolean("auto_record", on).apply()
        Log.i(TAG, "auto record $on")
        if (on) {
            if (locationGranted()) TripService.standby(this)
            onGear(vehicleRepo.state.value)
        } else {
            autoEndJob?.cancel()
            autoStarted = false
            if (tripRecorder?.state?.value?.recording != true) TripService.shutdown(this)
        }
    }

    /** 기어가 바뀔 때: 자동 기록이면 주행 시작에 기록을 켜고, 주차가 길어지면 끈다. */
    private fun onGear(v: com.carnect.roadcanvas.vehicle.VehicleState) {
        if (!_autoRecord.value || v.gear == null) return
        val recorder = tripRecorder ?: return
        val recording = recorder.state.value.recording
        if (!v.parked) {
            autoEndJob?.cancel()
            autoEndJob = null
            if (!recording && locationGranted() && testDrive?.state?.value?.running != true) {
                autoStarted = true
                Log.i(TAG, "auto record: start (gear ${v.gear})")
                TripService.start(this)
            }
        } else if (recording && autoStarted && autoEndJob == null) {
            autoEndJob = scope.launch {
                kotlinx.coroutines.delay(autoEndMs)
                if (vehicleRepo.state.value.parked && tripRecorder?.state?.value?.recording == true) {
                    Log.i(TAG, "auto record: end after parking ${autoEndMs / 1000}s")
                    autoStarted = false
                    TripService.stop(this@RoadCanvasApp)
                }
                autoEndJob = null
            }
        }
    }

    /** 시·군·구별 추천 여행지와 코스. */
    var spots: com.carnect.roadcanvas.data.Spots = com.carnect.roadcanvas.data.Spots.EMPTY
        private set

    /** 지도 꾸미기(낙서·도형·스티커). */
    val decor: com.carnect.roadcanvas.deco.DecorRepo by lazy { com.carnect.roadcanvas.deco.DecorRepo(this) }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "RoadCanvasApp started")
        vehicleRepo = VehicleRepo(this).also { it.start() }
        naviRepo = NaviRepo(this).also { it.start() }
        tts = TtsEngine(this).also { it.start() }
        stt = SttEngine(this).also { it.start() }

        scope.launch {
            val indexes = withContext(Dispatchers.IO) {
                Level.entries.associateWith { level -> assets.open(level.asset).use { RegionIndex.load(level, it) } }
            }
            val recorder = TripRecorder(this@RoadCanvasApp, indexes, tts)
            tripRecorder = recorder
            explorer = Explorer(indexes.getValue(Level.SGG))
            val places = withContext(Dispatchers.Default) { Places(indexes) }
            val plan = TripPlanner(
                places, naviRepo, tts,
                position = { recorder.state.value.track.lastOrNull()?.let { it.lat to it.lon } },
                ensureRecording = ::ensureRecording,
            )
            planner = plan
            spots = withContext(Dispatchers.IO) {
                runCatching { assets.open("spots.json").use(com.carnect.roadcanvas.data.Spots::load) }
                    .onFailure { Log.w(TAG, "spots load failed", it) }
                    .getOrDefault(com.carnect.roadcanvas.data.Spots.EMPTY)
            }
            recorder.spots = spots
            plan.spots = spots
            plan.onPlanComplete = { recorder.award(listOf("complete")) }
            Log.i(TAG, "spots loaded: ${spots.all.size}")
            // 지도 꾸미기에 처음 무언가 붙이면 '추억 저장' 업적
            launch { decor.items.collect { if (it.isNotEmpty()) recorder.award(listOf("memory")) } }
            val road = withContext(Dispatchers.IO) { assets.open("city_roads.json").use(com.carnect.roadcanvas.sim.RoadBook::load) }
            testDrive = TestDrive(this, recorder, plan, road, ::ensureRecording) { TripService.stop(this@RoadCanvasApp) }
            demoData = com.carnect.roadcanvas.sim.DemoData(recorder, road)
            // 내비 도착 이벤트와 차 위치를 계획에 넘긴다(에뮬레이터 내비는 GPS 주입으로 움직이지 않아 위치 판정이 주로 쓰인다).
            launch { naviRepo.events.collect(plan::onNavi) }
            // 테스트 주행 중에는 내비 화면 속 차가 움직이지 않으므로, 경로가 잡히면 지도가 칠해지는 우리 화면을 다시 앞으로 가져온다.
            launch {
                naviRepo.events.collect { e ->
                    val routed = e is com.carnect.roadcanvas.navi.NaviEvent.RouteStarted || e is com.carnect.roadcanvas.navi.NaviEvent.Error
                    if (routed && testDrive?.state?.value?.running == true) {
                        kotlinx.coroutines.delay(1_500)
                        bringToFront()
                    }
                }
            }
            launch { recorder.state.collect { s -> s.track.lastOrNull()?.takeIf { s.recording }?.let { plan.onLocation(it.lat, it.lon) } } }
            // 자동 기록: 저장된 설정을 읽고, 켜져 있으면 대기 서비스를 띄워 기어 변화를 지켜본다.
            _autoRecord.value = prefs.getBoolean("auto_record", false)
            if (_autoRecord.value && locationGranted()) TripService.standby(this@RoadCanvasApp)
            launch { vehicleRepo.state.collect { onGear(it) } }
            _regions.value = indexes
            Log.i(TAG, "regions loaded: " + indexes.values.joinToString { "${it.level.label} ${it.regions.size}" })
        }
    }

    private fun bringToFront() {
        val intent = android.content.Intent(this, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "bring to front failed", it) }
        Log.i(TAG, "test drive: app brought to front")
    }

    /** 길 안내·테스트 주행을 시작할 때 여행 기록을 켠다(위치 권한이 있을 때만). */
    private fun ensureRecording() {
        val recorder = tripRecorder ?: return
        val granted = checkSelfPermission(FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted && !recorder.state.value.recording) TripService.start(this)
    }

    companion object {
        const val TAG = "RoadCanvas"
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
    }
}
