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
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.graphics.asImageBitmap
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

/** 지도 밖 버튼으로 확대·축소·원래대로를 보내는 통로. 누를 때마다 [seq]가 늘어난다. */
class MapZoom {
    var seq by mutableStateOf(0)
        private set
    var factor = 1f
        private set

    fun zoomIn() { factor = 1.6f; seq++ }
    fun zoomOut() { factor = 1 / 1.6f; seq++ }
    fun reset() { factor = 0f; seq++ }
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

/**
 * 지도 색 테마. 단계 구분도(choropleth) 관례대로 한 가지 색이 방문 횟수만큼 진해진다
 * (1회 → 2회 → 3회 → 4회 → 5회 이상). 색표는 ColorBrewer 순차 색표(5단계, 가장 옅은 칸은 미방문 회색과 구분되게 뺌).
 */
enum class MapTheme(val label: String, val steps: List<Long>) {
    BLUE("파랑", listOf(0xFFC6DBEF, 0xFF9ECAE1, 0xFF6BAED6, 0xFF3182BD, 0xFF08519C)),
    GREEN("초록", listOf(0xFFC7E9C0, 0xFFA1D99B, 0xFF74C476, 0xFF31A354, 0xFF006D2C)),
    HEAT("주황·빨강", listOf(0xFFFED976, 0xFFFEB24C, 0xFFFD8D3C, 0xFFF03B20, 0xFFBD0026)),
    PURPLE("보라", listOf(0xFFDADAEB, 0xFFBCBDDC, 0xFF9E9AC8, 0xFF756BB1, 0xFF54278F)),
}

/** 지금 지도 색 테마. 바꾸면 지도와 범례가 바로 다시 그려진다. */
object MapColors {
    var theme by androidx.compose.runtime.mutableStateOf(MapTheme.BLUE)
}

/** 방문 횟수 [count]번에 칠할 색. [breaks]는 단계마다 시작 횟수(기본 1·2·3·4·5회). */
fun countColor(count: Int, t: MapTheme = MapColors.theme, breaks: List<Int> = DEFAULT_BREAKS): Color {
    val step = breaks.indexOfLast { count >= it }.coerceAtLeast(0)
    return Color(t.steps[step.coerceAtMost(t.steps.lastIndex)])
}

val DEFAULT_BREAKS = listOf(1, 2, 3, 4, 5)

/**
 * 단계 구분도의 분위수 구간: 실제 방문 횟수 분포를 다섯으로 나눠 단계마다 비슷한 수의 지역이 들어가게 한다.
 * 가장 많이 간 곳이 5회 이하면 1·2·3·4·5회 그대로.
 */
fun countBreaks(counts: Collection<Int>): List<Int> {
    if (counts.isEmpty() || counts.max() <= 5) return DEFAULT_BREAKS
    val sorted = counts.sorted()
    val out = mutableListOf(1)
    for (q in listOf(0.2, 0.4, 0.6, 0.8)) {
        val v = sorted[((sorted.size - 1) * q).toInt()]
        if (v > out.last()) out += v
    }
    // 같은 값이 많아 구간이 줄면 위쪽을 고르게 채운다
    while (out.size < 5) {
        val last = out.last()
        val next = last + ((sorted.last() - last) / (6 - out.size)).coerceAtLeast(1)
        if (next > sorted.last()) break
        out += next
    }
    return out
}

/** 범례 글자: 구간마다 "1", "2–8", "24+". */
fun breakLabels(breaks: List<Int>): List<String> = breaks.mapIndexed { i, b ->
    val next = breaks.getOrNull(i + 1)
    when {
        next == null -> "$b+"
        next - 1 == b -> "$b"
        else -> "$b–${next - 1}"
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
    /** 정차 중 지도를 한 번 누르면 그 자리의 좌표(구역 정보 보기). */
    onTapGeo: ((Geo) -> Unit)? = null,
    /** 추천 여행지. 지도를 충분히 확대하면 종류 스티커 그림으로 보여 준다. */
    spots: List<com.carnect.roadcanvas.data.Spot> = emptyList(),
    /** 확대·축소 버튼. */
    zoomControl: MapZoom? = null,
    /** 기간별 지도: 칠한 곳 전체와 경로가 다 보이게 범위를 맞춘다. */
    fitAll: Boolean = false,
    /** 칠한 곳마다 방문 횟수 숫자를 단다. */
    showCounts: Boolean = false,
    /** 색 단계 구간(없으면 지금 보이는 방문 횟수로 계산). */
    colorBreaks: List<Int>? = null,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bitmaps = remember { StickerBitmaps(ctx) }
    val tripRegions = trip.trip?.let { t -> trip.visitsAt(index.level).filterValues { it.tripId == t.id }.keys.mapNotNull(index::byCode) }.orEmpty()
    val shown = frame ?: trip
    val visits = shown.visitsAt(index.level)
    val viewport = if (focus != null) {
        // 위치가 조금씩 바뀔 때마다 화면이 흔들리지 않게 0.05° 단위로 묶어 다시 잡는다.
        val key = (focus.first * 20).toInt() to (focus.second * 20).toInt()
        remember(key, route) { Viewport.focus(focus, route.map { it.second }) }
    } else {
        val fitRegions = if (fitAll) trip.visitsAt(index.level).keys.mapNotNull(index::byCode) else tripRegions
        remember(fitRegions.size, trip.track.size / 20, fitAll) { Viewport.around(fitRegions, trip.track) }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var mapSize by remember { mutableStateOf(Size.Zero) }
    // 버튼으로 확대·축소: 지도 가운데를 중심으로
    LaunchedEffect(zoomControl?.seq) {
        val zc = zoomControl ?: return@LaunchedEffect
        if (zc.seq == 0) return@LaunchedEffect
        if (zc.factor == 0f) {
            zoom = 1f
            pan = Offset.Zero
            return@LaunchedEffect
        }
        val c = Offset(mapSize.width / 2, mapSize.height / 2)
        val newZoom = (zoom * zc.factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = newZoom / zoom
        pan = (pan - c) * applied + c
        zoom = newZoom
    }
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
            .pointerInput(onTapGeo != null) {
                detectTapGestures(
                    onDoubleTap = {
                        zoom = 1f
                        pan = Offset.Zero
                    },
                    onTap = { o ->
                        val tap = onTapGeo ?: return@detectTapGestures
                        tap(Projection(Size(size.width.toFloat(), size.height.toFloat()), currentViewport, zoom, pan).geo(o))
                    },
                )
            }
    } else {
        Modifier
    }
    val breaks = colorBreaks ?: remember(visits) { countBreaks(visits.values.map { it.count }) }
    // 읍·면·동은 구역이 촘촘해 경계선을 가늘게
    val borderWidth = if (index.level == Level.DONG) 0.5f else 1.2f
    val provinceCenters = remember(index) {
        index.regions.groupBy { it.provinceCode }.mapValues { (_, rs) ->
            Pair(rs.map { (it.minLat + it.maxLat) / 2 }.average(), rs.map { (it.minLon + it.maxLon) / 2 }.average()) to rs.first().province
        }
    }
    val pathCache = remember(index) { HashMap<Pair<Size, Viewport>, List<Pair<Region, Path>>>() }

    Canvas(modifier.clipToBounds().then(gestures).onSizeChanged { mapSize = Size(it.width.toFloat(), it.height.toFloat()) }) {
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
                drawPath(path, if (visit == null) Palette.Unvisited else countColor(visit.count, breaks = breaks))
                // 확대해도 경계선 두께는 그대로 보이게
                drawPath(path, Palette.MapBorder, style = Stroke(width = borderWidth / zoom))
            }
        }
        // 선·글자·핀은 확대와 상관없이 같은 크기로 그린다.
        val projection = Projection(size, viewport, zoom, pan)
        drawProvinceLabels(projection, provinceCenters.values, viewport, zoom)
        drawTrack(projection, shown.track)
        // 지도 위 스티커는 지도 꾸미기로 붙인 것만 그린다(받은 스티커·추천 여행지를 자동으로 찍지 않음)
        // [spots]를 넘긴 화면에서만, 확대했을 때 추천 여행지 그림(메인은 넘기지 않음)
        val spanDeg = (viewport.maxLon - viewport.minLon) / zoom
        if (spanDeg < 0.7) drawSpots(projection, spots, bitmaps, names = spanDeg < 0.4)
        if (showCounts) drawCounts(projection, index, visits)
        if (focus == null) drawPins(projection, shown, index.level)
        drawDecorations(projection, decorations + listOfNotNull(draft), bitmaps)
        if (route.isNotEmpty()) drawRoute(projection, focus, route)
        focus?.let { drawHere(projection, it) }
    }
}

/** 칠한 구역마다 방문 횟수(그 구역을 지난 여행 수). 화면에서 너무 작은 구역은 건너뛴다. */
private fun DrawScope.drawCounts(p: Projection, index: RegionIndex, visits: Map<String, com.carnect.roadcanvas.trip.VisitRecord>) {
    val text = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 22f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    visits.forEach { (code, v) ->
        val r = index.byCode(code) ?: return@forEach
        val w = kotlin.math.abs(p.x(r.maxLon) - p.x(r.minLon))
        val h = kotlin.math.abs(p.y(r.minLat) - p.y(r.maxLat))
        if (w < 34f || h < 34f) return@forEach
        val c = Offset(p.x(r.center.second), p.y(r.center.first))
        if (c.x < -20 || c.y < -20 || c.x > size.width + 20 || c.y > size.height + 20) return@forEach
        val label = "${v.count}"
        drawCircle(Color(0xCC16233A), radius = 17f, center = c)
        drawContext.canvas.nativeCanvas.drawText(label, c.x, c.y + 8f, text)
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
        drawRoundRect(Palette.Surface.copy(alpha = 0.92f), Offset(c.x + 26f, c.y - 18f), Size(w, 36f), CornerRadius(18f))
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

/** 스티커 그림(리소스) 캐시. 지도에 붙인 스티커·추천 여행지 표시에 쓴다. */
class StickerBitmaps(private val context: android.content.Context) {
    private val cache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

    fun get(key: String): androidx.compose.ui.graphics.ImageBitmap? = cache.getOrPut(key) {
        val def = com.carnect.roadcanvas.sticker.StickerCatalog.byKey(key) ?: return@getOrPut null
        android.graphics.BitmapFactory.decodeResource(context.resources, def.res)?.let {
            android.graphics.Bitmap.createScaledBitmap(it, 96, 96, true).asImageBitmap()
        }
    }
}

private fun DrawScope.drawSticker(img: androidx.compose.ui.graphics.ImageBitmap, c: Offset, size: Int) {
    drawImage(
        img,
        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
        srcSize = androidx.compose.ui.unit.IntSize(img.width, img.height),
        dstOffset = androidx.compose.ui.unit.IntOffset((c.x - size / 2).toInt(), (c.y - size / 2).toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(size, size),
    )
}

private fun DrawScope.drawSpots(p: Projection, spots: List<com.carnect.roadcanvas.data.Spot>, bitmaps: StickerBitmaps, names: Boolean) {
    val name = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 20f; color = Palette.Ink.toArgb(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    spots.forEach { sp ->
        val c = Offset(p.x(sp.lon), p.y(sp.lat))
        if (c.x < -40 || c.y < -40 || c.x > size.width + 40 || c.y > size.height + 40) return@forEach
        bitmaps.get(sp.kind)?.let { drawSticker(it, c, 52) } ?: drawCircle(Palette.Amber, 10f, c)
        if (!names) return@forEach
        val w = name.measureText(sp.name) + 14f
        drawRoundRect(Palette.Surface.copy(alpha = 0.85f), Offset(c.x - w / 2, c.y + 26f), Size(w, 26f), CornerRadius(13f))
        drawContext.canvas.nativeCanvas.drawText(sp.name, c.x, c.y + 45f, name)
    }
}

/** 낙서·도형·스티커. 굵기와 크기는 확대와 상관없이 화면 기준. */
private fun DrawScope.drawDecorations(p: Projection, decos: List<Decoration>, bitmaps: StickerBitmaps) {
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
                // 그림 스티커("img:<종류>")는 그림 그대로 붙인다
                if (d.emoji.startsWith("img:")) {
                    bitmaps.get(d.emoji.removePrefix("img:"))?.let { drawSticker(it, c, 84) }
                    if (d.text.isNotBlank()) {
                        val w = labelPaint.measureText(d.text) + 26f
                        drawRoundRect(Color(d.color), Offset(c.x - w / 2, c.y + 40f), Size(w, 34f), CornerRadius(17f))
                        drawContext.canvas.nativeCanvas.drawText(d.text, c.x, c.y + 64f, labelPaint)
                    }
                    return@forEach
                }
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
    // 여러 여행을 이어 볼 때는 이름표가 너무 많아지므로 지금 위치만
    if (trip.segments.size > 1) {
        val here = if (level == Level.DONG) trip.current[Level.DONG]?.name else trip.current[Level.SGG]?.let(TripRecorder::placeName)
        here?.let { drawPinList(p, listOf(trip.track.last() to it)) }
        return
    }
    trip.events.firstOrNull { it.kind == EventKind.START }?.let { pins += first to it.detail.substringBefore("에서 출발") }
    // 핀 이름은 읽기 쉬운 시·군·구 기준(읍·면·동 이름은 너무 잘다).
    trip.events.filter { it.kind == EventKind.STAY && it.level == Level.SGG }.forEach { e ->
        // 머문 이벤트 시각에 가장 가까운 점
        trip.track.minByOrNull { kotlin.math.abs(it.timeMs - e.timeMs) }?.let { pins += it to TripRecorder.shortName(e.title.substringBefore(" 머묾")) }
    }
    val here = if (level == Level.DONG) trip.current[Level.DONG]?.name else trip.current[Level.SGG]?.let(TripRecorder::placeName)
    here?.let { pins += trip.track.last() to it }

    drawPinList(p, pins.distinctBy { it.second })
}

private fun DrawScope.drawPinList(p: Projection, pins: List<Pair<TrackPoint, String>>) {
    val text = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    pins.forEach { (pt, label) ->
        val c = Offset(p.x(pt.lon), p.y(pt.lat))
        drawCircle(Color.White, radius = 12f, center = c)
        drawCircle(Palette.Primary, radius = 12f, center = c, style = Stroke(width = 5f))
        val w = text.measureText(label) + 34f
        val top = c.y - 62f
        drawRoundRect(Palette.Primary, topLeft = Offset(c.x - w / 2, top), size = Size(w, 42f), cornerRadius = CornerRadius(21f))
        drawContext.canvas.nativeCanvas.drawText(label, c.x, top + 30f, text)
    }
}
