package com.carnect.roadcanvas.ui.home

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
}

private const val MIN_ZOOM = 0.6f
private const val MAX_ZOOM = 8f

/** 방문 순서대로 빨강 → 보라를 돌아가며 칠한다. 머문 곳·확정은 같은 색을 진하게. */
fun rainbow(order: Int, state: VisitState): Color {
    val hue = (order * 26f) % 300f
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
) {
    val tripRegions = trip.trip?.let { t -> trip.visitsAt(index.level).filterValues { it.tripId == t.id }.keys.mapNotNull(index::byCode) }.orEmpty()
    val shown = frame ?: trip
    val visits = shown.visitsAt(index.level)
    val viewport = remember(tripRegions.size, trip.track.size / 20) { Viewport.around(tripRegions, trip.track) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // 주행을 시작하면 사용자가 옮겨 둔 화면을 이번 여행 범위로 되돌린다.
    LaunchedEffect(interactive) {
        if (!interactive) {
            zoom = 1f
            pan = Offset.Zero
        }
    }
    val gestures = if (interactive) {
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
        drawPins(projection, shown, index.level)
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
    track.drop(1).forEach { line.lineTo(p.x(it.lon), p.y(it.lat)) }
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
