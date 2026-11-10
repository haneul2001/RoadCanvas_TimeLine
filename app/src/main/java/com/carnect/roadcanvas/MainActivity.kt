package com.carnect.roadcanvas

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carnect.roadcanvas.trip.TripService
import com.carnect.roadcanvas.ui.CanvasScreen
import com.carnect.roadcanvas.ui.StatusScreen

class MainActivity : ComponentActivity() {

    private val app get() = application as RoadCanvasApp

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) TripService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    val regions by app.regions.collectAsStateWithLifecycle()
                    val vehicle by app.vehicleRepo.state.collectAsStateWithLifecycle()
                    Column {
                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("여행 지도") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("연결 상태") })
                        }
                        when (tab) {
                            0 -> CanvasScreen(regions, app.tripRecorder, vehicle.parked, ::toggleRecording)
                            else -> StatusScreen(app.vehicleRepo, app.naviRepo, app.tts)
                        }
                    }
                }
            }
        }
    }

    /** 위치 기록은 사용자가 시작을 누르고 위치 권한을 허용한 뒤에만 한다. */
    private fun toggleRecording(start: Boolean) {
        if (!start) {
            TripService.stop(this)
            return
        }
        if (checkSelfPermission(FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            TripService.start(this)
        } else {
            locationPermission.launch(FUSED_LOCATION)
        }
    }

    private companion object {
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
    }
}
