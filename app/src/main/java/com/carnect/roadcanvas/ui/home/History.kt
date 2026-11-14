package com.carnect.roadcanvas.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.trip.Trip
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.trip.TripState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Trip.date(): LocalDate = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()

/** 끝난 여행 중 [year]년(그리고 [month]월) 것. */
fun List<Trip>.inPeriod(year: Int, month: Int? = null): List<Trip> =
    filter { it.endMs != null && it.date().year == year && (month == null || it.date().monthValue == month) }.sortedBy { it.startMs }

/** 메인 지도 위 '년 ▾ · 월 ▾ · 여행 히스토리' 줄. 고르는 목록에는 그 기간 여행 수를 함께 보여 준다. */
@Composable
fun DateToggles(
    trips: List<Trip>,
    year: Int,
    month: Int,
    historyOpen: Boolean,
    onYear: (Int) -> Unit,
    onMonth: (Int) -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val years = (trips.filter { it.endMs != null }.map { it.date().year } + LocalDate.now().year + year).distinct().sortedDescending()
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(Palette.Surface.copy(alpha = 0.96f))
            .border(1.dp, Palette.Line, RoundedCornerShape(24.dp)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Toggle("${year}년", years.map { y -> y to "${y}년  · 여행 ${trips.inPeriod(y).size}번" }, onYear)
        Toggle("${month}월", (1..12).map { m -> m to "${m}월  · 여행 ${trips.inPeriod(year, m).size}번" }, onMonth)
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(if (historyOpen) Palette.Primary else Color.Transparent)
                .clickable(onClick = onHistory).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("📜 여행 히스토리", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (historyOpen) Color.White else Palette.Ink)
        }
    }
}

@Composable
private fun <T> Toggle(label: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            Text("  ▾", fontSize = 16.sp, color = Palette.Muted)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(Palette.Surface)) {
            options.forEach { (v, text) ->
                DropdownMenuItem(text = { Text(text, fontSize = 18.sp, color = Palette.Ink) }, onClick = { open = false; onPick(v) })
            }
        }
    }
}

/**
 * 여행 히스토리 패널: 고른 달의 여행 목록(날짜·거리·새 지역)과 요약,
 * 그 달·그 해 타임랩스, 여행 하나씩 다시 보기, 닫기.
 */
@Composable
fun HistoryPanel(
    trips: List<Trip>,
    all: TripState,
    year: Int,
    month: Int,
    onPlayMonth: () -> Unit,
    onPlayYear: () -> Unit,
    onPlayTrip: (Trip) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthTrips = trips.inPeriod(year, month)
    val yearTrips = trips.inPeriod(year)
    val newBy = all.visitsAt(Level.SGG).values.groupingBy { it.tripId }.eachCount()
    val km = monthTrips.sumOf { it.distanceM } / 1000
    Column(
        modifier.width(520.dp).clip(RoundedCornerShape(28.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Line, RoundedCornerShape(28.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${year}년 ${month}월 여행", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text(
                    "여행 ${monthTrips.size}번 · ${TripRecorder.formatKm(km)} · 새 시·군·구 ${monthTrips.sumOf { newBy[it.id] ?: 0 }}곳",
                    fontSize = 17.sp, color = Palette.Muted,
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Palette.Background).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 20.sp, color = Palette.Muted) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayButton("▶ ${month}월 타임랩스", monthTrips.isNotEmpty(), Palette.Primary, onPlayMonth, Modifier.weight(1f))
            PlayButton("▶ ${year}년 타임랩스", yearTrips.isNotEmpty(), Palette.Teal, onPlayYear, Modifier.weight(1f))
        }
        if (monthTrips.isEmpty()) {
            Text("이 달은 여행 기록이 없어요. 위에서 다른 달을 골라 보세요.", fontSize = 18.sp, color = Palette.Muted)
            return@Column
        }
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(monthTrips.reversed(), key = { it.id }) { t ->
                val d = t.date()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Background)
                        .clickable { onPlayTrip(t) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Palette.PrimarySoft), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${d.dayOfMonth}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Primary)
                            Text(dayName(d), fontSize = 12.sp, color = Palette.Primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            all.events.firstOrNull { it.tripId == t.id && it.kind == com.carnect.roadcanvas.trip.EventKind.START }?.detail ?: "${d.monthValue}월 ${d.dayOfMonth}일 여행",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1,
                        )
                        Text("${TripRecorder.formatKm(t.distanceM / 1000)} · 새 시·군·구 ${newBy[t.id] ?: 0}곳", fontSize = 15.sp, color = Palette.Muted)
                    }
                    Text("▶", fontSize = 20.sp, color = Palette.Primary)
                }
            }
        }
    }
}

private fun dayName(d: LocalDate) = "월화수목금토일"[d.dayOfWeek.value - 1].toString()

@Composable
private fun PlayButton(text: String, enabled: Boolean, color: Color, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(27.dp)).background(if (enabled) color else Palette.Unvisited)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else Palette.Muted) }
}

/** 기간 타임랩스 막대: 제목 · 지금 재생 중인 여행 날짜 · 누적 거리 · 지금 지역 · 진행 · 멈춤. */
@Composable
fun PeriodReplayBar(
    title: String,
    replay: Replay,
    frame: TripState,
    progress: Float,
    dateOf: (Long) -> String,
    onStop: () -> Unit,
    modifier: Modifier,
) {
    val time = replay.timeAt(progress)
    Row(
        modifier.width(620.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xE6162338))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color.White).clickable(onClick = onStop),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(14.dp).background(Color(0xFF16233A))) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$title · ${dateOf(time)} · ${TripRecorder.formatKm(replay.distanceAt(time) / 1000)}",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
            )
            Text(frame.current[Level.SGG]?.let { "${it.province} ${it.name}" } ?: "출발 전", fontSize = 15.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = Palette.Teal,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            )
        }
    }
}
