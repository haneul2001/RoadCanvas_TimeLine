package com.carnect.roadcanvas.ui.home

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.rememberUpdatedState
import com.carnect.roadcanvas.deco.Decoration
import com.carnect.roadcanvas.deco.Geo
import com.carnect.roadcanvas.deco.ShapeKind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.carnect.roadcanvas.data.Landmarks
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.Region
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.EventKind
import com.carnect.roadcanvas.trip.TrackPoint
import com.carnect.roadcanvas.trip.TripRecorder
import com.carnect.roadcanvas.trip.TripState
import com.carnect.roadcanvas.trip.VisitState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** 지도에 보일 위경도 범위. */
data class Viewport(val minLon: Double, val maxLon: Double, val minLat: Double, val maxLat: Double) {
    fun intersects(r: Region) = r.maxLon >= minLon && r.minLon <= maxLon && r.maxLat >= minLat && r.minLat <= maxLat

    companion object {
        val KOREA = Viewport(124.5, 131.0, 33.0, 38.7)

        /** 현재 위치를 가운데 두고, 등록된 경로의 들를 곳도 모두 보이게. */
        fun focus(center: Geo, others: List<Geo>): Viewport {
            val pts = others + center
            val halfW = max(pts.maxOf { kotlin.math.abs(it.second - center.second) } * 1.25, 0.55)
            val halfH = max(pts.maxOf { kotlin.math.abs(it.first - center.first) } * 1.25, 0.38)
            return Viewport(center.second - halfW, center.second + halfW, center.first - halfH, center.first + halfH)
        }

        /** 이번 여행이 지난 지역과 경로가 모두 보이도록 넉넉하게 잡는다. */
        fun around(regions: List<Region>, track: List<TrackPoint>): Viewport {
            if (regions.isEmpty() && track.isEmpty()) return KOREA
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            regions.forEach { minLon = min(minLon, it.minLon); maxLon = max(maxLon, it.maxLon); minLat = min(minLat, it.minLat); maxLat = max(maxLat, it.maxLat) }
            track.forEach { minLon = min(minLon, it.lon); maxLon = max(maxLon, it.lon); minLat = min(minLat, it.lat); maxLat = max(maxLat, it.lat) }
            // 너무 좁게 확대되지 않도록 최소 범위를 둔다.
            val cx = (minLon + maxLon) / 2
            val cy = (minLat + maxLat) / 2
            val halfW = max((maxLon - minLon) / 2 * 1.35, 0.9)
            val halfH = max((maxLat - minLat) / 2 * 1.35, 0.6)
            return Viewport(cx - halfW, cx + halfW, cy - halfH, cy + halfH)
        }
    }
}

/** [zoom]·[pan]은 사용자가 끌거나 벌려서 바꾼 화면 변환(기본 1배, 이동 없음). */
private class Projection(size: Size, v: Viewport, private val zoom: Float = 1f, private val pan: Offset = Offset.Zero) {
    private val lonScale = cos(Math.toRadians((v.minLat + v.maxLat) / 2))
    private val w = (v.maxLon - v.minLon) * lonScale
    private val h = v.maxLat - v.minLat
    // 범위 전체가 화면에 들어오게 맞춘다(여행 경로가 잘리지 않도록).
    private val scale = min(size.width / w, size.height / h)
    private val offsetX = (size.width - w * scale) / 2
    private val offsetY = (size.height - h * scale) / 2
    private val minLon = v.minLon
    private val maxLat = v.maxLat
    private val ls = lonScale

    fun x(lon: Double) = (offsetX + (lon - minLon) * ls * scale).toFloat() * zoom + pan.x
    fun y(lat: Double) = (offsetY + (maxLat - lat) * scale).toFloat() * zoom + pan.y
    fun at(g: Geo) = Offset(x(g.second), y(g.first))

    /** 화면 좌표 → 위도·경도(꾸미기를 지도에 붙일 때). */
    fun geo(o: Offset): Geo {
        val lon = ((o.x - pan.x) / zoom - offsetX) / (ls * scale) + minLon
        val lat = maxLat - ((o.y - pan.y) / zoom - offsetY) / scale
        return lat to lon
    }
}

/** 지도 꾸미기 도구. */
enum class DecoTool(val label: String) {
    MOVE("이동"), PEN("펜"), ERASER("지우개"), RECT("네모"), CIRCLE("동그라미"), ARROW("화살표"), STICKER("스티커")
}

/** 꾸미기 모드 설정. [sticker]는 붙일 스티커(이모지, 글, 색). */
class DecoEditor(
    val tool: DecoTool,
    val color: Int,
    val width: Float,
    val sticker: Triple<String, String, Int>?,
    val onAdd: (Decoration) -> Unit,
    val onErase: (Set<Long>) -> Unit,
)

private const val MIN_ZOOM = 0.6f
private const val MAX_ZOOM = 8f

/** 방문 순서대로 빨강 → 보라를 돌아가며 칠한다. 머문 곳·확정은 같은 색을 진하게. */
/** 지도 색 테마(정차 중 설정에서 고름). 방문 순서마다 [hueStart]부터 [hueRange] 안에서 색을 바꾼다. */
enum class MapTheme(val label: String, val hueStart: Float, val hueRange: Float, val step: Float) {
    RAINBOW("무지개", 0f, 300f, 26f),
    OCEAN("바다", 175f, 75f, 11f),
    FOREST("숲", 70f, 90f, 13f),
    SUNSET("노을", 330f, 75f, 11f),
    SINGLE("단색", 215f, 0f, 0f),
}

/** 지금 지도 색 테마. 바꾸면 지도와 범례가 바로 다시 그려진다. */
object MapColors {
    var theme by androidx.compose.runtime.mutableStateOf(MapTheme.RAINBOW)
}

fun rainbow(order: Int, state: VisitState, t: MapTheme = MapColors.theme): Color {
    val hue = if (t.hueRange == 0f) t.hueStart else (t.hueStart + (order * t.step) % t.hueRange) % 360f
    return when (state) {
        VisitState.PASSED -> Color.hsv(hue, 0.38f, 0.98f)
        VisitState.STAY_CANDIDATE -> Color.hsv(hue, 0.62f, 0.93f)
        VisitState.CONFIRMED -> Color.hsv(hue, 0.80f, 0.85f)
    }
}

/**
 * 여행 지도. [interactive]일 때(정차 중) 한 손가락으로 끌어 옮기고, 두 손가락으로 확대·축소하며,
 * 두 번 탭하면 이번 여행 범위로 돌아온다.
 */
@Composable
fun TripMap(
    index: RegionIndex,
    trip: TripState,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    /** 타임랩스 재생 중이면 그 순간의 상태. 보이는 범위는 여행 전체([trip]) 기준으로 고정한다. */
    frame: TripState? = null,
    /** 지도에 붙인 꾸미기(낙서·도형·스티커). */
    decorations: List<Decoration> = emptyList(),
    /** 꾸미기 모드면 손가락으로 그린다(이동 도구일 때만 끌어 옮김). */
    editor: DecoEditor? = null,
    /** 메인 화면: 이 위치를 가운데 두고 '현재 위치' 표시를 단다. */
    focus: Geo? = null,
    /** 등록된 경로(들를 곳 이름, 좌표). 지금 위치부터 점선으로 잇고 번호를 단다. */
    route: List<Pair<String, Geo>> = emptyList(),
) {
    val tripRegions = trip.trip?.let { t -> trip.visitsAt(index.level).filterValues { it.tripId == t.id }.keys.mapNotNull(index::byCode) }.orEmpty()
    val shown = frame ?: trip
    val visits = shown.visitsAt(index.level)
    val viewport = if (focus != null) {
        // 위치가 조금씩 바뀔 때마다 화면이 흔들리지 않게 0.05° 단위로 묶어 다시 잡는다.
        val key = (focus.first * 20).toInt() to (focus.second * 20).toInt()
        remember(key, route) { Viewport.focus(focus, route.map { it.second }) }
    } else {
        remember(tripRegions.size, trip.track.size / 20) { Viewport.around(tripRegions, trip.track) }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // 주행을 시작하면 사용자가 옮겨 둔 화면을 이번 여행 범위로 되돌린다.
    LaunchedEffect(interactive) {
        if (!interactive) {
            zoom = 1f
            pan = Offset.Zero
        }
    }
    // 그리는 중인 낙서·도형(손을 떼면 [DecoEditor.onAdd]로 저장)
    var draft by remember { mutableStateOf<Decoration?>(null) }
    val currentViewport by rememberUpdatedState(viewport)
    val currentDecos by rememberUpdatedState(decorations)
    val drawing = editor != null && editor.tool != DecoTool.MOVE
    val gestures = if (drawing) {
        val ed = editor!!
        Modifier.pointerInput(ed.tool, ed.color, ed.width, ed.sticker) {
            awaitEachGesture {
                val proj = Projection(Size(size.width.toFloat(), size.height.toFloat()), currentViewport, zoom, pan)
                val down = awaitFirstDown()
                val start = down.position
                val id = System.currentTimeMillis()
                when (ed.tool) {
                    DecoTool.PEN -> draft = Decoration.Stroke(id, listOf(proj.geo(start)), ed.color, ed.width)
                    DecoTool.RECT, DecoTool.CIRCLE, DecoTool.ARROW -> {
                        val kind = ShapeKind.valueOf(ed.tool.name)
                        draft = Decoration.Shape(id, kind, proj.geo(start), proj.geo(start), ed.color, ed.width)
                    }
                    DecoTool.ERASER -> ed.onErase(hits(proj, currentDecos, start))
                    else -> Unit
                }
                var last = start
                var moved = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull() ?: break
                    if (!ch.pressed) break
                    val pos = ch.position
                    if ((pos - last).getDistance() < 4f) continue
                    moved = moved || (pos - start).getDistance() > 12f
                    last = pos
                    ch.consume()
                    when (val d = draft) {
                        is Decoration.Stroke -> draft = d.copy(points = d.points + proj.geo(pos))
                        is Decoration.Shape -> draft = d.copy(b = proj.geo(pos))
                        else -> if (ed.tool == DecoTool.ERASER) ed.onErase(hits(proj, currentDecos, pos))
                    }
                }
                when (val d = draft) {
                    is Decoration.Stroke -> if (d.points.size >= 2) ed.onAdd(d)
                    is Decoration.Shape -> if (moved) ed.onAdd(d)
                    else -> if (ed.tool == DecoTool.STICKER && !moved && ed.sticker != null) {
                        val (emoji, text, color) = ed.sticker
                        ed.onAdd(Decoration.Sticker(id, proj.geo(start), emoji, text, color))
                    }
                }
                draft = null
            }
        }
    } else if (interactive) {
        Modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    val applied = newZoom / zoom
                    // 손가락 사이 지점을 중심으로 확대되도록 이동량을 보정한다.
                    pan = (pan - centroid) * applied + centroid + panChange
                    zoom = newZoom
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    zoom = 1f
                    pan = Offset.Zero
                })
            }
    } else {
        Modifier
    }
    // 색 순서는 여행 전체 기준으로 정해 두어 재생 중에도 색이 바뀌지 않게 한다.
    val allVisits = trip.visitsAt(index.level)
    val order = remember(allVisits) {
        allVisits.entries.sortedBy { it.value.firstMs }.mapIndexed { i, e -> e.key to i }.toMap()
    }
    // 읍·면·동은 구역이 촘촘해 경계선을 가늘게
    val borderWidth = if (index.level == Level.DONG) 0.5f else 1.2f
    val provinceCenters = remember(index) {
        index.regions.groupBy { it.provinceCode }.mapValues { (_, rs) ->
            Pair(rs.map { (it.minLat + it.maxLat) / 2 }.average(), rs.map { (it.minLon + it.maxLon) / 2 }.average()) to rs.first().province
        }
    }
    val pathCache = remember(index) { HashMap<Pair<Size, Viewport>, List<Pair<Region, Path>>>() }

    Canvas(modifier.clipToBounds().then(gestures)) {
        // 경계 Path는 기본 화면(1배) 기준으로 한 번 만들고, 끌기·확대는 변환으로 처리한다.
        val base = Projection(size, viewport)
        val paths = pathCache.getOrPut(size to viewport) {
            if (pathCache.size > 4) pathCache.clear()
            // 끌어서 옮기면 범위 밖도 보이므로 전국을 다 만든다.
            index.regions.map { region ->
                val path = Path()
                region.rings.forEach { ring ->
                    path.moveTo(base.x(ring[0]), base.y(ring[1]))
                    for (i in 2 until ring.size step 2) path.lineTo(base.x(ring[i]), base.y(ring[i + 1]))
                    path.close()
                }
                region to path
            }
        }
        drawRect(Palette.Sea)
        withTransform({
            translate(pan.x, pan.y)
            scale(zoom, zoom, pivot = Offset.Zero)
        }) {
            paths.forEach { (region, path) ->
                val visit = visits[region.code]
                drawPath(path, if (visit == null) Palette.Unvisited else rainbow(order[region.code] ?: 0, visit.state))
                // 확대해도 경계선 두께는 그대로 보이게
                drawPath(path, Color.White, style = Stroke(width = borderWidth / zoom))
            }
        }
        // 선·글자·핀은 확대와 상관없이 같은 크기로 그린다.
        val projection = Projection(size, viewport, zoom, pan)
        drawProvinceLabels(projection, provinceCenters.values, viewport, zoom)
        drawTrack(projection, shown.track)
        drawStickerStars(projection, shown)
        if (focus == null) drawPins(projection, shown, index.level)
        drawDecorations(projection, decorations + listOfNotNull(draft))
        if (route.isNotEmpty()) drawRoute(projection, focus, route)
        focus?.let { drawHere(projection, it) }
    }
}

/** 등록된 경로: 지금 위치 → 들를 곳 순서로 점선, 번호 핀과 이름. */
private fun DrawScope.drawRoute(p: Projection, from: Geo?, route: List<Pair<String, Geo>>) {
    val pts = listOfNotNull(from) + route.map { it.second }
    if (pts.size >= 2) {
        val line = Path()
        p.at(pts[0]).let { line.moveTo(it.x, it.y) }
        pts.drop(1).forEach { g -> p.at(g).let { line.lineTo(it.x, it.y) } }
        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(22f, 14f))
        drawPath(line, Color.White, style = Stroke(width = 10f, cap = StrokeCap.Round, pathEffect = dash))
        drawPath(line, Palette.Orange, style = Stroke(width = 6f, cap = StrokeCap.Round, pathEffect = dash))
    }
    val num = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 26f; color = android.graphics.Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val name = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.LEFT; textSize = 24f; color = Palette.Ink.toArgb(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    route.forEachIndexed { i, (label, g) ->
        val c = p.at(g)
        drawCircle(Color.White, radius = 22f, center = c)
        drawCircle(Palette.Orange, radius = 18f, center = c)
        drawContext.canvas.nativeCanvas.drawText("${i + 1}", c.x, c.y + 9f, num)
        val w = name.measureText(label) + 20f
        drawRoundRect(Color.White.copy(alpha = 0.92f), Offset(c.x + 26f, c.y - 18f), Size(w, 36f), CornerRadius(18f))
        drawContext.canvas.nativeCanvas.drawText(label, c.x + 36f, c.y + 8f, name)
    }
}

/** 현재 위치: 파란 점과 둘레. */
private fun DrawScope.drawHere(p: Projection, g: Geo) {
    val c = p.at(g)
    drawCircle(Palette.Primary.copy(alpha = 0.18f), radius = 46f, center = c)
    drawCircle(Color.White, radius = 18f, center = c)
    drawCircle(Palette.Primary, radius = 13f, center = c)
}

private const val HIT_PX = 26f

/** 지우개: 화면 [pos] 근처에 있는 꾸미기들. */
private fun hits(p: Projection, decos: List<Decoration>, pos: Offset): Set<Long> = decos.filter { d ->
    when (d) {
        is Decoration.Sticker -> (p.at(d.at) - pos).getDistance() <= 48f
        is Decoration.Stroke -> d.points.zipWithNext().any { (a, b) -> segDist(pos, p.at(a), p.at(b)) <= HIT_PX + d.width / 2 }
            || (d.points.size == 1 && (p.at(d.points[0]) - pos).getDistance() <= HIT_PX)
        is Decoration.Shape -> {
            val a = p.at(d.a)
            val b = p.at(d.b)
            when (d.kind) {
                ShapeKind.ARROW -> segDist(pos, a, b) <= HIT_PX
                else -> pos.x in min(a.x, b.x) - HIT_PX..max(a.x, b.x) + HIT_PX && pos.y in min(a.y, b.y) - HIT_PX..max(a.y, b.y) + HIT_PX
            }
        }
    }
}.map { it.id }.toSet()

private fun segDist(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len = ab.x * ab.x + ab.y * ab.y
    val t = if (len == 0f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

/** 낙서·도형·스티커. 굵기와 크기는 확대와 상관없이 화면 기준. */
private fun DrawScope.drawDecorations(p: Projection, decos: List<Decoration>) {
    val emojiPaint = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 40f }
    val labelPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 22f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    decos.forEach { d ->
        when (d) {
            is Decoration.Stroke -> {
                if (d.points.isEmpty()) return@forEach
                val path = Path()
                val first = p.at(d.points[0])
                path.moveTo(first.x, first.y)
                d.points.drop(1).forEach { g -> p.at(g).let { path.lineTo(it.x, it.y) } }
                drawPath(path, Color(d.color), style = Stroke(width = d.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            is Decoration.Shape -> {
                val a = p.at(d.a)
                val b = p.at(d.b)
                val tl = Offset(min(a.x, b.x), min(a.y, b.y))
                val sz = Size(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y))
                val stroke = Stroke(width = d.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                when (d.kind) {
                    ShapeKind.RECT -> drawRoundRect(Color(d.color), tl, sz, CornerRadius(8f), style = stroke)
                    ShapeKind.CIRCLE -> drawOval(Color(d.color), tl, sz, style = stroke)
                    ShapeKind.ARROW -> {
                        drawLine(Color(d.color), a, b, strokeWidth = d.width, cap = StrokeCap.Round)
                        val ang = kotlin.math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
                        val head = 18f + d.width * 2
                        listOf(ang + 2.6, ang - 2.6).forEach { t ->
                            drawLine(Color(d.color), b, Offset(b.x + (head * kotlin.math.cos(t)).toFloat(), b.y + (head * kotlin.math.sin(t)).toFloat()), strokeWidth = d.width, cap = StrokeCap.Round)
                        }
                    }
                }
            }
            is Decoration.Sticker -> {
                val c = p.at(d.at)
                drawCircle(Color.White, radius = 36f, center = c)
                drawCircle(Color(d.color), radius = 36f, center = c, style = Stroke(width = 6f))
                drawContext.canvas.nativeCanvas.drawText(d.emoji, c.x, c.y + 14f, emojiPaint)
                if (d.text.isNotBlank()) {
                    val w = labelPaint.measureText(d.text) + 26f
                    val top = c.y + 42f
                    drawRoundRect(Color(d.color), Offset(c.x - w / 2, top), Size(w, 34f), CornerRadius(17f))
                    drawContext.canvas.nativeCanvas.drawText(d.text, c.x, top + 24f, labelPaint)
                }
            }
        }
    }
}

private fun DrawScope.drawProvinceLabels(p: Projection, centers: Collection<Pair<Pair<Double, Double>, String>>, v: Viewport, zoom: Float) {
    // 전국 보기에서는 이름이 겹치므로 확대했을 때만, 도 단위 이름만 보인다.
    if ((v.maxLon - v.minLon) / zoom > 4.0) return
    val paint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = Palette.Muted.toArgb()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    centers.forEach { (center, name) ->
        if (!name.endsWith("도")) return@forEach
        val (lat, lon) = center
        val x = p.x(lon)
        val y = p.y(lat)
        if (x < 0 || x > size.width || y < 0 || y > size.height) return@forEach
        drawContext.canvas.nativeCanvas.drawText(name, x, y, paint)
    }
}

private fun DrawScope.drawTrack(p: Projection, track: List<TrackPoint>) {
    if (track.size < 2) return
    val line = Path()
    line.moveTo(p.x(track[0].lon), p.y(track[0].lat))
    track.drop(1).forEach { if (it.newSegment) line.moveTo(p.x(it.lon), p.y(it.lat)) else line.lineTo(p.x(it.lon), p.y(it.lat)) }
    drawPath(line, Color.White, style = Stroke(width = 11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(line, Palette.Primary, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 장소 스티커를 받은 명소에 별을 찍는다. */
private fun DrawScope.drawStickerStars(p: Projection, trip: TripState) {
    trip.stickers.filter { it.isPlace }.mapNotNull { Landmarks.byId(it.id.removePrefix("place:")) }.forEach { lm ->
        val c = Offset(p.x(lm.lon), p.y(lm.lat))
        val star = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 22f else 9f
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = c.x + (r * kotlin.math.cos(a)).toFloat()
            val y = c.y + (r * kotlin.math.sin(a)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        drawPath(star, Color.White, style = Stroke(width = 6f, join = StrokeJoin.Round))
        drawPath(star, Palette.Amber)
    }
}

/** 출발점, 머문 곳, 현재 위치에 이름표를 단다. */
private fun DrawScope.drawPins(p: Projection, trip: TripState, level: Level) {
    if (trip.track.isEmpty()) return
    val pins = mutableListOf<Pair<TrackPoint, String>>()
    val first = trip.track.first()
    trip.events.firstOrNull { it.kind == EventKind.START }?.let { pins += first to it.detail.substringBefore("에서 출발") }
    // 핀 이름은 읽기 쉬운 시·군·구 기준(읍·면·동 이름은 너무 잘다).
    trip.events.filter { it.kind == EventKind.STAY && it.level == Level.SGG }.forEach { e ->
        // 머문 이벤트 시각에 가장 가까운 점
        trip.track.minByOrNull { kotlin.math.abs(it.timeMs - e.timeMs) }?.let { pins += it to TripRecorder.shortName(e.title.substringBefore(" 머묾")) }
    }
    val here = if (level == Level.DONG) trip.current[Level.DONG]?.name else trip.current[Level.SGG]?.let(TripRecorder::placeName)
    here?.let { pins += trip.track.last() to it }

    val text = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    pins.distinctBy { it.second }.forEach { (pt, label) ->
        val c = Offset(p.x(pt.lon), p.y(pt.lat))
        drawCircle(Color.White, radius = 12f, center = c)
        drawCircle(Palette.Primary, radius = 12f, center = c, style = Stroke(width = 5f))
        val w = text.measureText(label) + 34f
        val top = c.y - 62f
        drawRoundRect(Palette.Primary, topLeft = Offset(c.x - w / 2, top), size = Size(w, 42f), cornerRadius = CornerRadius(21f))
        drawContext.canvas.nativeCanvas.drawText(label, c.x, top + 30f, text)
    }
}
