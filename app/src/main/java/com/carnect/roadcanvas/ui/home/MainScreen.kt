package com.carnect.roadcanvas.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnect.roadcanvas.plan.PlanPhase
import com.carnect.roadcanvas.plan.PlanState
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.trip.TripState
import kotlinx.coroutines.launch

/**
 * 메인 화면. 가운데 지도는 현재 위치를 중심으로 보이고, 등록된 경로(Voice Planner·추천으로 정한 들를 곳)가 있으면
 * 지도에 점선·번호로 그리고 오른쪽에 경로 카드를 띄운다. 왼쪽은 지금 위치와 기록 상태.
 */
@Composable
fun MainScreen(
    indexes: Map<Level, RegionIndex>?,
    trip: TripState,
    plan: PlanState,
    parked: Boolean,
    driving: Boolean,
    onToggleTrip: () -> Unit,
    onFeature: (Feature) -> Unit,
    onDepart: () -> Unit,
    /** 주행하다 주차(P)로 바꾸면: 이번 여행 타임랩스로. */
    onParkedAfterDrive: () -> Unit,
    voiceText: String,
    listening: Boolean,
    onVoice: () -> Unit,
    simLine: String?,
    simWaiting: Boolean,
    onSimDepart: () -> Unit,
    onSimStop: () -> Unit,
    onTestDrive: () -> Unit,
    decorations: List<com.carnect.roadcanvas.deco.Decoration>,
    onDecorate: () -> Unit,
    /** 위치 권한이 있는지(없으면 '위치 표시 허용' 버튼). */
    locationAllowed: Boolean,
    onAllowLocation: () -> Unit,
    /** 시·군·구 추천 여행지. */
    spots: com.carnect.roadcanvas.data.Spots = com.carnect.roadcanvas.data.Spots.EMPTY,
    onGoSpot: (com.carnect.roadcanvas.data.Spot) -> Unit = {},
    onCourse: (String, List<com.carnect.roadcanvas.data.Spot>) -> Unit = { _, _ -> },
    onStats: () -> Unit = {},
    /** 저장된 여행 목록과 기간 기록 불러오기(여행 히스토리·기간 타임랩스). */
    trips: List<com.carnect.roadcanvas.trip.Trip> = emptyList(),
    loadPeriod: (List<Long>) -> TripState? = { null },
) {
    // 날짜 토글(년·월)과 여행 히스토리 패널
    val today = java.time.LocalDate.now()
    var year by rememberSaveable { mutableStateOf(today.year) }
    var month by rememberSaveable { mutableStateOf(today.monthValue) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    // 기간 타임랩스: (제목, 기록, 재생 길이 ms)
    var playing by remember { mutableStateOf<Triple<String, TripState, Int>?>(null) }
    var playRun by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val replay = remember(playing) { playing?.let { (_, st, _) -> indexes?.let { Replay(st, it, st.segments.map { s -> s.second }.toSet().ifEmpty { setOfNotNull(st.trip?.id) }) } }?.takeIf { it.playable } }
    LaunchedEffect(playRun) {
        val p = playing ?: return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, androidx.compose.animation.core.tween(p.third, easing = androidx.compose.animation.core.LinearEasing))
        kotlinx.coroutines.delay(2_000)
        playing = null
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var loadingPlay by remember { mutableStateOf(false) }
    // 기간 기록(여행 수십 개)은 무거우니 백그라운드에서 읽는다.
    fun play(title: String, ids: List<Long>, ms: Int) {
        if (loadingPlay) return
        loadingPlay = true
        scope.launch {
            val st = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { loadPeriod(ids) }
            loadingPlay = false
            if (st == null) return@launch
            playing = Triple(title, st, ms)
            playRun++
        }
    }
    // 지도에서 누른 시·군·구(추천 여행지 카드)
    var picked by remember { mutableStateOf<com.carnect.roadcanvas.region.Region?>(null) }
    val zoomControl = remember { MapZoom() }
    var level by rememberSaveable { mutableStateOf(Level.SGG) }
    val index = indexes?.get(level)
    val here = (trip.here ?: trip.track.lastOrNull())?.let { it.lat to it.lon }
    val route = if (plan.phase != PlanPhase.DONE) plan.remaining.map { it.name to (it.lat to it.lon) } else emptyList()

    var wasDriving by remember { mutableStateOf(false) }
    LaunchedEffect(parked, driving) {
        if (parked && wasDriving) onParkedAfterDrive()
        wasDriving = driving
    }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(onTestDrive, onStats = onStats)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(360.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VoiceBubble(voiceText, listening, onVoice)
                HereCard(indexes, trip, parked, driving, onToggleTrip, locationAllowed, onAllowLocation, Modifier.weight(1f))
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Palette.Sea)) {
                val pl = playing
                // 한 프레임에 한 번만 계산(지도와 막대가 같이 쓴다)
                val playFrame = if (pl != null && replay != null) replay.frameAt(replay.timeAt(progress.value)) else null
                if (index != null && pl != null && replay != null) {
                    // 기간 타임랩스: 그 기간 전체가 보이는 지도에 시간 순으로 그린다
                    TripMap(index, pl.second, interactive = false, Modifier.fillMaxSize(), frame = playFrame, decorations = decorations)
                } else if (index != null) {
                    TripMap(
                        index, trip, interactive = parked, Modifier.fillMaxSize(),
                        decorations = decorations, focus = here, route = route,
                        onTapGeo = if (parked) { g -> picked = indexes?.get(Level.SGG)?.find(g.first, g.second) } else null,
                        zoomControl = zoomControl,
                    )
                }
                Column(Modifier.align(Alignment.TopStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LevelSelector(level, onSelect = { level = it }, Modifier)
                    DateToggles(
                        trips, year, month, historyOpen,
                        onYear = { year = it; historyOpen = true },
                        onMonth = { month = it; historyOpen = true },
                        onHistory = { historyOpen = !historyOpen },
                    )
                    if (parked) {
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp)).background(Palette.Surface.copy(alpha = 0.95f))
                                .border(1.dp, Palette.Line, RoundedCornerShape(20.dp)).clickable(onClick = onDecorate)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) { Text("🎨  지도 꾸미기", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
                    }
                }
                if (here == null) {
                    Text(
                        if (locationAllowed) "현재 위치를 받는 중이에요…" else "위치를 허용하면 현재 위치가 지도 가운데에 표시돼요",
                        fontSize = 18.sp, color = Palette.Ink,
                        modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp))
                            .background(Palette.Surface.copy(alpha = 0.92f)).padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                if (historyOpen && pl == null) {
                    HistoryPanel(
                        trips, trip, year, month,
                        onPlayMonth = { play("${year}년 ${month}월", trips.inPeriod(year, month).map { it.id }, 16_000) },
                        onPlayYear = { play("${year}년", trips.inPeriod(year).map { it.id }, 26_000) },
                        onPlayTrip = { t -> play(t.date().let { "${it.monthValue}월 ${it.dayOfMonth}일 여행" }, listOf(t.id), 12_000) },
                        onClose = { historyOpen = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )
                }
                picked?.let { r ->
                        RegionSheet(
                            r, trip, spots.course(r.code), here,
                            onGo = { sp -> picked = null; onGoSpot(sp) },
                            onCourse = { c -> picked = null; onCourse(r.name, c) },
                            onClose = { picked = null },
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                        )
                }
                if (pl == null && parked) {
                    ZoomButtons(zoomControl, Modifier.align(Alignment.CenterEnd).padding(14.dp))
                }
                if (pl != null && replay != null) {
                    val frame = playFrame ?: return@Box
                    val byId = trips.associateBy { it.id }
                    PeriodReplayBar(
                        pl.first, replay, frame, progress.value,
                        dateOf = { t ->
                            val id = pl.second.segments.lastOrNull { it.first <= t }?.second ?: pl.second.trip?.id
                            byId[id]?.date()?.let { "${it.monthValue}월 ${it.dayOfMonth}일" } ?: ""
                        },
                        onStop = { playing = null },
                        modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                    )
                } else if (simLine != null) {
                    SimBar(simLine, simWaiting, onSimDepart, onSimStop, Modifier.align(Alignment.BottomStart).padding(14.dp))
                } else {
                    MapLegend(Modifier.align(Alignment.BottomEnd).padding(14.dp))
                }
            }
            RouteCard(plan, here, parked, onDepart, onFeature, Modifier.width(400.dp).fillMaxHeight())
        }
        Launcher(parked, onFeature, selected = Feature.AUTO_CANVAS)
    }
}

/** 지금 위치: 시·군·구(크게), 시·도·읍·면·동, 좌표, 기록 상태, 지금 시·도 진행률, 여행 시작/마치기. */
@Composable
private fun HereCard(
    indexes: Map<Level, RegionIndex>?,
    trip: TripState,
    parked: Boolean,
    driving: Boolean,
    onToggleTrip: () -> Unit,
    locationAllowed: Boolean,
    onAllowLocation: () -> Unit,
    modifier: Modifier,
) {
    val sgg = trip.current[Level.SGG]
    val sido = trip.current[Level.SIDO]
    val dong = trip.current[Level.DONG]
    val p = trip.here ?: trip.track.lastOrNull()
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(22.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("지금 위치", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Muted)
        Text(sgg?.let(TripRecorder::placeName) ?: if (locationAllowed) "위치 확인 중" else "위치를 아직 몰라요", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1)
        Text(
            listOfNotNull(sido?.name, sgg?.name?.takeIf { it != sido?.name }, dong?.name).joinToString(" · ")
                .ifEmpty { if (locationAllowed) "차 위치를 받고 있어요" else "위치를 허용하면 지금 있는 곳을 보여 줘요(저장은 여행 시작 후에만)" },
            fontSize = 18.sp, color = Palette.Ink, maxLines = 2,
        )
        if (!locationAllowed) {
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(Palette.PrimarySoft)
                    .border(1.dp, Palette.Primary, RoundedCornerShape(14.dp)).clickable(onClick = onAllowLocation),
                contentAlignment = Alignment.Center,
            ) { Text("📍  위치 표시 허용", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Primary) }
        }
        if (p != null) Text("%.4f, %.4f".format(p.lat, p.lon), fontSize = 15.sp, color = Palette.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(if (trip.recording) "● 기록 중" else "기록 꺼짐", if (trip.recording) Palette.Green else Palette.Muted)
            StatusPill(if (parked) "주차 중" else if (driving) "주행 중 · 음성만" else "기어 확인 중", if (parked) Palette.Primary else Palette.Orange)
        }
        // 지금 시·도에서 칠한 시·군·구
        val sggIndex = indexes?.get(Level.SGG)
        if (sido != null && sggIndex != null) {
            val total = sggIndex.countIn(sido.code)
            val done = trip.visitsAt(Level.SGG).keys.count { sggIndex.byCode(it)?.parent == sido.code }
            Spacer(Modifier.height(4.dp))
            Text("${sido.name} 시·군·구 $done/$total", fontSize = 17.sp, color = Palette.Muted)
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                color = Palette.Primary, trackColor = Palette.PrimarySoft,
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            )
        }
        if (trip.recording) {
            val km = (trip.trip?.distanceM ?: 0.0) / 1000
            Text("이번 여행 ${TripRecorder.formatKm(km)} · 새 시·군·구 ${trip.newInTrip(Level.SGG)}곳 · 스티커 ${trip.stickersInTrip()}개", fontSize = 16.sp, color = Palette.Muted)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(16.dp)).background(if (trip.recording) Palette.Orange else Palette.Primary)
                .clickable(onClick = onToggleTrip),
            contentAlignment = Alignment.Center,
        ) { Text(if (trip.recording) "여행 마치기" else "▶  여행 시작", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White) }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).border(1.dp, color, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color) }
}

/** 등록된 경로가 있으면 들를 곳 목록과 출발, 없으면 계획 만들기·추천으로 안내. */
@Composable
private fun RouteCard(
    plan: PlanState,
    here: Pair<Double, Double>?,
    parked: Boolean,
    onDepart: () -> Unit,
    onFeature: (Feature) -> Unit,
    modifier: Modifier,
) {
    val active = plan.stops.isNotEmpty() && plan.phase != PlanPhase.DONE
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(22.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("등록된 경로", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        if (!active) {
            Text(
                if (plan.phase == PlanPhase.DONE) "계획한 곳을 모두 들렀어요." else "아직 등록된 경로가 없어요.\n말로 여러 곳을 정하거나, 안 가 본 곳을 추천받아 보세요.",
                fontSize = 18.sp, color = Palette.Muted,
            )
            Spacer(Modifier.weight(1f))
            BigButton("🎤  말로 계획하기", Palette.Primary, enabled = parked) { onFeature(Feature.VOICE_PLANNER) }
            BigButton("🔍  안 가 본 곳 추천", Palette.Green, enabled = parked) { onFeature(Feature.CANVAS_QUEST) }
            return@Column
        }
        Text(
            when (plan.phase) {
                PlanPhase.GUIDING -> "안내 중 · 다음 ${plan.target?.name}"
                PlanPhase.ARRIVED -> "도착 · '출발'을 기다려요"
                else -> "출발 전 · 내비에 아직 안 넣었어요"
            },
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (plan.phase == PlanPhase.GUIDING) Palette.Primary else Palette.Orange,
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(plan.remaining) { i, p ->
                val next = i == 0
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (next) Palette.OrangeSoft else Palette.Background).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Palette.Orange), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                    if (next && here != null) Text("직선 ${p.distanceKmTo(here.first, here.second).toInt()}km", fontSize = 15.sp, color = Palette.Muted)
                }
            }
        }
        if (plan.phase != PlanPhase.GUIDING) BigButton("출발 (내비 안내)", Palette.Primary, enabled = true, onClick = onDepart)
        BigButton("계획 고치기", Palette.Muted, enabled = parked) { onFeature(Feature.VOICE_PLANNER) }
    }
}

@Composable
private fun BigButton(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(if (enabled) color.copy(alpha = 0.12f) else Palette.Unvisited)
            .border(1.dp, if (enabled) color else Palette.Line, RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = if (enabled) color else Palette.Muted) }
}

/** 지도 확대(+)·축소(−)·원래대로(⟲) 버튼 묶음. */
@Composable
private fun ZoomButtons(zoom: MapZoom, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Palette.Surface.copy(alpha = 0.96f))
            .border(1.dp, Palette.Line, RoundedCornerShape(22.dp)).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf("+" to zoom::zoomIn, "−" to zoom::zoomOut, "⟲" to zoom::reset).forEachIndexed { i, (label, action) ->
            if (i > 0) Box(Modifier.width(36.dp).height(1.dp).background(Palette.Line))
            Box(
                Modifier.size(56.dp).clip(CircleShape).clickable { action() },
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = if (label == "⟲") 24.sp else 32.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
        }
    }
}
