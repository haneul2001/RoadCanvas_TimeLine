package com.carnect.roadcanvas

import android.app.Application
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

    private val _regions = MutableStateFlow<Map<Level, RegionIndex>?>(null)
    /** 단계별 경계 데이터(합쳐 5MB 가까이)는 백그라운드에서 읽는다. */
    val regions: StateFlow<Map<Level, RegionIndex>?> = _regions.asStateFlow()

    var tripRecorder: TripRecorder? = null
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "RoadCanvasApp started")
        vehicleRepo = VehicleRepo(this).also { it.start() }
        naviRepo = NaviRepo(this).also { it.start() }
        tts = TtsEngine(this).also { it.start() }

        scope.launch {
            val indexes = withContext(Dispatchers.IO) {
                Level.entries.associateWith { level -> assets.open(level.asset).use { RegionIndex.load(level, it) } }
            }
            tripRecorder = TripRecorder(this@RoadCanvasApp, indexes, tts)
            _regions.value = indexes
            Log.i(TAG, "regions loaded: " + indexes.values.joinToString { "${it.level.label} ${it.regions.size}" })
        }
    }

    companion object {
        const val TAG = "RoadCanvas"
    }
}
