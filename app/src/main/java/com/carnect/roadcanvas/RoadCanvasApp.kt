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
            val road = withContext(Dispatchers.IO) { assets.open("demo_roads.json").use(TestDrive::loadRoad) }
            testDrive = TestDrive(this, recorder, plan, road, ::ensureRecording) { TripService.stop(this@RoadCanvasApp) }
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
