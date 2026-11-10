package com.carnect.roadcanvas.ui.home

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

private class Projection(size: Size, v: Viewport) {
    private val lonScale = cos(Math.toRadians((v.minLat + v.maxLat) / 2))
    private val w = (v.maxLon - v.minLon) * lonScale
    private val h = v.maxLat - v.minLat
    // 전국 보기는 전체가 들어오게, 여행 보기는 화면을 꽉 채운다(남는 쪽은 잘림).
    private val scale = if (v == Viewport.KOREA) min(size.width / w, size.height / h) else max(size.width / w, size.height / h)
    private val offsetX = (size.width - w * scale) / 2
    private val offsetY = (size.height - h * scale) / 2
    private val minLon = v.minLon
    private val maxLat = v.maxLat
    private val ls = lonScale

    fun x(lon: Double) = (offsetX + (lon - minLon) * ls * scale).toFloat()
    fun y(lat: Double) = (offsetY + (maxLat - lat) * scale).toFloat()
}

/** 방문 순서대로 빨강 → 보라를 돌아가며 칠한다. 머문 곳·확정은 같은 색을 진하게. */
fun rainbow(order: Int, state: VisitState): Color {
    val hue = (order * 26f) % 300f
    return when (state) {
        VisitState.PASSED -> Color.hsv(hue, 0.38f, 0.98f)
        VisitState.STAY_CANDIDATE -> Color.hsv(hue, 0.62f, 0.93f)
        VisitState.CONFIRMED -> Color.hsv(hue, 0.80f, 0.85f)
    }
}

@Composable
fun TripMap(index: RegionIndex, trip: TripState, modifier: Modifier = Modifier) {
    val tripRegions = trip.trip?.let { t -> trip.visits.filterValues { it.tripId == t.id }.keys.mapNotNull(index::byCode) }.orEmpty()
    val viewport = remember(tripRegions.size, trip.track.size / 20) { Viewport.around(tripRegions, trip.track) }
    val order = remember(trip.visits) {
        trip.visits.entries.sortedBy { it.value.firstMs }.mapIndexed { i, e -> e.key to i }.toMap()
    }
    val provinceCenters = remember(index) {
        index.regions.groupBy { it.provinceCode }.mapValues { (_, rs) ->
            Pair(rs.map { (it.minLat + it.maxLat) / 2 }.average(), rs.map { (it.minLon + it.maxLon) / 2 }.average()) to rs.first().province
        }
    }
    val pathCache = remember(index) { HashMap<Pair<Size, Viewport>, List<Pair<Region, Path>>>() }

    Canvas(modifier) {
        val projection = Projection(size, viewport)
        val paths = pathCache.getOrPut(size to viewport) {
            if (pathCache.size > 4) pathCache.clear()
            index.regions.filter(viewport::intersects).map { region ->
                val path = Path()
                region.rings.forEach { ring ->
                    path.moveTo(projection.x(ring[0]), projection.y(ring[1]))
                    for (i in 2 until ring.size step 2) path.lineTo(projection.x(ring[i]), projection.y(ring[i + 1]))
                    path.close()
                }
                region to path
            }
        }
        drawRect(Palette.Sea)
        paths.forEach { (region, path) ->
            val visit = trip.visits[region.code]
            drawPath(path, if (visit == null) Palette.Unvisited else rainbow(order[region.code] ?: 0, visit.state))
            drawPath(path, Color.White, style = Stroke(width = 1.2f))
        }
        drawProvinceLabels(projection, provinceCenters.values, viewport)
        drawTrack(projection, trip.track)
        drawPins(projection, trip)
    }
}

private fun DrawScope.drawProvinceLabels(p: Projection, centers: Collection<Pair<Pair<Double, Double>, String>>, v: Viewport) {
    // 전국 보기에서는 이름이 겹치므로 확대했을 때만, 도 단위 이름만 보인다.
    if (v.maxLon - v.minLon > 4.0) return
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
        if (lon < v.minLon || lon > v.maxLon || lat < v.minLat || lat > v.maxLat) return@forEach
        drawContext.canvas.nativeCanvas.drawText(name, p.x(lon), p.y(lat), paint)
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

/** 출발점, 머문 곳, 현재 위치에 이름표를 단다. */
private fun DrawScope.drawPins(p: Projection, trip: TripState) {
    if (trip.track.isEmpty()) return
    val pins = mutableListOf<Pair<TrackPoint, String>>()
    val first = trip.track.first()
    trip.events.firstOrNull { it.kind == EventKind.START }?.let { pins += first to it.detail.substringBefore("에서 출발") }
    trip.events.filter { it.kind == EventKind.STAY }.forEach { e ->
        // 머문 이벤트 시각에 가장 가까운 점
        trip.track.minByOrNull { kotlin.math.abs(it.timeMs - e.timeMs) }?.let { pins += it to e.title.substringBefore(" 머묾").let(::shortName) }
    }
    trip.currentRegion?.let { pins += trip.track.last() to TripRecorder.placeName(it) }

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

private fun shortName(n: String) = if (n.length > 2 && (n.endsWith("시") || n.endsWith("군") || n.endsWith("구"))) n.dropLast(1) else n
