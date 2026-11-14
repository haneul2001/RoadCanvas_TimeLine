package com.carnect.roadcanvas.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnect.roadcanvas.plan.Explorer
import com.carnect.roadcanvas.plan.Place
import com.carnect.roadcanvas.plan.PlanPhase
import com.carnect.roadcanvas.plan.PlanState
import com.carnect.roadcanvas.plan.Suggestion
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.ExcludedZone
import com.carnect.roadcanvas.trip.Trip
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.trip.TripState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 정차 중에 여는 상세 화면의 공통 틀: 제목줄 + 돌아가기. */
@Composable
private fun Panel(
    title: String,
    caption: String,
    onClose: () -> Unit,
    icon: (@Composable () -> Unit),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text(caption, fontSize = 20.sp, color = Palette.Muted)
            }
            Pill("← 지도로", Palette.Primary, Palette.Card, onClose)
        }
        content()
    }
}

@Composable
private fun PanelIcon(icon: ImageVector, tint: Color) = Icon(icon, null, tint = tint, modifier = Modifier.size(44.dp))

@Composable
private fun Pill(text: String, fg: Color, bg: Color, onClick: () -> Unit, enabled: Boolean = true, size: Int = 22) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (enabled) bg else Palette.Unvisited)
            .border(1.dp, Palette.Line, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    ) { Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold, color = if (enabled) fg else Palette.Muted) }
}

@Composable
private fun CardBox(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(22.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

// ───────────────────────────── Voice Planner ─────────────────────────────

/** 말하기 예시. 에뮬레이터 마이크가 없을 때도 같은 흐름을 눌러서 시험할 수 있다. */
val PLAN_EXAMPLES = listOf(
    "속초, 양양, 강릉 들렀다가 서울로",
    "양양 말고 주문진 먼저 가자",
    "강릉은 빼줘",
    "속초 뭐가 유명해?",
    "출발",
)

@Composable
fun PlannerScreen(
    plan: PlanState,
    listening: Boolean,
    partial: String,
    sttStatus: String,
    onMic: () -> Unit,
    onSay: (String) -> Unit,
    onDepart: () -> Unit,
    onSkip: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Panel("Voice Planner", "말로 여러 곳을 정하면 앱이 순서를 계산하고, 내비에 한 곳씩 안내를 맡겨요", onClose, { MicGlyph(Palette.Primary, Modifier.size(44.dp)) }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CardBox(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(if (listening) Palette.Orange else Palette.Primary).clickable(onClick = onMic),
                        contentAlignment = Alignment.Center,
                    ) { MicGlyph(Color.White, Modifier.size(52.dp)) }
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text(if (listening) "듣고 있어요…" else "마이크를 누르고 말해 보세요", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                        Text("음성 인식: $sttStatus", fontSize = 16.sp, color = Palette.Muted)
                    }
                }
                val heard = if (listening) partial else plan.heard
                if (heard.isNotBlank()) Text("“$heard”", fontSize = 24.sp, color = Palette.Ink)
                if (plan.message.isNotBlank()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.PrimarySoft).padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(plan.message, fontSize = 22.sp, color = Palette.Ink) }
                }
                Spacer(Modifier.weight(1f))
                Text("예시 문장 (눌러서 말한 것처럼 보내기)", fontSize = 18.sp, color = Palette.Muted)
                PLAN_EXAMPLES.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { Pill(it, Palette.Ink, Palette.Background, { onSay(it) }, size = 20) }
                    }
                }
            }
            CardBox(Modifier.width(620.dp).fillMaxHeight()) {
                Text("여행 순서", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text(
                    when (plan.phase) {
                        PlanPhase.IDLE -> "아직 계획이 없어요"
                        PlanPhase.PROPOSED -> "확인 후 '출발'"
                        PlanPhase.GUIDING -> "안내 중 · ${plan.target?.name}"
                        PlanPhase.ARRIVED -> "도착 · 출발을 기다려요"
                        PlanPhase.DONE -> "모두 들렀어요"
                    },
                    fontSize = 18.sp, color = Palette.Muted,
                )
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(plan.stops) { i, p ->
                        val done = i < plan.index
                        val now = i == plan.index && plan.phase != PlanPhase.DONE
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (now) Palette.PrimarySoft else Palette.Background).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(if (done) Palette.Muted else if (now) Palette.Primary else Palette.Teal),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                            Spacer(Modifier.width(14.dp))
                            Text(p.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (done) Palette.Muted else Palette.Ink, modifier = Modifier.weight(1f))
                            if (i > 0) {
                                Text("직선 ${plan.stops[i - 1].distanceKmTo(p).toInt()} km", fontSize = 16.sp, color = Palette.Muted)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("출발", Color.White, Palette.Primary, onDepart, enabled = plan.target != null && plan.phase != PlanPhase.GUIDING)
                    Pill("다음 곳으로", Palette.Ink, Palette.Card, onSkip, enabled = plan.target != null)
                    Pill("계획 지우기", Palette.Orange, Palette.Card, onClear, enabled = plan.stops.isNotEmpty())
                }
            }
        }
    }
}

// ───────────────────────────── Canvas Quest ─────────────────────────────

@Composable
fun QuestScreen(
    explorer: Explorer,
    trip: TripState,
    onGo: (Place) -> Unit,
    onAdd: (Place) -> Unit,
    onClose: () -> Unit,
    onCourse: ((String, List<com.carnect.roadcanvas.data.Spot>) -> Unit)? = null,
) {
    val visited = trip.visitsAt(Level.SGG).keys
    val here = trip.track.lastOrNull()?.let { it.lat to it.lon } ?: com.carnect.roadcanvas.plan.TripPlanner.DEFAULT_START
    val currentProvince = trip.current[Level.SIDO]?.name
    val provinces = remember(visited) { explorer.provincesLeft(visited) }
    var province by remember { mutableStateOf<String?>(currentProvince) }
    val list = remember(visited, province, here) { explorer.quest(visited, here, province, limit = 12) }

    Panel("Canvas Quest", "아직 색칠하지 않은 시·군·구와 그곳의 추천 여행지예요 · 가까운 순 (거리는 직선 기준)", onClose, { PanelIcon(Icons.Filled.Search, Palette.Green) }) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { FilterChip("가까운 곳 전체", province == null) { province = null } }
            items(provinces) { (name, left, total) ->
                FilterChip("$name  $left/$total", province == name) { province = name }
            }
        }
        SuggestionList(list, Modifier.weight(1f), extra = false, onGo = onGo, onAdd = onAdd, onCourse = onCourse)
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(24.dp)).background(if (selected) Palette.Primary else Palette.Card)
            .border(1.dp, Palette.Line, RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Palette.Ink) }
}

/** 추천 지역 카드: 지역 이름·거리, 추천 여행지 3곳(종류 스티커 그림 + 이름), 바로 안내·코스·추가. */
@Composable
private fun SuggestionList(
    list: List<Suggestion>,
    modifier: Modifier,
    extra: Boolean,
    onGo: ((Place) -> Unit)?,
    onAdd: (Place) -> Unit,
    addLabel: String = "계획에 추가",
    onCourse: ((String, List<com.carnect.roadcanvas.data.Spot>) -> Unit)? = null,
) {
    if (list.isEmpty()) {
        Text("조건에 맞는 미방문 지역이 없어요.", fontSize = 22.sp, color = Palette.Muted)
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(list) { s ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Card)
                    .border(1.dp, Palette.Line, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(s.region.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                            Spacer(Modifier.width(10.dp))
                            Text(s.region.province, fontSize = 17.sp, color = Palette.Muted)
                        }
                        Text(
                            buildString {
                                if (extra) {
                                    val km = s.extraKm ?: 0.0
                                    append("약 ${(km / DETOUR_KMH * 60).toInt().coerceAtLeast(1)}분 더 (추정, ${km.toInt()} km) · ")
                                }
                                append("직선 ${s.distanceKm.toInt()} km")
                                if (s.spots.isEmpty() && s.landmarks.isNotEmpty()) append(" · 볼거리 ${s.landmarks.joinToString { it.name }}")
                            },
                            fontSize = 17.sp, color = Palette.Muted, maxLines = 1,
                        )
                    }
                    if (onGo != null) {
                        Pill("바로 안내", Color.White, Palette.Primary, { onGo(s.place) }, size = 20)
                        Spacer(Modifier.width(10.dp))
                    }
                    if (onCourse != null && s.spots.isNotEmpty()) {
                        Pill("코스로 계획", Palette.Green, Palette.GreenSoft, { onCourse(s.region.name, s.spots) }, size = 20)
                        Spacer(Modifier.width(10.dp))
                    }
                    Pill(addLabel, Palette.Primary, Palette.PrimarySoft, { onAdd(s.place) }, size = 20)
                }
                if (s.spots.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        s.spots.take(3).forEachIndexed { i, sp ->
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Palette.Background).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                com.carnect.roadcanvas.sticker.StickerCatalog.byKey(sp.kind)?.let { StickerImage(it, 52.dp) }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("${i + 1}. ${sp.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1)
                                    Text(sp.note, fontSize = 14.sp, color = Palette.Muted, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Smart Detour ─────────────────────────────

@Composable
fun DetourScreen(
    explorer: Explorer,
    trip: TripState,
    plan: PlanState,
    onDetour: (Place) -> Unit,
    onOpenPlanner: () -> Unit,
    onClose: () -> Unit,
) {
    val dest = plan.target
    // 더 들러도 되는 시간(분). 평균 시속 [DETOUR_KMH]km로 어림한 거리로 바꿔 고른다.
    var budget by remember { mutableIntStateOf(15) }
    val here = trip.track.lastOrNull()?.let { it.lat to it.lon } ?: com.carnect.roadcanvas.plan.TripPlanner.DEFAULT_START
    val visited = trip.visitsAt(Level.SGG).keys
    val list = remember(dest, budget, visited, here) {
        dest?.let { explorer.detour(visited, here, it, budget * DETOUR_KMH / 60, limit = 8) }.orEmpty()
    }
    Panel("Smart Detour", "다음 목적지로 가는 길에 조금만 돌아가면 칠할 수 있는 곳", onClose, { PanelIcon(Icons.Filled.Share, Palette.Primary) }) {
        if (dest == null) {
            CardBox(Modifier.fillMaxWidth()) {
                Text("먼저 Voice Planner에서 목적지를 정해 주세요.", fontSize = 24.sp, color = Palette.Ink)
                Pill("Voice Planner 열기", Color.White, Palette.Primary, onOpenPlanner)
            }
            return@Panel
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("목적지 ${dest.name} · 더 들러도 되는 시간", fontSize = 22.sp, color = Palette.Ink)
            listOf(15, 30, 60).forEach { min -> FilterChip("${min}분", budget == min) { budget = min } }
        }
        Text(
            "추가 시간은 직선 거리를 평균 시속 60km로 나눠 어림한 값이라 실제와 다를 수 있어요. 정확한 시간은 경유지를 넣은 뒤 내비가 알려 줘요.",
            fontSize = 17.sp, color = Palette.Muted,
        )
        SuggestionList(list, Modifier.weight(1f), extra = true, onGo = null, onAdd = onDetour, addLabel = "들렀다 가기")
    }
}

// ───────────────────────────── 기록 보기 ─────────────────────────────

private val dateFormat = SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREA)

/** 우회 추천에서 시간을 거리로 어림할 때 쓰는 평균 시속(km). 실제 시간은 내비가 알려 준다. */
const val DETOUR_KMH = 60.0

/** 기록 보기 묶음 단위. */
enum class TripPeriod(val label: String) { TRIP("여행별"), WEEK("주간"), MONTH("월별"), YEAR("연도별"), ALL("전체") }

/** 기간 하나(주·달·해)에 든 여행들. */
data class PeriodGroup(val key: String, val title: String, val tripIds: List<Long>)

/** 여행을 기간별로 묶는다(최근 기간이 위). 주는 월요일 시작. */
fun groupTrips(trips: List<Trip>, period: TripPeriod): List<PeriodGroup> {
    val zone = java.time.ZoneId.systemDefault()
    fun day(t: Trip) = java.time.Instant.ofEpochMilli(t.startMs).atZone(zone).toLocalDate()
    return trips.groupBy { t ->
        val d = day(t)
        when (period) {
            TripPeriod.TRIP -> t.id.toString()
            TripPeriod.WEEK -> d.with(java.time.DayOfWeek.MONDAY).toString()
            TripPeriod.MONTH -> "${d.year}-${d.monthValue}"
            TripPeriod.YEAR -> "${d.year}"
            TripPeriod.ALL -> "all"
        }
    }.map { (key, ts) ->
        val d = day(ts.first())
        val title = when (period) {
            TripPeriod.TRIP -> dateFormat.format(Date(ts.first().startMs))
            TripPeriod.WEEK -> {
                val mon = d.with(java.time.DayOfWeek.MONDAY)
                val sun = mon.plusDays(6)
                "${mon.year}년 ${mon.monthValue}월 ${mon.dayOfMonth}일 ~ ${sun.monthValue}월 ${sun.dayOfMonth}일"
            }
            TripPeriod.MONTH -> "${d.year}년 ${d.monthValue}월"
            TripPeriod.YEAR -> "${d.year}년"
            TripPeriod.ALL -> "지금까지 모든 여행"
        }
        PeriodGroup(key, title, ts.map { it.id }.sorted())
    }.sortedByDescending { it.tripIds.max() }
}

@Composable
fun TripsScreen(
    indexes: Map<Level, RegionIndex>,
    trips: List<Trip>,
    current: TripState,
    load: (Long) -> TripState?,
    loadPeriod: (List<Long>) -> TripState?,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val finished = trips.filter { it.endMs != null || it.id == current.trip?.id }
    var period by remember { mutableStateOf(TripPeriod.TRIP) }
    val groups = remember(finished, period) { groupTrips(finished, period) }
    var selectedKey by remember(period) { mutableStateOf(groups.firstOrNull()?.key) }
    val group = groups.firstOrNull { it.key == selectedKey }
    val selected = remember(group, trips) {
        group?.let { g -> if (period == TripPeriod.TRIP) load(g.tripIds.first()) else loadPeriod(g.tripIds) }
    }
    var run by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    val replay = remember(selected) { selected?.let { Replay(it, indexes, group?.tripIds.orEmpty().toSet()) }?.takeIf { it.playable } }
    LaunchedEffect(run) {
        if (run == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(if (period == TripPeriod.TRIP) 12_000 else 18_000, easing = LinearEasing))
        delay(500)
    }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    val newByTrip = current.visitsAt(Level.SGG).values.groupingBy { it.tripId }.eachCount()

    Panel("여행 기록", "여행별·주간·월별·연도별로 묶어 지도와 타임랩스로 다시 볼 수 있어요. 기록은 기기 안에만 있어요.", onClose, { PanelIcon(Icons.Filled.List, Palette.Primary) }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LazyColumn(Modifier.width(560.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TripPeriod.entries.forEach { p -> FilterChip(p.label, period == p) { period = p; confirmDelete = null } }
                    }
                }
                item { FrequentPlaces(indexes, current) }
                if (groups.isEmpty()) item { Text("아직 기록된 여행이 없어요.", fontSize = 22.sp, color = Palette.Muted) }
                items(groups, key = { it.key }) { g ->
                    val isSel = g.key == selectedKey
                    val ts = g.tripIds.mapNotNull { id -> finished.firstOrNull { it.id == id } }
                    val km = ts.sumOf { it.distanceM } / 1000
                    val newSgg = g.tripIds.sumOf { newByTrip[it] ?: 0 }
                    val single = period == TripPeriod.TRIP
                    val t = ts.firstOrNull()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (isSel) Palette.PrimarySoft else Palette.Card)
                            .border(if (isSel) 2.dp else 1.dp, if (isSel) Palette.Primary else Palette.Line, RoundedCornerShape(18.dp))
                            .clickable { selectedKey = g.key; confirmDelete = null }.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                            Text(
                                (if (single) "" else "여행 ${ts.size}번 · ") + "${TripRecorder.formatKm(km)} · 새 시·군·구 ${newSgg}곳" +
                                    if (single && t?.active == true) " · 기록 중" else "",
                                fontSize = 17.sp, color = Palette.Muted,
                            )
                        }
                        if (single && t != null && !t.active) {
                            if (confirmDelete == t.id) {
                                Pill("지우기", Color.White, Palette.Orange, { onDelete(t.id); confirmDelete = null; selectedKey = null }, size = 18)
                            } else {
                                Box(Modifier.size(48.dp).clip(CircleShape).clickable { confirmDelete = t.id }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Delete, "지우기", tint = Palette.Muted)
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Palette.Sea)) {
                val index = indexes.getValue(Level.SGG)
                if (selected != null) {
                    val playing = replay != null && progress.value < 1f
                    val frame = if (playing) replay!!.frameAt(replay.timeAt(progress.value)) else null
                    TripMap(index, selected, interactive = !playing, Modifier.fillMaxSize(), frame = frame)
                    Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill(if (playing) "재생 중…" else "▶ 타임랩스", Color.White, Palette.Primary, { run++ }, enabled = replay != null && !playing)
                        Spacer(Modifier.width(12.dp))
                        if (confirmDelete != null) {
                            Text("지운 여행은 되돌릴 수 없어요. 그 여행에서만 간 곳과 스티커도 지워져요.", fontSize = 17.sp, color = Palette.Orange)
                        } else if (group != null && period != TripPeriod.TRIP) {
                            Text("${group.title} · 여행 ${group.tripIds.size}번을 이어서 재생 (여행 사이 빈 시간은 건너뜀)", fontSize = 17.sp, color = Palette.Ink)
                        }
                    }
                } else {
                    Text("왼쪽에서 여행이나 기간을 골라 주세요.", fontSize = 22.sp, color = Palette.Muted, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

// ───────────────────────────── 설정 · 개인정보 ─────────────────────────────

/** 앱에 넣은 외부 데이터의 출처 표시(라이선스 요구 사항). */
private val DATA_SOURCES = listOf(
    "행정구역 경계: 통계청 SGIS 행정동 경계(공공누리 제1유형), 가공 vuski/admdongkor(CC BY 4.0), 2026-10-01 기준",
    "테스트 주행 도로 모양: © OpenStreetMap contributors (ODbL), 경로 계산 OSRM. 미리 받아 앱에 넣었고 실행 중 외부로 위치를 보내지 않음",
    "명소 좌표: 앱에 넣은 대표 명소 18곳(근사 좌표)",
)

@Composable
fun SettingsScreen(
    zones: List<ExcludedZone>,
    hereName: String?,
    recording: Boolean,
    onTheme: (MapTheme) -> Unit,
    onAddZone: () -> Unit,
    onRemoveZone: (ExcludedZone) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
    /** 자동 기록 켜짐 여부와 켜고 끄기. */
    autoRecord: Boolean,
    autoEndMin: Long,
    onAutoRecord: (Boolean) -> Unit,
    status: @Composable () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmAuto by remember { mutableStateOf(false) }
    Panel("설정 · 개인정보", if (autoRecord) "자동 기록이 켜져 있어요 · 위치는 기기 안에만 저장해요" else "위치 기록은 '여행 시작'을 누른 뒤에만, 기기 안에만 저장해요", onClose, { PanelIcon(Icons.Filled.Settings, Palette.Primary) }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CardBox(Modifier.weight(1f).fillMaxHeight()) {
                Text("지도 색", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MapTheme.entries.forEach { t ->
                        Row(
                            Modifier.clip(RoundedCornerShape(24.dp)).background(if (MapColors.theme == t) Palette.PrimarySoft else Palette.Card)
                                .border(if (MapColors.theme == t) 2.dp else 1.dp, if (MapColors.theme == t) Palette.Primary else Palette.Line, RoundedCornerShape(24.dp))
                                .clickable { onTheme(t) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 견본: 그 테마의 첫 다섯 색
                            (0..2).forEach { i ->
                                Box(Modifier.size(14.dp).clip(CircleShape).background(rainbow(i, com.carnect.roadcanvas.trip.VisitState.STAY_CANDIDATE, t)))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(t.label, fontSize = 18.sp, color = Palette.Ink)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = Palette.Primary, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("기록 제외 구역", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                }
                Text("집·회사처럼 남기고 싶지 않은 곳. 이 반경 500m 안의 위치는 저장하지도, 색칠하지도 않아요.", fontSize = 18.sp, color = Palette.Muted)
                Pill(
                    if (hereName != null) "지금 위치($hereName)를 제외 구역으로" else "현재 위치를 아직 몰라요",
                    Color.White, Palette.Primary, onAddZone, enabled = hereName != null,
                )
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(zones) { z ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Background).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${z.name} · 반경 ${z.radiusM.toInt()}m", fontSize = 21.sp, color = Palette.Ink, modifier = Modifier.weight(1f))
                            Pill("해제", Palette.Orange, Palette.Card, { onRemoveZone(z) }, size = 18)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (confirmClear) {
                    Text("모든 여행·방문·스티커·제외 구역이 지워지고 되돌릴 수 없어요.", fontSize = 18.sp, color = Palette.Orange)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pill("모두 지우기", Color.White, Palette.Orange, { onClearAll(); confirmClear = false })
                        Pill("취소", Palette.Ink, Palette.Card, { confirmClear = false })
                    }
                } else {
                    Pill(
                        if (recording) "기록 중에는 지울 수 없어요" else "여행 기록 모두 지우기",
                        Palette.Orange, Palette.Card, { confirmClear = true }, enabled = !recording,
                    )
                }
            }
            Column(Modifier.width(820.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CardBox(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("자동 기록", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                            Text(
                                if (autoRecord) "켜짐 · 주행(기어 D)을 시작하면 기록하고, 주차한 채 ${autoEndMin}분이 지나면 끝내요"
                                else "꺼짐 · '여행 시작'을 눌렀을 때만 기록해요",
                                fontSize = 17.sp, color = if (autoRecord) Palette.Green else Palette.Muted,
                            )
                        }
                        Box(
                            Modifier.size(width = 88.dp, height = 48.dp).clip(RoundedCornerShape(24.dp))
                                .background(if (autoRecord) Palette.Green else Palette.Unvisited)
                                .clickable { if (autoRecord) onAutoRecord(false) else confirmAuto = true },
                            contentAlignment = if (autoRecord) Alignment.CenterEnd else Alignment.CenterStart,
                        ) { Box(Modifier.padding(5.dp).size(38.dp).clip(CircleShape).background(Color.White)) }
                    }
                    if (confirmAuto && !autoRecord) {
                        Text(
                            "켜면 주행할 때마다 '여행 시작'을 누르지 않아도 달린 길(위치)을 기록해요. 기록은 기기 안에만 저장되고, " +
                                "기록 제외 구역(집·회사) 안에서는 저장하지 않으며, 언제든 끄거나 지울 수 있어요. 대기 중에는 알림이 떠 있어요.",
                            fontSize = 17.sp, color = Palette.Ink,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Pill("동의하고 켜기", Color.White, Palette.Green, { onAutoRecord(true); confirmAuto = false })
                            Pill("취소", Palette.Ink, Palette.Card, { confirmAuto = false })
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Card)) { status() }
                CardBox(Modifier.fillMaxWidth()) {
                    Text("데이터 출처", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    DATA_SOURCES.forEach { Text("· $it", fontSize = 16.sp, color = Palette.Muted) }
                }
            }
        }
    }
}

// ───────────────────────────── 테스트 주행 ─────────────────────────────

/** 테스트 주행 목적지 예시. 말한 것과 같은 흐름(Voice Planner)으로 넣는다. */
val DRIVE_EXAMPLES = listOf(
    "속초로",
    "강릉으로",
    "부산으로",
    "속초, 양양, 강릉 들렀다가 서울로",
    "홍천, 인제 들렀다가 속초로",
    "대전, 대구 들렀다가 부산으로",
    "전주, 여수 들렀다가 광주로",
    "경주, 부산 들렀다가 강릉으로",
)

@Composable
fun TestDriveScreen(
    plan: PlanState,
    drive: com.carnect.roadcanvas.sim.DriveState,
    recording: Boolean,
    onPlan: (String, fromSeoul: Boolean) -> Unit,
    onSpeed: (Int) -> Unit,
    onStart: (fromSeoul: Boolean) -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    /** 시연용 샘플 여행 만들기. null이면 버튼을 숨긴다. */
    onDemo: (() -> Unit)?,
    /** 샘플 만들기 진행 상황(없으면 null). */
    demoStatus: String?,
    onClose: () -> Unit,
) {
    var fromSeoul by remember { mutableStateOf(true) }
    var confirmReset by remember { mutableStateOf(false) }
    Panel("테스트 주행", "목적지를 내비에 넣고, 가상의 차가 시간 배속으로 달려요. 기록·색칠·타임라인·음성은 실제 주행과 같은 방식으로 쌓여요.", onClose, { PanelIcon(Icons.Filled.Share, Palette.Orange) }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CardBox(Modifier.weight(1f).fillMaxHeight()) {
                Text("1. 목적지", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                DRIVE_EXAMPLES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { Pill(it, Palette.Ink, Palette.Background, { onPlan(it, fromSeoul) }, size = 19) }
                    }
                }
                Text(
                    if (plan.stops.isEmpty()) "아직 목적지가 없어요" else "순서: " + plan.stops.drop(plan.index).joinToString(" → ") { it.name },
                    fontSize = 20.sp, color = if (plan.stops.isEmpty()) Palette.Muted else Palette.Primary,
                )
                Text("2. 출발 위치", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip("서울시청에서", fromSeoul) { fromSeoul = true }
                    FilterChip("마지막 기록 위치에서", !fromSeoul) { fromSeoul = false }
                }
                Text("3. 시간 배속 (시속 ${drive.kmh}km 기준)", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.carnect.roadcanvas.sim.TestDrive.SPEEDS.forEach { x -> FilterChip("×$x", drive.speedX == x) { onSpeed(x) } }
                }
                Text(
                    "서울·춘천·홍천·인제·속초·양양·강릉·대전·전주·광주·여수·대구·경주·부산 사이는 실제 도로 모양(미리 받아 둔 OpenStreetMap 경로)을 따라가고, 그 밖은 직선으로 가요. 배속 중에도 머문 곳 판정은 실제 시간 기준이에요.",
                    fontSize = 16.sp, color = Palette.Muted,
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (drive.running) {
                        Pill("테스트 멈추기", Color.White, Palette.Orange, onStop)
                    } else {
                        Pill("출발 (내비 안내 + 주행 시작)", Color.White, Palette.Primary, { onStart(fromSeoul) }, enabled = plan.target != null)
                    }
                }
            }
            CardBox(Modifier.width(620.dp).fillMaxHeight()) {
                Text("상태", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text(if (drive.running) drive.status else "대기", fontSize = 22.sp, color = Palette.Ink)
                drive.remainingKm?.let { Text("남은 거리 약 ${it.toInt()} km", fontSize = 20.sp, color = Palette.Muted) }
                Text("이번 테스트로 달린 거리 ${TripRecorder.formatKm(drive.drivenKm)}", fontSize = 20.sp, color = Palette.Muted)
                Text("여행 기록: ${if (recording) "기록 중" else "꺼짐"}", fontSize = 20.sp, color = Palette.Muted)
                Spacer(Modifier.weight(1f))
                if (onDemo != null) {
                    Text("시연용 샘플 여행", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Text("지난 6주 동안 춘천·전주·여수·경주·부산·속초·강릉을 다녀온 것처럼 여행 6개를 바로 만들어요. 실제 기록과 같은 방식으로 쌓여 지도·타임랩스·미션에 반영돼요.", fontSize = 17.sp, color = Palette.Muted)
                    if (demoStatus != null) Text(demoStatus, fontSize = 18.sp, color = Palette.Primary)
                    Pill("샘플 여행 만들기", Palette.Primary, Palette.PrimarySoft, onDemo, enabled = !recording && !drive.running && demoStatus?.endsWith("중…") != true)
                    Spacer(Modifier.height(12.dp))
                }
                Text("초기화", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text("테스트를 멈추고 모든 여행·방문·스티커·계획을 지워 처음 상태로 돌려요. 되돌릴 수 없어요.", fontSize = 17.sp, color = Palette.Muted)
                if (confirmReset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pill("정말 초기화", Color.White, Palette.Orange, { onReset(); confirmReset = false })
                        Pill("취소", Palette.Ink, Palette.Card, { confirmReset = false })
                    }
                } else {
                    Pill("전체 초기화", Palette.Orange, Palette.Card, { confirmReset = true })
                }
            }
        }
    }
}

/** 많이 간 시·군·구(다녀온 여행 수 기준). */
@Composable
fun FrequentPlaces(indexes: Map<Level, RegionIndex>, trip: TripState, modifier: Modifier = Modifier) {
    val sgg = indexes.getValue(Level.SGG)
    val top = trip.visitsAt(Level.SGG).entries.sortedWith(compareByDescending<Map.Entry<String, com.carnect.roadcanvas.trip.VisitRecord>> { it.value.count }.thenBy { it.value.firstMs })
        .take(5).mapNotNull { e -> sgg.byCode(e.key)?.let { it to e.value.count } }
    CardBox(modifier.fillMaxWidth()) {
        Text("많이 간 곳", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        if (top.isEmpty()) Text("아직 기록이 없어요.", fontSize = 18.sp, color = Palette.Muted)
        top.forEachIndexed { i, (r, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Primary, modifier = Modifier.width(28.dp))
                Text("${r.name} (${r.province})", fontSize = 18.sp, color = Palette.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${n}회", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            }
        }
    }
}
