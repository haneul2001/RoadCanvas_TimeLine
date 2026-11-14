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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

/** 스티커 그림. [locked]면 흐리게(아직 못 받음). */
@Composable
fun StickerImage(def: com.carnect.roadcanvas.sticker.StickerDef, size: androidx.compose.ui.unit.Dp, locked: Boolean = false) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(def.res),
        contentDescription = def.label,
        modifier = Modifier.size(size),
        alpha = if (locked) 0.28f else 1f,
        colorFilter = if (locked) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null,
    )
}

/** 도감 탭: 전체 + 모으는 분류들 + 꾸미기용(날씨·감정). */
private typealias DexFilter = (com.carnect.roadcanvas.sticker.StickerDef) -> Boolean

private val DEX_TABS: List<Pair<String, DexFilter>> = buildList {
    add("전체" to { d: com.carnect.roadcanvas.sticker.StickerDef -> d.category.collectible })
    com.carnect.roadcanvas.sticker.StickerCategory.entries.filter { it.collectible }.forEach { c ->
        add(c.label to { d: com.carnect.roadcanvas.sticker.StickerDef -> d.category == c })
    }
    add("꾸미기용" to { d: com.carnect.roadcanvas.sticker.StickerDef -> !d.category.collectible })
}

/**
 * 스티커 도감. 모으는 스티커는 실제 방문 기록으로만 얻고(못 받은 것은 흐리게, 누르면 얻는 방법),
 * 날씨·감정 스티커는 지도 꾸미기에서 늘 쓸 수 있다. 오른쪽은 지역 탐험 미션.
 */
@Composable
fun CollectionScreen(indexes: Map<Level, RegionIndex>, trip: TripState, onClose: () -> Unit) {
    val owned = trip.stickers.mapNotNull { com.carnect.roadcanvas.sticker.StickerCatalog.ofStickerId(it.id)?.key }.toSet()
    val collectible = com.carnect.roadcanvas.sticker.StickerCatalog.collectible
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var picked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.carnect.roadcanvas.sticker.StickerDef?>(null) }
    val shown = com.carnect.roadcanvas.sticker.StickerCatalog.all.filter(DEX_TABS[tab].second)
    val places = trip.stickers.filter { it.id.startsWith("spot:") || it.isPlace }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👑", fontSize = 38.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("스티커 도감", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text("다양한 스티커로 나만의 여행 지도를 완성해 보세요", fontSize = 19.sp, color = Palette.Muted)
            }
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(16.dp))
                    .clickable(onClick = onClose).padding(horizontal = 28.dp, vertical = 16.dp),
            ) { Text("← 지도로", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Primary) }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier.weight(1.5f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DEX_TABS.size) { i ->
                        val sel = i == tab
                        Box(
                            Modifier.clip(RoundedCornerShape(22.dp)).background(if (sel) Palette.Primary else Palette.Background)
                                .clickable { tab = i; picked = null }.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) { Text(DEX_TABS[i].first, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else Palette.Ink) }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(118.dp), modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown) { d ->
                        val have = !d.category.collectible || d.key in owned
                        Column(
                            Modifier.clip(RoundedCornerShape(18.dp)).background(if (picked == d) Palette.PrimarySoft else Palette.Background)
                                .clickable { picked = d }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            StickerImage(d, 76.dp, locked = !have)
                            Text(if (have) d.label else "?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (have) Palette.Ink else Palette.Muted, maxLines = 1)
                        }
                    }
                }
                // 고른 스티커 설명, 아래는 보유 수
                picked?.let { d ->
                    val have = !d.category.collectible || d.key in owned
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.PrimarySoft).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StickerImage(d, 56.dp, locked = !have)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${d.label} · ${d.category.label}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                            Text(if (have && d.category.collectible) "받았어요!" else d.how, fontSize = 16.sp, color = Palette.Muted)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Background).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🏆", fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("보유 스티커 수", fontSize = 16.sp, color = Palette.Muted)
                        LinearProgressIndicator(
                            progress = { owned.size.toFloat() / collectible.size },
                            color = Palette.Amber, trackColor = Palette.Line,
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("${owned.size} / ${collectible.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("지역 탐험 미션", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                missions(indexes, trip).forEach { m -> MissionRow(m) }
                Text("다녀온 여행지 ${places.size}곳", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text(places.sortedByDescending { it.timeMs }.take(12).joinToString(" · ") { it.title }.ifEmpty { "추천 여행지 근처를 지나며 방문이 확정되면 쌓여요." }, fontSize = 16.sp, color = Palette.Muted)
            }
        }
    }
}

/** 새 스티커를 받았을 때 띄우는 팝업. */
@Composable
fun StickerAwardDialog(defs: List<com.carnect.roadcanvas.sticker.StickerDef>, onDismiss: () -> Unit) {
    val first = defs.first()
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(620.dp).clip(RoundedCornerShape(32.dp)).background(Brush.verticalGradient(listOf(Color(0xFFEAF3FF), Color.White)))
                .clickable(enabled = false) {}.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFE08A)).padding(horizontal = 28.dp, vertical = 10.dp)) {
                Text("새로운 스티커를 획득했어요!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A4B00))
            }
            StickerImage(first, 200.dp)
            Text(if (first.category == com.carnect.roadcanvas.sticker.StickerCategory.ACHIEVE) first.label else "${first.label} 스티커", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            Text("${first.category.label} 스티커 · 스티커 도감에 담았어요", fontSize = 18.sp, color = Palette.Muted, textAlign = TextAlign.Center)
            if (defs.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    defs.drop(1).take(6).forEach { StickerImage(it, 56.dp) }
                    if (defs.size > 7) Text("+${defs.size - 7}", fontSize = 20.sp, color = Palette.Muted)
                }
                Text("함께 받은 스티커 ${defs.size - 1}개", fontSize = 16.sp, color = Palette.Muted)
            }
            Box(
                Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(Palette.Primary).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("확인하기  ›", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        }
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
