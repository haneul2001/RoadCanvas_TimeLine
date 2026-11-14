package com.carnect.roadcanvas.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.EventKind
import com.carnect.roadcanvas.trip.TimelineEvent
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.trip.TripState

/** 런처 타일. [ready]가 아니면 '준비 중'으로 안내한다. */
enum class Feature(val title: String, val caption: String, val ready: Boolean) {
    AUTO_CANVAS("Auto Canvas", "주행 경로 자동 색칠", true),
    CANVAS_QUEST("Canvas Quest", "미방문 지역 추천", true),
    SMART_DETOUR("Smart Detour", "최소 우회 탐험", true),
    ROUTE_REPLAY("Route Replay", "타임랩스 재생", true),
    TIMELINE("TimeLine", "여행 기록 타임라인", true),
    COLLECTION("Collection", "스티커 · 미션", true),
    VOICE_PLANNER("Voice Planner", "음성 다구간 여행 계획", true),
    SETTINGS("설정 · 개인정보", "", true),
}

/** 여행 전체를 재생하는 시간(여행 길이와 상관없이 일정하게). */
private const val REPLAY_MS = 14_000

@Composable
fun HomeScreen(
    indexes: Map<Level, RegionIndex>?,
    trip: TripState,
    parked: Boolean,
    /** 기어를 확실히 P가 아닌 단으로 놓았을 때(기어를 아직 모르면 false). */
    driving: Boolean,
    onToggleTrip: () -> Unit,
    onFeature: (Feature) -> Unit,
    /** 음성 말풍선에 보일 문장(들은 말 또는 앱이 한 말). */
    voiceText: String = "",
    listening: Boolean = false,
    /** 말풍선을 누르면 바로 듣는다(주행 중에도 음성은 쓸 수 있다). */
    onVoice: () -> Unit = {},
    /** 테스트 주행 중이면 상태 한 줄. */
    simLine: String? = null,
    /** 테스트 주행이 목적지에서 '출발'을 기다리는 중. */
    simWaiting: Boolean = false,
    onSimDepart: () -> Unit = {},
    onSimStop: () -> Unit = {},
    onTestDrive: () -> Unit = {},
    /** 지도에 붙인 꾸미기. */
    decorations: List<com.carnect.roadcanvas.deco.Decoration> = emptyList(),
    /** 지도 꾸미기 화면 열기(정차 중). */
    onDecorate: () -> Unit = {},
    /** 메인에서 주차를 감지해 넘어왔으면 바로 이번 여행 타임랩스를 튼다. */
    replayOnOpen: Boolean = false,
    /** 메인 화면으로 돌아가기. */
    onBack: (() -> Unit)? = null,
) {
    var showCollection by rememberSaveable { mutableStateOf(false) }
    if (showCollection && indexes != null) {
        androidx.activity.compose.BackHandler { showCollection = false }
        CollectionScreen(indexes, trip, onClose = { showCollection = false })
        return
    }
    // 색칠 기준. 기본은 시·군·구.
    var level by rememberSaveable { mutableStateOf(Level.SGG) }
    val index = indexes?.get(level)

    // 타임랩스: 시작할 때의 기록을 붙잡아 두고 진행률(0~1)에 따라 그 시점의 상태를 그린다.
    var replay by remember { mutableStateOf<Replay?>(null) }
    var replayRun by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    fun startReplay() {
        val r = indexes?.let { Replay(trip, it) }?.takeIf { it.playable } ?: return
        replay = r
        replayRun++
    }
    LaunchedEffect(replayRun) {
        if (replayRun == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(REPLAY_MS, easing = LinearEasing))
        delay(1_500)
        replay = null
    }
    LaunchedEffect(indexes != null) { if (replayOnOpen && indexes != null) startReplay() }
    // 주행하다 P로 바꾸면(주차) 이번 여행을 자동으로 재생한다.
    var wasDriving by remember { mutableStateOf(false) }
    LaunchedEffect(parked, driving) {
        if (parked && wasDriving) startReplay()
        wasDriving = driving
    }
    val frame = replay?.let { it.frameAt(it.timeAt(progress.value)) }
    val shown = frame ?: trip
    val onFeatureTap: (Feature) -> Unit = { f ->
        when (f) {
            Feature.ROUTE_REPLAY -> startReplay()
            Feature.COLLECTION -> showCollection = true
            else -> onFeature(f)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(onTestDrive, onBack)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(330.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VoiceBubble(voiceText, listening, onVoice)
                StatCards(indexes, level, shown, Modifier.weight(1f))
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Palette.Sea),
            ) {
                if (index != null) TripMap(index, trip, interactive = parked && replay == null, Modifier.fillMaxSize(), frame = frame, decorations = decorations)
                Column(Modifier.align(Alignment.TopStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LevelSelector(level, onSelect = { level = it }, Modifier)
                    if (parked && replay == null) {
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.95f))
                                .border(1.dp, Palette.Line, RoundedCornerShape(20.dp)).clickable(onClick = onDecorate)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Text("🎨  지도 꾸미기", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
                    }
                }
                val r = replay
                if (r != null && frame != null) {
                    ReplayBar(r, frame, progress.value, onStop = { replay = null }, Modifier.align(Alignment.BottomStart).padding(14.dp))
                } else {
                    if (simLine == null) MapLegend(Modifier.align(Alignment.BottomEnd).padding(14.dp))
                    if (simLine != null) SimBar(simLine, simWaiting, onSimDepart, onSimStop, Modifier.align(Alignment.BottomStart).padding(14.dp))
                }
            }
            TimelineCard(shown.eventsAt(level), Modifier.width(470.dp).fillMaxHeight())
        }
        ActionRow(trip.recording, parked, onToggleTrip, onFeatureTap)
        Launcher(parked, onFeatureTap)
    }
}

/** 타임랩스 재생 중 지도 아래에 띄우는 막대: 시각 · 거리 · 지금 지역 · 진행 · 멈춤. */
@Composable
private fun ReplayBar(replay: Replay, frame: TripState, progress: Float, onStop: () -> Unit, modifier: Modifier) {
    val time = replay.timeAt(progress)
    val day = java.time.temporal.ChronoUnit.DAYS.between(
        java.time.Instant.ofEpochMilli(replay.startMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
        java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
    ) + 1
    val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date(time))
    Row(
        modifier.width(560.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Ink.copy(alpha = 0.88f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color.White).clickable(onClick = onStop),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(14.dp).background(Palette.Ink)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "타임랩스 · Day $day $clock · ${TripRecorder.formatKm(replay.distanceAt(time) / 1000)}",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
            )
            Text(frame.current[Level.SGG]?.toString()?.substringBefore("(") ?: "출발 전", fontSize = 15.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
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

/** 테스트 주행 상태 막대: 지금 상태 · 출발 · 멈춤. */
@Composable
internal fun SimBar(line: String, waiting: Boolean, onDepart: () -> Unit, onStop: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Ink.copy(alpha = 0.88f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("테스트 주행", fontSize = 14.sp, color = Palette.Amber, fontWeight = FontWeight.Bold)
            Text(line, fontSize = 17.sp, color = Color.White, maxLines = 3, lineHeight = 22.sp)
        }
        if (waiting) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Primary).clickable(onClick = onDepart)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("출발", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).clickable(onClick = onStop)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) { Text("멈춤", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
    }
}

@Composable
internal fun Header(onTestDrive: () -> Unit, onBack: (() -> Unit)? = null, onStats: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandTitle()
        Spacer(Modifier.weight(1f))
        if (onBack != null) {
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(16.dp))
                    .clickable(onClick = onBack).padding(horizontal = 20.dp, vertical = 12.dp),
            ) { Text("← 메인", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Primary) }
            Spacer(Modifier.width(12.dp))
        }
        if (onStats != null) {
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(16.dp))
                    .clickable(onClick = onStats).padding(horizontal = 20.dp, vertical = 12.dp),
            ) { Text("📊  여행 통계", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Primary) }
            Spacer(Modifier.width(12.dp))
        }
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.OrangeSoft).border(1.dp, Palette.Orange, RoundedCornerShape(16.dp))
                .clickable(onClick = onTestDrive).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Palette.Orange, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(6.dp))
            Text("테스트 주행", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Orange)
        }
    }
}

@Composable
internal fun VoiceBubble(text: String, listening: Boolean, onVoice: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(if (listening) Palette.OrangeSoft else Palette.Card)
            .border(if (listening) 2.dp else 1.dp, if (listening) Palette.Orange else Palette.Line, RoundedCornerShape(30.dp))
            .clickable(onClick = onVoice).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicGlyph(if (listening) Palette.Orange else Palette.Primary, Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                listening && text.isBlank() -> "듣고 있어요…"
                text.isNotBlank() -> text
                else -> "“목적지를 말해 보세요”"
            },
            fontSize = 18.sp, color = if (text.isNotBlank() || listening) Palette.Ink else Palette.Muted, maxLines = 2,
        )
    }
}

/** 지도 위 색칠 기준 선택(시·도 / 시·군·구 / 읍·면·동). */
@Composable
internal fun LevelSelector(level: Level, onSelect: (Level) -> Unit, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.95f))
            .border(1.dp, Palette.Line, RoundedCornerShape(24.dp)).padding(4.dp),
    ) {
        Level.entries.forEach { l ->
            val selected = l == level
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Palette.Primary else Color.Transparent)
                    .clickable { onSelect(l) }.padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(l.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Palette.Ink)
            }
        }
    }
}

/**
 * 진행률은 단계마다 기준이 다르다.
 * 시·도: 전국 시·도 중 / 시·군·구: 지금 있는 시·도 안에서 / 읍·면·동: 지금 있는 시·군·구 안에서.
 */
private fun progressOf(indexes: Map<Level, RegionIndex>?, level: Level, trip: TripState): Triple<String, Int, Int>? {
    val index = indexes?.get(level) ?: return null
    val visited = trip.visitsAt(level).keys
    return when (level) {
        Level.SIDO -> Triple("전국 시·도", visited.size, index.regions.size)
        Level.SGG -> trip.current[Level.SIDO]?.let { sido ->
            Triple(sido.name, visited.count { index.byCode(it)?.parent == sido.code }, index.countIn(sido.code))
        }
        Level.DONG -> trip.current[Level.SGG]?.let { sgg ->
            Triple(sgg.name, visited.count { index.byCode(it)?.parent == sgg.code }, index.countIn(sgg.code))
        }
    }
}

@Composable
private fun StatCards(indexes: Map<Level, RegionIndex>?, level: Level, trip: TripState, modifier: Modifier) {
    val km = (trip.trip?.distanceM ?: 0.0) / 1000
    val progressInfo = progressOf(indexes, level, trip)
    val (scope, done, total) = progressInfo ?: Triple("", 0, 0)
    val progress = if (total > 0) done.toFloat() / total else 0f

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Icons.Filled.Place, Palette.Primary, Palette.PrimarySoft, "총 이동거리", TripRecorder.formatKm(km), Modifier.weight(1f))
        StatCard(Icons.Filled.Share, Palette.Teal, Palette.TealSoft, "새 방문 ${level.label}", "${trip.newInTrip(level)}곳", Modifier.weight(1f))
        StatCard(Icons.Filled.Star, Palette.Amber, Palette.AmberSoft, "스티커 (전체 ${trip.stickers.size}개)", "${trip.stickersInTrip()}개 획득", Modifier.weight(1f))
        Column(
            Modifier.weight(1.2f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.PrimarySoft).padding(18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                when {
                    progressInfo == null -> "진행률"
                    level == Level.SIDO -> "$scope 진행률"
                    else -> "$scope ${level.label} 진행률"
                },
                fontSize = 16.sp, color = Palette.Muted, maxLines = 1,
            )
            Text(
                if (total > 0) "${(progress * 100).toInt()}%  ($done/$total)" else "—",
                fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = Palette.Primary,
                trackColor = Color.White,
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            )
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, tint: Color, bg: Color, label: String, value: String, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, fontSize = 16.sp, color = Palette.Muted)
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        }
    }
}

@Composable
internal fun MapLegend(modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.92f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 34.dp, height = 14.dp).clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient((0..6).map { rainbow(it * 2, com.carnect.roadcanvas.trip.VisitState.PASSED) })),
            )
            Text(if (MapColors.theme == MapTheme.SINGLE) "  지나간 곳" else "  방문 지역 (방문 순서)", fontSize = 15.sp, color = Palette.Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 34.dp, height = 14.dp).clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient((0..6).map { rainbow(it * 2, com.carnect.roadcanvas.trip.VisitState.STAY_CANDIDATE) })),
            )
            Text("  머문 곳 (진하게)", fontSize = 15.sp, color = Palette.Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 34.dp, height = 14.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Unvisited))
            Text("  미방문 지역", fontSize = 15.sp, color = Palette.Ink)
        }
    }
}

@Composable
private fun TimelineCard(events: List<TimelineEvent>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(events.size) { if (events.isNotEmpty()) listState.animateScrollToItem(events.size - 1) }
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(22.dp)).padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DateRange, null, tint = Palette.Primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text("여행 타임라인", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        }
        Spacer(Modifier.height(14.dp))
        if (events.isEmpty()) {
            Text("'여행 시작'을 누르면 지나간 곳이 여기에 차례로 쌓여요.", fontSize = 18.sp, color = Palette.Muted)
        }
        LazyColumn(state = listState) {
            itemsIndexed(events) { i, e -> TimelineRow(e, first = i == 0, last = i == events.lastIndex) }
        }
    }
}

@Composable
private fun TimelineRow(e: TimelineEvent, first: Boolean, last: Boolean) {
    val accent = when (e.kind) {
        EventKind.START, EventKind.DAY, EventKind.END -> Palette.Primary
        EventKind.FIRST_VISIT -> Palette.Teal
        EventKind.STAY, EventKind.CONFIRM, EventKind.STICKER -> Palette.Amber
    }
    Row(Modifier.fillMaxWidth().height(78.dp)) {
        Box(Modifier.width(30.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.width(3.dp).fillMaxHeight().padding(top = if (first) 18.dp else 0.dp, bottom = if (last) 60.dp else 0.dp)
                    .background(Palette.Line),
            )
            Box(
                Modifier.padding(top = 10.dp).size(18.dp).clip(CircleShape).background(Color.White)
                    .border(4.dp, accent, CircleShape),
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.weight(1f).padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp))
                .background(if (e.kind == EventKind.STAY || e.kind == EventKind.CONFIRM || e.kind == EventKind.STICKER) Palette.AmberSoft else Palette.Background)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1)
                Text(e.detail, fontSize = 15.sp, color = Palette.Muted, maxLines = 1)
            }
            if (e.kind == EventKind.STAY || e.kind == EventKind.CONFIRM || e.kind == EventKind.STICKER) {
                Icon(Icons.Filled.Star, null, tint = Palette.Amber, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun ActionRow(recording: Boolean, parked: Boolean, onToggleTrip: () -> Unit, onFeature: (Feature) -> Unit) {
    Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                .background(if (recording) Palette.Orange else Palette.Primary)
                .clickable(onClick = onToggleTrip),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (recording) Icons.Filled.Lock else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (recording) "여행 마치기" else "여행 시작", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        ActionButton(Icons.Filled.Share, "경유 추천", parked) { onFeature(Feature.SMART_DETOUR) }
        ActionButton(Icons.Filled.PlayArrow, "타임랩스 보기", parked) { onFeature(Feature.ROUTE_REPLAY) }
        ActionButton(Icons.Filled.List, "기록 보기", parked) { onFeature(Feature.TIMELINE) }
    }
}

@Composable
private fun RowScope.ActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) Palette.Primary else Palette.Muted.copy(alpha = 0.5f)
    Row(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Palette.Card)
            .border(1.dp, Palette.Line, RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Palette.Ink else color)
    }
}

@Composable
internal fun Launcher(parked: Boolean, onFeature: (Feature) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Card)
            .border(1.dp, Palette.Line, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 56.dp, height = 5.dp).clip(CircleShape).background(Palette.Line))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            AppIcon(Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text("RoadCanvas", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            Text(" (로드 캔버스)", fontSize = 18.sp, color = Palette.Muted)
        }
        Row(Modifier.fillMaxWidth().height(128.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Feature.entries.forEach { f -> LauncherTile(f, enabled = parked || f == Feature.AUTO_CANVAS, onFeature) }
        }
    }
}

@Composable
private fun RowScope.LauncherTile(f: Feature, enabled: Boolean, onFeature: (Feature) -> Unit) {
    val selected = f == Feature.TIMELINE
    val icon = when (f) {
        Feature.AUTO_CANVAS -> Icons.Filled.Place
        Feature.CANVAS_QUEST -> Icons.Filled.Search
        Feature.SMART_DETOUR -> Icons.Filled.Share
        Feature.ROUTE_REPLAY -> Icons.Filled.PlayArrow
        Feature.TIMELINE -> Icons.Filled.DateRange
        Feature.COLLECTION -> Icons.Filled.Star
        Feature.VOICE_PLANNER -> null
        Feature.SETTINGS -> Icons.Filled.Settings
    }
    val tint = when (f) {
        Feature.COLLECTION -> Palette.Amber
        Feature.CANVAS_QUEST -> Palette.Green
        else -> Palette.Primary
    }
    Column(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Palette.PrimarySoft else Palette.Card)
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Primary else Palette.Line, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onFeature(f) }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(40.dp)) else MicGlyph(tint, Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(f.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1)
        if (f.caption.isNotEmpty()) {
            Text(f.caption, fontSize = 14.sp, color = if (selected) Palette.Primary else Palette.Muted, maxLines = 1, lineHeight = TextUnit.Unspecified)
        }
    }
}
