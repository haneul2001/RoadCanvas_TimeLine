package com.carnect.roadcanvas

import android.app.Application
import android.util.Log
import com.carnect.roadcanvas.navi.NaviRepo
import com.carnect.roadcanvas.vehicle.VehicleRepo
import com.carnect.roadcanvas.voice.TtsEngine

/** 프로세스 초기화. Pleos SDK 세션(Vehicle·NaviHelper·TTS)은 여기서 한 번만 연다. */
class RoadCanvasApp : Application() {

    lateinit var vehicleRepo: VehicleRepo
        private set
    lateinit var naviRepo: NaviRepo
        private set
    lateinit var tts: TtsEngine
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "RoadCanvasApp started")
        vehicleRepo = VehicleRepo(this).also { it.start() }
        naviRepo = NaviRepo(this).also { it.start() }
        tts = TtsEngine(this).also { it.start() }
    }

    companion object {
        const val TAG = "RoadCanvas"
    }
}
