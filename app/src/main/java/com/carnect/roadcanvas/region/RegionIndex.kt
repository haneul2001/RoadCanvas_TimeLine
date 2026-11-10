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

    fun contains(lat: Double, lon: Double): Boolean {
        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
        return rings.any { ringContains(it, lat, lon) }
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

    fun find(lat: Double, lon: Double): Region? = regions.firstOrNull { it.contains(lat, lon) }

    fun byCode(code: String): Region? = byCode[code]

    /** 시·도별 구역 수. 진행률(예: 강원 11/18) 계산에 쓴다. */
    fun countByProvince(): Map<String, Int> = regions.groupingBy { it.provinceCode }.eachCount()

    /** 한 단계 위 구역 안의 구역 수(예: 종로구 안의 읍·면·동 수). */
    fun countIn(parent: String): Int = childCount[parent] ?: 0

    companion object {
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
