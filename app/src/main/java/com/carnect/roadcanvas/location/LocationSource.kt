package com.carnect.roadcanvas.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG

/**
 * Pleos Fused Location 구독. SDK가 서비스 연결 완료를 알려 주지 않고 연결 전 등록은
 * 실패로 돌려주므로, 등록이 성공할 때까지 잠시 간격을 두고 다시 시도한다.
 */
class LocationSource(context: Context, private val onLocation: (LocationData) -> Unit) {

    private val fused = FusedLocation(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private var registered = false
    private var attempts = 0

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            registered = true
            location?.let(onLocation)
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "location error: ${exception.message}")
            if (active && !registered) scheduleRetry()
        }
    }

    private val retry = Runnable { register() }

    fun start() {
        if (active) return
        active = true
        registered = false
        attempts = 0
        fused.initialize()
        main.postDelayed(retry, RETRY_MS)
    }

    fun stop() {
        if (!active) return
        active = false
        main.removeCallbacks(retry)
        try {
            fused.unregisterFusedLocationCallback(listener)
        } catch (e: Exception) {
            Log.w(TAG, "location unregister failed", e)
        }
        fused.release()
    }

    @SuppressLint("MissingPermission") // 위치 권한은 기록 시작 전에 화면에서 받는다.
    private fun register() {
        if (!active) return
        attempts++
        try {
            fused.registerFusedLocationCallback(listener)
            Log.i(TAG, "location register requested (attempt $attempts)")
        } catch (e: Exception) {
            Log.w(TAG, "location register failed: ${e.message}")
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (attempts >= MAX_ATTEMPTS) {
            Log.w(TAG, "location register gave up after $attempts attempts")
            return
        }
        main.removeCallbacks(retry)
        main.postDelayed(retry, RETRY_MS)
    }

    private companion object {
        const val RETRY_MS = 1_000L
        const val MAX_ATTEMPTS = 30
    }
}
