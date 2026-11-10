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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carnect.roadcanvas.trip.TripService
import com.carnect.roadcanvas.trip.TripState
import com.carnect.roadcanvas.ui.StatusScreen
import com.carnect.roadcanvas.ui.home.Feature
import com.carnect.roadcanvas.ui.home.HomeScreen
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val app get() = application as RoadCanvasApp

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) TripService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val empty = MutableStateFlow(TripState())
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var settings by rememberSaveable { mutableStateOf(false) }
                    val regions by app.regions.collectAsStateWithLifecycle()
                    val vehicle by app.vehicleRepo.state.collectAsStateWithLifecycle()
                    // 경계 데이터를 읽은 뒤에 기록기가 생긴다.
                    val tripFlow = (if (regions != null) app.tripRecorder?.state else null) ?: empty
                    val trip by tripFlow.collectAsStateWithLifecycle()

                    if (settings) {
                        BackHandler { settings = false }
                        Column {
                            OutlinedButton(onClick = { settings = false }, modifier = Modifier.padding(24.dp)) { Text("← 돌아가기") }
                            OutlinedButton(
                                onClick = { app.tripRecorder?.clear(); toast("여행 기록을 모두 지웠어요") },
                                enabled = !trip.recording,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            ) { Text("여행 기록 모두 지우기") }
                            StatusScreen(app.vehicleRepo, app.naviRepo, app.tts)
                        }
                    } else {
                        HomeScreen(
                            indexes = regions,
                            trip = trip,
                            parked = vehicle.parked,
                            onToggleTrip = { toggleTrip(trip.recording) },
                            onFeature = { f ->
                                when {
                                    f == Feature.SETTINGS -> settings = true
                                    !f.ready -> toast("${f.title}은 준비 중이에요")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    /** 위치 기록은 사용자가 시작을 누르고 위치 권한을 허용한 뒤에만 한다. */
    private fun toggleTrip(recording: Boolean) {
        if (recording) {
            TripService.stop(this)
            return
        }
        if (app.tripRecorder == null) return
        if (checkSelfPermission(FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            TripService.start(this)
        } else {
            locationPermission.launch(FUSED_LOCATION)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private companion object {
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
    }
}
