package com.carnect.roadcanvas.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NaviState(
    val ready: Boolean = false,
    val status: NavigationStatus? = null,
)

/** 다구간 여행에 쓰는 내비 이벤트. */
sealed interface NaviEvent {
    data class RouteStarted(val name: String, val distanceM: Int, val durationSec: Int) : NaviEvent
    data class WaypointArrived(val name: String) : NaviEvent
    data class DestinationArrived(val name: String) : NaviEvent
    data class Error(val code: NaviErrorCode) : NaviEvent
}

/**
 * 기본 내비(NaviHelper)를 감싼다. 경유지는 SDK가 최대 2곳까지만 받으므로,
 * 다구간 여행은 도착할 때마다 다음 목적지를 순서대로 요청한다.
 */
class NaviRepo(context: Context) {

    private val navi = NaviHelper(context.applicationContext)
    private var started = false

    private val _state = MutableStateFlow(NaviState())
    val state: StateFlow<NaviState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) =
            emit(NaviEvent.RouteStarted(info.name, info.distance, info.duration))

        override fun onWayPointArrived(info: WaypointArrivedInfo) = emit(NaviEvent.WaypointArrived(info.name))
        override fun onDestinationArrived(info: DestinationArrivedInfo) = emit(NaviEvent.DestinationArrived(info.name))
        override fun onError(code: NaviErrorCode) = emit(NaviEvent.Error(code))

        override fun onNavigationStatus(status: NavigationStatus) {
            Log.d(TAG, "navi status $status")
            _state.update { it.copy(status = status) }
        }
    }

    fun start() {
        if (started) return
        started = true
        navi.initialize(
            {
                Log.i(TAG, "NaviHelper initialized")
                navi.addListener(listener)
                _state.update { it.copy(ready = true) }
            },
            { e ->
                Log.w(TAG, "NaviHelper initialize failed", e)
                started = false
            },
        )
    }

    fun stop() {
        if (!started) return
        navi.removeListener(listener)
        navi.release()
        started = false
        _state.update { it.copy(ready = false) }
    }

    /** 다음 목적지로 경로를 요청한다. */
    fun requestRoute(name: String, latitude: Double, longitude: Double) = call("requestRoute") {
        navi.requestRoute(
            RouteInfo(latitude, longitude, null, null, name, "", "", "", RouteOption.RECOMMENDED, "", false)
        )
    }

    /** 지금 경로에 경유지를 하나 넣는다(SDK는 경유지를 최대 2곳까지만 받는다). */
    fun addWaypoint(name: String, latitude: Double, longitude: Double) = call("addWaypoint") {
        navi.addWaypoint(RequestWaypointInfo(latitude, longitude, name, WaypointIndex.FIRST, "", "", "", ""))
    }

    fun cancelRoute() = call("cancelRoute") { navi.cancelRoute() }

    private fun emit(event: NaviEvent) {
        Log.d(TAG, "navi $event")
        _events.tryEmit(event)
    }

    private inline fun call(name: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            Log.w(TAG, "navi $name failed", e)
        }
    }
}
