package com.carnect.roadcanvas.region

import org.json.JSONArray
import java.io.InputStream

/** 색칠 단위. 큰 것부터 시·도 → 시·군·구 → 읍·면·동. */
enum class Level(val label: String, val asset: String) {
    SIDO("시·도", "regions_sido.json"),
    SGG("시·군·구", "regions_sgg.json"),
    DONG("읍·면·동", "regions_dong.json"),
}

/**
 * 행정 구역 하나. [rings]는 바깥 경계들이며 각 배열은 lon, lat 순서로 번갈아 담긴다.
 * [parent]는 한 단계 위 구역 코드(시·도는 빈 문자열).
 */
class Region(
    val code: String,
    val name: String,
    val province: String,
    val parent: String,
    val rings: List<DoubleArray>,
) {
    val minLon: Double = rings.minOf { r -> (r.indices step 2).minOf { r[it] } }
    val maxLon: Double = rings.maxOf { r -> (r.indices step 2).maxOf { r[it] } }
    val minLat: Double = rings.minOf { r -> (1 until r.size step 2).minOf { r[it] } }
    val maxLat: Double = rings.maxOf { r -> (1 until r.size step 2).maxOf { r[it] } }

    val provinceCode: String get() = code.take(2)

    /** 길 안내 목적지·거리 계산에 쓰는 대표 지점(구역 안에 있는 점). (lat, lon) */
    val center: Pair<Double, Double> by lazy {
        val ring = rings.maxBy { it.size }
        val n = ring.size / 2
        val lat = (0 until n).sumOf { ring[it * 2 + 1] } / n
        val lon = (0 until n).sumOf { ring[it * 2] } / n
        when {
            contains(lat, lon) -> lat to lon
            contains((minLat + maxLat) / 2, (minLon + maxLon) / 2) -> (minLat + maxLat) / 2 to (minLon + maxLon) / 2
            // 모양이 휘어 평균점이 밖에 있으면 경계 위 점 중 평균점에 가장 가까운 점
            else -> (0 until n).minBy { (ring[it * 2 + 1] - lat).let { d -> d * d } + (ring[it * 2] - lon).let { d -> d * d } }
                .let { ring[it * 2 + 1] to ring[it * 2] }
        }
    }

    fun contains(lat: Double, lon: Double): Boolean {
        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
        return rings.any { ringContains(it, lat, lon) }
    }

    /** 경계선까지의 가장 짧은 거리(km, 좁은 범위라 평면 근사). */
    fun boundaryDistanceKm(lat: Double, lon: Double): Double {
        val kx = 111.32 * Math.cos(Math.toRadians(lat))
        val ky = 110.57
        var best = Double.MAX_VALUE
        rings.forEach { r ->
            val n = r.size / 2
            for (i in 0 until n) {
                val j = (i + 1) % n
                val ax = (r[i * 2] - lon) * kx
                val ay = (r[i * 2 + 1] - lat) * ky
                val bx = (r[j * 2] - lon) * kx
                val by = (r[j * 2 + 1] - lat) * ky
                val dx = bx - ax
                val dy = by - ay
                val len = dx * dx + dy * dy
                val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
                val px = ax + dx * t
                val py = ay + dy * t
                best = minOf(best, Math.sqrt(px * px + py * py))
            }
        }
        return best
    }

    override fun toString() = if (name == province) name else "$province $name($code)"

    private companion object {
        /** 짝수-홀수 규칙(ray casting). */
        fun ringContains(ring: DoubleArray, lat: Double, lon: Double): Boolean {
            var inside = false
            val n = ring.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = ring[i * 2]
                val yi = ring[i * 2 + 1]
                val xj = ring[j * 2]
                val yj = ring[j * 2 + 1]
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }
    }
}

/**
 * 좌표 → 행정 구역 판정. 경계 데이터는 `assets/regions_*.json`
 * (통계청 SGIS 행정동 경계를 가공한 vuski/admdongkor 데이터를 단계별로 합치고 단순화).
 */
class RegionIndex(val level: Level, val regions: List<Region>) {

    private val byCode = regions.associateBy { it.code }
    private val childCount = regions.groupingBy { it.parent }.eachCount()

    /**
     * 좌표가 든 구역. 경계를 단순화하면서 해안선·경계가 조금 안쪽으로 들어와 해변·항구 같은 점이
     * 어느 구역에도 안 들어갈 수 있으므로, 그럴 때는 [NEAR_KM] 안의 가장 가까운 구역으로 본다.
     */
    fun find(lat: Double, lon: Double): Region? =
        regions.firstOrNull { it.contains(lat, lon) } ?: nearest(lat, lon)

    private fun nearest(lat: Double, lon: Double): Region? {
        val m = NEAR_KM / 111.0 * 1.5
        return regions.asSequence()
            .filter { lat >= it.minLat - m && lat <= it.maxLat + m && lon >= it.minLon - m && lon <= it.maxLon + m }
            .map { it to it.boundaryDistanceKm(lat, lon) }
            .filter { it.second <= NEAR_KM }
            .minByOrNull { it.second }?.first
    }

    fun byCode(code: String): Region? = byCode[code]

    /** 시·도별 구역 수. 진행률(예: 강원 11/18) 계산에 쓴다. */
    fun countByProvince(): Map<String, Int> = regions.groupingBy { it.provinceCode }.eachCount()

    /** 한 단계 위 구역 안의 구역 수(예: 종로구 안의 읍·면·동 수). */
    fun countIn(parent: String): Int = childCount[parent] ?: 0

    companion object {
        /** 어느 구역에도 안 드는 점을 가장 가까운 구역으로 볼 최대 거리(km). */
        const val NEAR_KM = 1.5

        fun load(level: Level, input: InputStream): RegionIndex {
            val array = JSONArray(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val regions = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val ringsJson = o.getJSONArray("rings")
                val rings = (0 until ringsJson.length()).map { r ->
                    val coords = ringsJson.getJSONArray(r)
                    DoubleArray(coords.length()) { coords.getDouble(it) }
                }
                Region(o.getString("code"), o.getString("name"), o.getString("province"), o.optString("parent"), rings)
            }
            return RegionIndex(level, regions)
        }
    }
}
