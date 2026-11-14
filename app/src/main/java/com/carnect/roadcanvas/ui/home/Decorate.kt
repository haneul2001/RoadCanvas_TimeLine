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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnect.roadcanvas.deco.CustomSticker
import com.carnect.roadcanvas.deco.DecorRepo
import com.carnect.roadcanvas.deco.Decoration
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.Sticker
import com.carnect.roadcanvas.trip.TripState

/** 꾸미기 색(펜·도형·스티커 테두리). */
val DECO_COLORS = listOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFF212121, 0xFFFFFFFF).map { it.toInt() }

/** 커스텀 스티커에 고를 수 있는 이모지. */
val DECO_EMOJIS = listOf("🏖️", "⛰️", "🍜", "☕", "🏕️", "🌸", "🌊", "📸", "🚗", "🎉", "❤️", "⭐", "🍦", "🏯", "🎡", "🐟")

private val WIDTHS = listOf(4f to "가늘게", 8f to "보통", 14f to "굵게")

/** 받은 스티커(업적)를 지도에 붙일 모양으로. 장소는 별, 지역은 메달. */
fun Sticker.asDeco(): Triple<String, String, Int> =
    Triple(if (isPlace) "⭐" else "🏅", title, if (isPlace) 0xFFF5A524.toInt() else 0xFF2BB5A4.toInt())

/**
 * 지도 꾸미기(정차 중). 지금까지 다닌 지도 위에 펜으로 낙서하고, 지우개로 지우고, 도형을 그리고,
 * 받은 업적 스티커나 직접 만든 스티커를 붙인다. 꾸민 것은 지도 좌표에 붙어 확대·이동해도 그 자리에 있다.
 */
@Composable
fun DecorateScreen(
    indexes: Map<Level, RegionIndex>,
    map: TripState,
    decor: DecorRepo,
    decorations: List<Decoration>,
    custom: List<CustomSticker>,
    onClose: () -> Unit,
) {
    var tool by remember { mutableStateOf(DecoTool.PEN) }
    var color by remember { mutableIntStateOf(DECO_COLORS[0]) }
    var width by remember { mutableFloatStateOf(8f) }
    var sticker by remember { mutableStateOf<Triple<String, String, Int>?>(map.stickers.firstOrNull()?.asDeco()) }
    var confirmClear by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(Level.SGG) }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎨", fontSize = 36.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("지도 꾸미기", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text("지금까지 다닌 지도에 낙서하고 도형을 그리고 스티커를 붙여요. 꾸민 것은 지도 위치에 붙어 기기에 저장돼요.", fontSize = 18.sp, color = Palette.Muted)
            }
            ToolButton("← 지도로", false, onClose)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // 도구
            Column(
                Modifier.width(230.dp).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Palette.Card)
                    .border(1.dp, Palette.Line, RoundedCornerShape(22.dp)).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Section("도구")
                DecoTool.entries.forEach { t -> ToolButton("${toolIcon(t)}  ${t.label}", tool == t, { tool = t }, Modifier.fillMaxWidth()) }
                Section("색")
                DECO_COLORS.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(Color(c))
                                    .border(if (c == color) 4.dp else 1.dp, if (c == color) Palette.Primary else Palette.Line, CircleShape)
                                    .clickable { color = c },
                            )
                        }
                    }
                }
                Section("굵기")
                WIDTHS.forEach { (w, label) -> ToolButton(label, width == w, { width = w }, Modifier.fillMaxWidth()) }
                Spacer(Modifier.height(4.dp))
                ToolButton("↶  되돌리기", false, { decor.undo() }, Modifier.fillMaxWidth())
                if (confirmClear) {
                    Text("꾸민 것을 모두 지울까요?", fontSize = 16.sp, color = Palette.Orange)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToolButton("지우기", true, { decor.clear(); confirmClear = false })
                        ToolButton("취소", false, { confirmClear = false })
                    }
                } else {
                    ToolButton("🗑  모두 지우기", false, { confirmClear = true }, Modifier.fillMaxWidth())
                }
            }
            // 지도
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Palette.Sea)) {
                val index = indexes.getValue(level)
                TripMap(
                    index, map, interactive = true, Modifier.fillMaxSize(),
                    decorations = decorations,
                    editor = DecoEditor(tool, color, width, sticker, onAdd = decor::add, onErase = decor::remove),
                )
                Row(
                    Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Surface.copy(alpha = 0.95f)).padding(4.dp),
                ) {
                    Level.entries.forEach { l -> ToolButton(l.label, l == level, { level = l }) }
                }
                Text(
                    when (tool) {
                        DecoTool.MOVE -> "끌어서 옮기고 두 손가락으로 확대해요"
                        DecoTool.PEN -> "손가락으로 그어서 낙서해요"
                        DecoTool.ERASER -> "지우고 싶은 낙서·도형·스티커를 문질러요"
                        DecoTool.RECT, DecoTool.CIRCLE, DecoTool.ARROW -> "끌어서 ${tool.label}를 그려요"
                        DecoTool.STICKER -> if (sticker == null) "오른쪽에서 붙일 스티커를 골라요" else "붙일 곳을 눌러요: " + sticker!!.let { st -> if (st.first.startsWith("img:")) com.carnect.roadcanvas.sticker.StickerCatalog.byKey(st.first.removePrefix("img:"))?.label.orEmpty() else "${st.first} ${st.second}" }
                    },
                    fontSize = 17.sp, color = Palette.Ink,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(14.dp))
                        .background(Palette.Surface.copy(alpha = 0.92f)).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            // 스티커
            StickerPanel(
                earned = map.stickers,
                custom = custom,
                selected = sticker,
                onPick = { sticker = it; tool = DecoTool.STICKER },
                onCreate = { e, t, c -> decor.addCustom(e, t, c).let { s -> sticker = Triple(s.emoji, s.text, s.color); tool = DecoTool.STICKER } },
                onDeleteCustom = decor::removeCustom,
                modifier = Modifier.width(420.dp).fillMaxHeight(),
            )
        }
    }
}

private fun toolIcon(t: DecoTool) = when (t) {
    DecoTool.MOVE -> "✋"
    DecoTool.PEN -> "✏️"
    DecoTool.ERASER -> "🧽"
    DecoTool.RECT -> "▭"
    DecoTool.CIRCLE -> "◯"
    DecoTool.ARROW -> "➜"
    DecoTool.STICKER -> "⭐"
}

@Composable
private fun Section(title: String) = Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Muted)

@Composable
private fun ToolButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Palette.Primary else Palette.Background)
            .border(1.dp, if (selected) Palette.Primary else Palette.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Palette.Ink, maxLines = 1) }
}

@Composable
private fun StickerPanel(
    earned: List<Sticker>,
    custom: List<CustomSticker>,
    selected: Triple<String, String, Int>?,
    onPick: (Triple<String, String, Int>) -> Unit,
    onCreate: (String, String, Int) -> Unit,
    onDeleteCustom: (Long) -> Unit,
    modifier: Modifier,
) {
    var making by remember { mutableStateOf(false) }
    var emoji by remember { mutableStateOf(DECO_EMOJIS[0]) }
    var text by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(DECO_COLORS[4]) }

    LazyColumn(
        modifier.clip(RoundedCornerShape(22.dp)).background(Palette.Card).border(1.dp, Palette.Line, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("내 스티커 ${custom.size}개", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.weight(1f))
                ToolButton(if (making) "닫기" else "+ 만들기", making, { making = !making })
            }
        }
        if (making) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Background).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("그림", fontSize = 16.sp, color = Palette.Muted)
                    DECO_EMOJIS.chunked(8).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { e ->
                                Box(
                                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(if (e == emoji) Palette.PrimarySoft else Color.White)
                                        .border(if (e == emoji) 2.dp else 1.dp, if (e == emoji) Palette.Primary else Palette.Line, RoundedCornerShape(10.dp))
                                        .clickable { emoji = e },
                                    contentAlignment = Alignment.Center,
                                ) { Text(e, fontSize = 22.sp) }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(DecorRepo.MAX_TEXT) },
                        label = { Text("글 (${DecorRepo.MAX_TEXT}자까지, 비워도 돼요)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("색", fontSize = 16.sp, color = Palette.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DECO_COLORS.forEach { c ->
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                    .border(if (c == color) 3.dp else 1.dp, if (c == color) Palette.Primary else Palette.Line, CircleShape)
                                    .clickable { color = c },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StickerChip(Triple(emoji, text, color), false, {}, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        ToolButton("만들기", true, { onCreate(emoji, text.trim(), color); text = ""; making = false })
                    }
                }
            }
        }
        if (custom.isEmpty() && !making) item { Text("이모지와 짧은 글로 나만의 스티커를 만들 수 있어요.", fontSize = 16.sp, color = Palette.Muted) }
        items(custom.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    val d = Triple(s.emoji, s.text, s.color)
                    Box(Modifier.weight(1f)) {
                        StickerChip(d, selected == d, { onPick(d) }, Modifier.fillMaxWidth())
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Palette.Surface)
                                .border(1.dp, Palette.Line, CircleShape).clickable { onDeleteCustom(s.id) },
                            contentAlignment = Alignment.Center,
                        ) { Text("×", fontSize = 16.sp, color = Palette.Muted) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        // 그림 스티커: 모은 도감 스티커 + 늘 쓸 수 있는 날씨·감정 스티커
        val ownedKeys = earned.mapNotNull { com.carnect.roadcanvas.sticker.StickerCatalog.ofStickerId(it.id)?.key }.toSet()
        val pictures = com.carnect.roadcanvas.sticker.StickerCatalog.all.filter { !it.category.collectible || it.key in ownedKeys }
            .map { Triple("img:${it.key}", "", 0xFF1E88E5.toInt()) }
        item { Spacer(Modifier.height(6.dp)); Text("그림 스티커 ${pictures.size}개", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
        item { Text("모은 도감 스티커와 날씨·감정 스티커를 붙일 수 있어요.", fontSize = 15.sp, color = Palette.Muted) }
        items(pictures.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d -> StickerChip(d, selected == d, { onPick(d) }, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item { Spacer(Modifier.height(6.dp)); Text("방문 스티커 ${earned.size}개", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
        if (earned.isEmpty()) item { Text("방문을 확정하면 스티커를 받아요.", fontSize = 16.sp, color = Palette.Muted) }
        items(earned.filter { com.carnect.roadcanvas.sticker.StickerCatalog.ofStickerId(it.id) == null }.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s -> val d = s.asDeco(); StickerChip(d, selected == d, { onPick(d) }, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StickerChip(s: Triple<String, String, Int>, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) Palette.PrimarySoft else Color.White)
            .border(if (selected) 3.dp else 1.dp, if (selected) Palette.Primary else Color(s.third), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val def = if (s.first.startsWith("img:")) com.carnect.roadcanvas.sticker.StickerCatalog.byKey(s.first.removePrefix("img:")) else null
        if (def != null) StickerImage(def, 54.dp) else Text(s.first, fontSize = 30.sp)
        if (s.second.isNotBlank()) Text(s.second, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1, textAlign = TextAlign.Center)
    }
}
