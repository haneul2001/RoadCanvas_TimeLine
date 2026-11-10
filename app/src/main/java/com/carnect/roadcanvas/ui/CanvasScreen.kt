package com.carnect.roadcanvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.trip.VisitState
import kotlin.math.cos
import kotlin.math.min

private val Unvisited = Color(0xFFE9E6DF)
private val Passed = Color(0xFFF2C38B)
private val StayCandidate = Color(0xFFE8894A)
private val Confirmed = Color(0xFFC4532A)
private val Border = Color(0xFFFFFFFF)
private val TrackColor = Color(0xFF2D4F8B)
private val Sea = Color(0xFFF6F4EF)

/** 한반도 남쪽(제주 포함)을 담는 범위. */
private const val MIN_LON = 124.5
private const val MAX_LON = 131.0
private const val MIN_LAT = 33.0
private const val MAX_LAT = 38.7
private val LON_SCALE = cos(Math.toRadians((MIN_LAT + MAX_LAT) / 2))

/** 위경도 → 화면 좌표. 경도는 위도에 맞춰 줄여 모양이 눌리지 않게 한다. */
private class Projection(size: Size) {
    private val w = (MAX_LON - MIN_LON) * LON_SCALE
    private val h = MAX_LAT - MIN_LAT
    private val scale = min(size.width / w, size.height / h)
    private val offsetX = (size.width - w * scale) / 2
    private val offsetY = (size.height - h * scale) / 2

    fun x(lon: Double) = (offsetX + (lon - MIN_LON) * LON_SCALE * scale).toFloat()
    fun y(lat: Double) = (offsetY + (MAX_LAT - lat) * scale).toFloat()
}

@Composable
fun CanvasScreen(
    index: RegionIndex?,
    recorder: TripRecorder?,
    parked: Boolean,
    onToggleRecording: (Boolean) -> Unit,
) {
    if (index == null || recorder == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("지도를 준비하고 있어요") }
        return
    }
    val trip by recorder.state.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxSize().background(Sea)) {
        MapCanvas(index, trip.visits, trip.track.map { it.lat to it.lon }, Modifier.weight(1f).fillMaxHeight())
        SidePanel(
            index = index,
            visits = trip.visits,
            recording = trip.recording,
            currentRegion = trip.currentRegion,
            parked = parked,
            onToggleRecording = onToggleRecording,
            modifier = Modifier.width(420.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun MapCanvas(
    index: RegionIndex,
    visits: Map<String, VisitState>,
    track: List<Pair<Double, Double>>,
    modifier: Modifier,
) {
    // 경계 Path는 화면 크기가 바뀔 때만 다시 만든다.
    val cache = remember(index) { mutableMapOf<Size, List<Pair<String, Path>>>() }
    Canvas(modifier.padding(24.dp)) {
        val projection = Projection(size)
        val paths = cache.getOrPut(size) {
            index.regions.map { region ->
                val path = Path()
                region.rings.forEach { ring ->
                    path.moveTo(projection.x(ring[0]), projection.y(ring[1]))
                    for (i in 2 until ring.size step 2) path.lineTo(projection.x(ring[i]), projection.y(ring[i + 1]))
                    path.close()
                }
                region.code to path
            }
        }
        paths.forEach { (code, path) ->
            val fill = when (visits[code]) {
                VisitState.PASSED -> Passed
                VisitState.STAY_CANDIDATE -> StayCandidate
                VisitState.CONFIRMED -> Confirmed
                null -> Unvisited
            }
            drawPath(path, fill)
            drawPath(path, Border, style = Stroke(width = 0.8f))
        }
        if (track.size >= 2) {
            val line = Path()
            line.moveTo(projection.x(track[0].second), projection.y(track[0].first))
            track.drop(1).forEach { (lat, lon) -> line.lineTo(projection.x(lon), projection.y(lat)) }
            drawPath(line, TrackColor, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val (lat, lon) = track.last()
            drawCircle(TrackColor, radius = 9f, center = Offset(projection.x(lon), projection.y(lat)))
            drawCircle(Color.White, radius = 4f, center = Offset(projection.x(lon), projection.y(lat)))
        }
    }
}

@Composable
private fun SidePanel(
    index: RegionIndex,
    visits: Map<String, VisitState>,
    recording: Boolean,
    currentRegion: String?,
    parked: Boolean,
    onToggleRecording: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val totals = remember(index) { index.countByProvince() }
    val provinceNames = remember(index) { index.regions.associate { it.provinceCode to it.province } }
    val visitedByProvince = visits.keys.groupingBy { it.take(2) }.eachCount()

    Column(
        modifier.background(Color.White).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("RoadCanvas : TimeLine", style = MaterialTheme.typography.titleLarge)
        Text(
            "대한민국 ${visits.size} / ${index.regions.size}",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(currentRegion?.let { "지금 $it" } ?: "현재 위치 없음", style = MaterialTheme.typography.bodyLarge)

        Button(
            onClick = { onToggleRecording(!recording) },
            colors = if (recording) ButtonDefaults.buttonColors(containerColor = Confirmed) else ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (recording) "여행 기록 멈추기" else "여행 기록 시작") }
        if (!parked) {
            Text("주행 중에는 지도 꾸미기가 잠겨요", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Legend()
        Spacer(Modifier.height(4.dp))
        Text("시·도별 탐험", style = MaterialTheme.typography.titleMedium)
        // 많이 다닌 시·도부터
        totals.keys.sortedByDescending { (visitedByProvince[it] ?: 0).toFloat() / totals.getValue(it) }.take(8).forEach { code ->
            val done = visitedByProvince[code] ?: 0
            val total = totals.getValue(code)
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(provinceNames[code] ?: code, style = MaterialTheme.typography.bodyMedium)
                    Text("$done / $total", style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(
                    progress = { done.toFloat() / total },
                    color = StayCandidate,
                    trackColor = Unvisited,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Passed to "통과", StayCandidate to "머문 곳", Confirmed to "방문 확정").forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(color, RoundedCornerShape(3.dp)))
                Text(" $label", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
