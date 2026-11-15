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
    /** 시스템 주행 UX 제한(CarUxRestrictions)이 걸렸는지. 확인 못 하면 null. */
    val uxRestricted: Boolean? = null,
) {
    /** 기어가 P인지(자동 기록·주차 후 타임랩스 판단용). */
    val atPark: Boolean get() = gear == VehicleGear.GEAR_PARK

    /** 기어가 P가 아닌 주행 기어인지. 기어를 모르면 false. */
    val driving: Boolean get() = gear != null && gear != VehicleGear.GEAR_PARK

    /**
     * 화면 조작(지도 꾸미기·타임랩스·상세 목록)을 열어도 되는지. 차량 시스템의 주행 제한(CarUxRestrictions)을
     * 따른다. 시스템이 주행 중에도 제한하지 않는 차(동승자 화면 등)면 열고, 제한하면 음성만 쓴다.
     * 시스템 신호를 받을 수 없으면 기어 P일 때만 연다.
     */
    val screenOpen: Boolean get() = uxRestricted?.let { !it } ?: atPark
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

    private val appContext = context.applicationContext
    private var car: android.car.Car? = null

    /**
     * Pleos 주행 규제 가이드대로 CarUxRestrictions도 따른다. 주의 분산 최적화가 필요한 상태(주행 중)면
     * 기어와 상관없이 상세 화면을 닫는다. android.car가 없는 환경이면 기어만 본다.
     */
    private fun watchUxRestrictions() {
        try {
            val c = android.car.Car.createCar(appContext) ?: return
            car = c
            val ux = c.getCarManager(android.car.Car.CAR_UX_RESTRICTION_SERVICE) as? android.car.drivingstate.CarUxRestrictionsManager ?: return
            fun apply(r: android.car.drivingstate.CarUxRestrictions?) {
                val restricted = r?.isRequiresDistractionOptimization
                Log.d(TAG, "ux restricted=$restricted")
                _state.update { it.copy(uxRestricted = restricted) }
            }
            apply(ux.currentCarUxRestrictions)
            ux.registerListener { apply(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "CarUxRestrictions unavailable: ${t.message}")
        }
    }

    fun start() {
        if (started) return
        watchUxRestrictions()
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
