package com.carnect.roadcanvas.region

import org.json.JSONArray
import java.io.InputStream

/** 시·군·구 하나. [rings]는 바깥 경계들이며 각 배열은 lon, lat 순서로 번갈아 담긴다. */
class Region(
    val code: String,
    val name: String,
    val province: String,
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

    override fun toString() = "$province $name($code)"

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

/** 좌표 → 시·군·구 판정. 경계 데이터는 `assets/regions.json`(통계청 2018 시·군·구 경계를 단순화). */
class RegionIndex(val regions: List<Region>) {

    private val byCode = regions.associateBy { it.code }

    fun find(lat: Double, lon: Double): Region? = regions.firstOrNull { it.contains(lat, lon) }

    fun byCode(code: String): Region? = byCode[code]

    /** 시·도별 시·군·구 수. 진행률(예: 강원 11/18) 계산에 쓴다. */
    fun countByProvince(): Map<String, Int> = regions.groupingBy { it.provinceCode }.eachCount()

    companion object {
        fun load(input: InputStream): RegionIndex {
            val array = JSONArray(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val regions = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val ringsJson = o.getJSONArray("rings")
                val rings = (0 until ringsJson.length()).map { r ->
                    val coords = ringsJson.getJSONArray(r)
                    DoubleArray(coords.length()) { coords.getDouble(it) }
                }
                Region(o.getString("code"), o.getString("name"), o.getString("province"), rings)
            }
            return RegionIndex(regions)
        }
    }
}
