package com.carnect.roadcanvas.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnect.roadcanvas.R
import com.carnect.roadcanvas.data.Spot
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.Region
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.sticker.StickerCatalog
import com.carnect.roadcanvas.trip.Trip
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.trip.TripState
import com.carnect.roadcanvas.trip.VisitState

/** 앱 로고(아이콘 그림) + RoadCanvas 글자. */
@Composable
fun BrandTitle(subtitle: String = "지금, 당신의 여행이 지도 위에 쌓이고 있어요", logo: Int = 64) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo_roadcanvas), "RoadCanvas", Modifier.size(logo.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (Palette.dark) Palette.Ink else Color(0xFF1B2A6B))) { append("Road") }
                    withStyle(SpanStyle(color = Palette.Primary)) { append("Canvas") }
                },
                fontSize = 40.sp, fontWeight = FontWeight.Bold,
            )
            Text(subtitle, fontSize = 19.sp, color = Palette.Muted)
        }
    }
}

/** 시작 화면: 하늘색 배경, 길이 이어지는 언덕, 로고와 '시작하기'. */
@Composable
fun StartScreen(onStart: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF9CCBFF), Color(0xFFDDEEFF), Color(0xFFF4F9FF)))),
    ) {
        // 언덕과 굽은 길(아이콘 그림과 같은 느낌으로 직접 그림)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun hill(color: Color, top: Float, peak: Float, shift: Float) {
                val p = androidx.compose.ui.graphics.Path()
                p.moveTo(0f, h)
                p.lineTo(0f, h * top)
                p.cubicTo(w * (0.25f + shift), h * peak, w * (0.55f + shift), h * (top + 0.05f), w, h * (top - 0.02f))
                p.lineTo(w, h)
                p.close()
                drawPath(p, color)
            }
            hill(Color(0xFF7FB7F2), 0.62f, 0.48f, 0.1f)
            hill(Color(0xFF6CC3B0), 0.70f, 0.56f, -0.1f)
            hill(Color(0xFF8DD4C2), 0.80f, 0.70f, 0.05f)
            val road = androidx.compose.ui.graphics.Path()
            road.moveTo(w * 0.62f, h * 0.66f)
            road.cubicTo(w * 0.45f, h * 0.74f, w * 0.75f, h * 0.84f, w * 0.40f, h)
            drawPath(road, Color.White, style = Stroke(width = 90f, cap = StrokeCap.Round))
            drawPath(road, Color(0xFFDDE8F5), style = Stroke(width = 6f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(30f, 26f))))
            listOf(0.14f to 0.18f, 0.78f to 0.12f, 0.62f to 0.30f).forEach { (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.85f), 46f, Offset(w * x, h * y))
                drawCircle(Color.White.copy(alpha = 0.85f), 60f, Offset(w * x + 60f, h * y - 14f))
                drawCircle(Color.White.copy(alpha = 0.85f), 42f, Offset(w * x + 120f, h * y))
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.logo_roadcanvas), "RoadCanvas", Modifier.size(220.dp))
            Spacer(Modifier.height(18.dp))
            Text("지금, 떠나는 여행이 더 특별해져요!", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2A6B))
            Text("달리면 칠해지는 나만의 여행 지도", fontSize = 22.sp, color = Color(0xFF41507A))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp).size(width = 360.dp, height = 84.dp)
                .clip(RoundedCornerShape(42.dp)).background(Color.White).clickable(onClick = onStart),
            contentAlignment = Alignment.Center,
        ) { Text("시작하기  ›", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2A6B)) }
    }
}

/**
 * 지도에서 시·군·구를 눌렀을 때: 방문 상태, 추천 여행지 3곳(종류 스티커·한 줄 소개·거리), 추천 코스.
 * 거리는 직선 기준이고, 실제 안내는 내비가 한다.
 */
@Composable
fun RegionSheet(
    region: Region,
    trip: TripState,
    course: List<Spot>,
    here: Pair<Double, Double>?,
    onGo: (Spot) -> Unit,
    onCourse: (List<Spot>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visit = trip.visitsAt(Level.SGG)[region.code]
    val owned = trip.stickers.map { it.id }.toSet()
    Column(
        modifier.width(560.dp).clip(RoundedCornerShape(28.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Line, RoundedCornerShape(28.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(region.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text(region.province, fontSize = 17.sp, color = Palette.Muted)
            }
            val (label, color) = when {
                visit == null -> "아직 안 가 봤어요" to Palette.Muted
                visit.state == VisitState.CONFIRMED -> "방문 완료 · ${visit.count}회" to Palette.Green
                else -> "지나간 곳 · ${visit.count}회" to Palette.Primary
            }
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Background).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Text("✕", fontSize = 18.sp, color = Palette.Muted)
            }
        }
        if (course.isEmpty()) {
            Text("이 지역은 추천 여행지 정보가 아직 없어요.", fontSize = 18.sp, color = Palette.Muted)
            return@Column
        }
        Text("추천 여행지", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        course.forEach { sp ->
            val def = StickerCatalog.byKey(sp.kind)
            val got = "spot:${sp.id}" in owned
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (got) Palette.GreenSoft else Palette.Background).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (def != null) StickerImage(def, 56.dp, locked = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sp.name + if (got) "  ✓" else "", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1)
                    Text(
                        listOfNotNull(def?.label, sp.note.ifBlank { null }, here?.let { "직선 ${sp.distanceKmTo(it.first, it.second).toInt()}km" }).joinToString(" · "),
                        fontSize = 15.sp, color = Palette.Muted, maxLines = 2,
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.PrimarySoft).clickable { onGo(sp) }.padding(horizontal = 14.dp, vertical = 10.dp),
                ) { Text("여기로", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Primary) }
            }
        }
        Text("추천 코스  " + course.joinToString(" → ") { it.name }, fontSize = 16.sp, color = Palette.Ink, maxLines = 2)
        Box(
            Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp)).background(Palette.Primary).clickable { onCourse(course) },
            contentAlignment = Alignment.Center,
        ) { Text("이 코스로 여행 계획 만들기  ›", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White) }
    }
}

/** 여행 통계: 전국 시·군·구 방문률, 총 이동 거리, 여행 횟수, 방문 지역, 나의 여행 레벨. */
@Composable
fun StatsScreen(indexes: Map<Level, RegionIndex>, trip: TripState, trips: List<Trip>, onClose: () -> Unit) {
    val sgg = indexes.getValue(Level.SGG)
    val visited = trip.visitsAt(Level.SGG).size
    val rate = visited.toFloat() / sgg.regions.size
    val km = trips.sumOf { it.distanceM } / 1000
    val (lv, toNext) = TripRecorder.levelOf(km)
    val owned = trip.stickers.count { StickerCatalog.ofStickerId(it.id) != null }
    Column(
        Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📊", fontSize = 36.sp)
            Spacer(Modifier.width(12.dp))
            Text("여행 통계", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(16.dp))
                    .clickable(onClick = onClose).padding(horizontal = 28.dp, vertical = 16.dp),
            ) { Text("← 지도로", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Primary) }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // 방문률 원
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(Palette.Card).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(300.dp)) {
                        drawArc(Palette.Line, -90f, 360f, false, style = Stroke(34f, cap = StrokeCap.Round))
                        drawArc(Brush.sweepGradient(listOf(Palette.Teal, Palette.Primary, Palette.Teal)), -90f, 360f * rate, false, style = Stroke(34f, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StickerCatalog.byKey("mountain")?.let { StickerImage(it, 64.dp) }
                        Text("${(rate * 100).toInt()}%", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                        Text("전국 시·군·구 방문률", fontSize = 18.sp, color = Palette.Muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("$visited / ${sgg.regions.size}곳", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatRow("🚗", "총 이동 거리", TripRecorder.formatKm(km), Modifier.weight(1f))
                StatRow("📅", "여행 횟수", "${trips.count { it.endMs != null }}회", Modifier.weight(1f))
                StatRow("⭐", "방문 지역", "${visited}개 · 시·도 ${trip.visitsAt(Level.SIDO).size}곳", Modifier.weight(1f))
                StatRow("🏅", "모은 스티커", "$owned / ${StickerCatalog.collectible.size}", Modifier.weight(1f))
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(Palette.Card).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("🏆  나의 여행 레벨", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text("Lv.$lv", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                LinearProgressIndicator(
                    progress = { TripRecorder.levelProgress(km) },
                    color = Palette.Amber, trackColor = Palette.Line,
                    modifier = Modifier.fillMaxWidth().height(14.dp).clip(CircleShape),
                )
                Text(if (toNext > 0) "다음 레벨까지 ${TripRecorder.formatKm(toNext)}" else "최고 레벨이에요!", fontSize = 20.sp, color = Palette.Muted)
                Spacer(Modifier.weight(1f))
                StickerCatalog.byKey(if (lv >= 3) "level_up" else "hiking")?.let { Box(Modifier.align(Alignment.End)) { StickerImage(it, 150.dp) } }
            }
        }
    }
}

@Composable
private fun StatRow(icon: String, label: String, value: String, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Card).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(Palette.PrimarySoft), contentAlignment = Alignment.Center) { Text(icon, fontSize = 30.sp) }
        Spacer(Modifier.width(18.dp))
        Column {
            Text(label, fontSize = 18.sp, color = Palette.Muted)
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        }
    }
}
