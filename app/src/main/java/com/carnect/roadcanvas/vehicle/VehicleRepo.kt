package com.carnect.roadcanvas.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VehicleState(
    val gear: VehicleGear? = null,
    val lastError: String? = null,
) {
    /** 화면 조작(지도 꾸미기·타임랩스)은 P단에서만 연다. */
    val parked: Boolean get() = gear == VehicleGear.GEAR_PARK
}

/** Vehicle SDK 구독 결과를 [state]로 내보낸다. */
class VehicleRepo(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)
    private var started = false

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onGear(gear)
        override fun onFailed(e: Exception) = onError("gear listener", e)
    }

    fun start() {
        if (started) return
        try {
            // initialize() 직후의 요청은 서비스 연결 뒤 SDK가 순서대로 처리한다.
            vehicle.initialize()
            started = true
        } catch (e: Exception) {
            onError("initialize", e)
            return
        }
        vehicle.getGear().run {
            getCurrentGearSelectionState(::onGear) { onError("gear get", it) }
            registerGearSelection(gearListener)
        }
    }

    fun stop() {
        if (!started) return
        try {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.release()
        } catch (e: Exception) {
            onError("release", e)
        }
        started = false
    }

    private fun onGear(gear: VehicleGear?) {
        Log.d(TAG, "gear=$gear")
        _state.update { it.copy(gear = gear) }
    }

    private fun onError(where: String, e: Exception) {
        Log.w(TAG, "$where failed", e)
        _state.update { it.copy(lastError = "$where: ${e.message ?: e.javaClass.simpleName}") }
    }
}
