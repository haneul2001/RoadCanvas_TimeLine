package com.carnect.roadcanvas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carnect.roadcanvas.navi.NaviRepo
import com.carnect.roadcanvas.vehicle.VehicleRepo
import com.carnect.roadcanvas.voice.TtsEngine

/** SDK 연결 상태를 보여 주는 개발용 첫 화면. */
@Composable
fun StatusScreen(vehicleRepo: VehicleRepo, naviRepo: NaviRepo, tts: TtsEngine) {
    val vehicle by vehicleRepo.state.collectAsStateWithLifecycle()
    val navi by naviRepo.state.collectAsStateWithLifecycle()
    val ttsStatus by tts.status.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("RoadCanvas : TimeLine", style = MaterialTheme.typography.headlineMedium)
        ValueRow("기어", vehicle.gear?.name)
        ValueRow("주차 중", if (vehicle.parked) "예" else "아니요")
        ValueRow("내비 연결", if (navi.ready) "됨" else "안 됨")
        ValueRow("내비 상태", navi.status?.name)
        ValueRow("TTS", ttsStatus)
        vehicle.lastError?.let { Text("오류: $it", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ValueRow(label: String, value: String?) {
    Row {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(200.dp))
        Text(value ?: "—", style = MaterialTheme.typography.titleLarge)
    }
}
