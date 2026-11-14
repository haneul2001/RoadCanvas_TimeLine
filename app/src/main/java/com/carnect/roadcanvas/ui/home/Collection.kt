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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnect.roadcanvas.data.Landmarks
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.TripState

private data class Mission(val title: String, val caption: String, val done: Int, val total: Int)

/** 동해안 7번 국도가 지나는 시·군(남쪽부터). (시·도 이름 앞부분, 시·군·구 이름) */
val ROUTE_7 = listOf(
    "부산" to "기장군", "울산" to "북구", "경상북도" to "경주시", "경상북도" to "포항시북구", "경상북도" to "영덕군", "경상북도" to "울진군",
    "강원" to "삼척시", "강원" to "동해시", "강원" to "강릉시", "강원" to "양양군", "강원" to "속초시", "강원" to "고성군",
)

/** 미션은 실제 방문 기록과 방문 확정으로 받은 스티커로만 계산한다. */
private fun missions(indexes: Map<Level, RegionIndex>, trip: TripState): List<Mission> {
    val sido = indexes.getValue(Level.SIDO)
    val sgg = indexes.getValue(Level.SGG)
    val visitedSgg = trip.visitsAt(Level.SGG).keys
    val gangwon = sido.regions.firstOrNull { it.name.startsWith("강원") }
    val eastCoast = Landmarks.all.filter { it.group == Landmarks.EAST_COAST }
    return listOfNotNull(
        gangwon?.let { g ->
            Mission("${g.name} ${sgg.countIn(g.code)}개 시·군 방문", "강원의 모든 시·군을 지나 보세요", visitedSgg.count { sgg.byCode(it)?.parent == g.code }, sgg.countIn(g.code))
        },
        ROUTE_7.mapNotNull { (p, n) -> sgg.regions.firstOrNull { it.province.startsWith(p) && it.name == n } }.let { stops ->
            Mission("동해안 7번 국도 완주", "부산 기장 ~ 강원 고성, ${stops.size}개 시·군을 지나 보세요", stops.count { it.code in visitedSgg }, stops.size)
        },
        Mission("동해안 여행지 탐험", eastCoast.joinToString(" · ") { it.name }, eastCoast.count { lm -> trip.stickers.any { it.id == "place:${lm.id}" } }, eastCoast.size),
        Mission("전국 시·도 정복", "모든 시·도에 한 번씩", trip.visitsAt(Level.SIDO).size, sido.regions.size),
        Mission("전국 시·군·구 정복", "대한민국 구석구석", visitedSgg.size, sgg.regions.size),
    )
}

@Composable
fun CollectionScreen(indexes: Map<Level, RegionIndex>, trip: TripState, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = Palette.Amber, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Collection", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text("방문을 확정한 지역과 근처를 지난 명소의 스티커, 지역 탐험 미션", fontSize = 20.sp, color = Palette.Muted)
            }
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(16.dp))
                    .clickable(onClick = onClose).padding(horizontal = 28.dp, vertical = 16.dp),
            ) { Text("← 지도로", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Primary) }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier.weight(1.4f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Palette.Card).padding(24.dp),
            ) {
                Text("스티커 ${trip.stickers.size}개", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Spacer(Modifier.height(16.dp))
                if (trip.stickers.isEmpty()) {
                    Text("머문 곳에서 방문을 확정하면 스티커가 생겨요.", fontSize = 20.sp, color = Palette.Muted)
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(170.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(trip.stickers.sortedByDescending { it.timeMs }) { s -> StickerTile(s.title, s.isPlace) }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Palette.Card).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("지역 탐험 미션", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                missions(indexes, trip).forEach { m -> MissionRow(m) }
            }
        }
    }
}

@Composable
private fun StickerTile(title: String, place: Boolean) {
    val accent = if (place) Palette.Amber else Palette.Teal
    Column(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).background(if (place) Palette.AmberSoft else Palette.TealSoft)
            .border(2.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(if (place) Icons.Filled.Star else Icons.Filled.Place, null, tint = accent, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1, textAlign = TextAlign.Center)
        Text(if (place) "장소 스티커" else "지역 스티커", fontSize = 14.sp, color = Palette.Muted)
    }
}

@Composable
private fun MissionRow(m: Mission) {
    val progress = if (m.total > 0) m.done.toFloat() / m.total else 0f
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(m.title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${m.done}/${m.total} · ${(progress * 100).toInt()}%", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = if (m.done >= m.total) Palette.Green else Palette.Primary)
        }
        Text(m.caption, fontSize = 15.sp, color = Palette.Muted, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = if (m.done >= m.total) Palette.Green else Palette.Primary,
            trackColor = Palette.Line,
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
        )
    }
}

/** 지도 위, 머문 곳의 방문 확인을 묻는 카드. 정차 중에만 보인다. */
@Composable
fun ConfirmStayCard(regionName: String, onConfirm: () -> Unit, onLater: () -> Unit, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).border(2.dp, Palette.Amber, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, null, tint = Palette.Amber, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("${regionName}에 머물렀어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            Text("방문으로 기록할까요?", fontSize = 16.sp, color = Palette.Muted)
        }
        Spacer(Modifier.width(18.dp))
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Amber).clickable(onClick = onConfirm).padding(horizontal = 18.dp, vertical = 12.dp),
        ) { Text("방문 확정", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onLater).padding(horizontal = 14.dp, vertical = 12.dp),
        ) { Text("나중에", fontSize = 18.sp, color = Palette.Muted) }
    }
}
