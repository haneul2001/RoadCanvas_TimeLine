package com.carnect.roadcanvas.plan

import com.carnect.roadcanvas.data.Landmarks
import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.TrackPoint
import com.carnect.roadcanvas.trip.TripTracker

/** 길 안내에 쓸 수 있는 장소. [regionCode]는 들어 있는 시·군·구 코드. */
data class Place(val name: String, val lat: Double, val lon: Double, val regionCode: String?) {
    fun distanceKmTo(lat: Double, lon: Double) = TripTracker.distanceM(TrackPoint(0, this.lat, this.lon), TrackPoint(0, lat, lon)) / 1000
    fun distanceKmTo(other: Place) = distanceKmTo(other.lat, other.lon)
}

/**
 * 말이나 글에 나온 지명을 장소로 바꾸는 사전. LLM 없이 규칙으로 처리한다.
 * - 특별·광역시 이름(서울, 부산…)은 시청 부근
 * - 시·군·구는 "속초", "속초시"처럼 접미사를 빼도 찾는다
 * - 읍·면은 "주문진", "주문진읍"
 * - 대표 명소(속초 해변, 낙산사…)
 * 같은 이름이 여러 곳이면 정확한 이름·광역시·명소 > 시·군·구 줄임말 > 읍·면 줄임말 순으로 고르고
 * ("부산"은 부산광역시, 장흥 "부산면"이 아님), 같은 순위끼리는(고성군 등) 기준 위치에서 가까운 곳을 고른다.
 */
class Places(indexes: Map<Level, RegionIndex>) {

    private val sgg = indexes.getValue(Level.SGG)
    /** 이름 → (순위, 장소). 순위가 낮을수록 먼저. */
    private val entries: Map<String, List<Pair<Int, Place>>>

    init {
        val map = HashMap<String, MutableList<Pair<Int, Place>>>()
        fun add(key: String, place: Place, rank: Int = EXACT) {
            if (key.length >= 2) map.getOrPut(key) { mutableListOf() } += rank to place
        }
        METRO.forEach { (name, lat, lon) -> add(name, Place(name, lat, lon, sgg.find(lat, lon)?.code)) }
        sgg.regions.forEach { r ->
            val (lat, lon) = r.center
            val place = Place(r.name, lat, lon, r.code)
            add(r.name, place)
            shortName(r.name)?.let { add(it, place, SGG_SHORT) }
        }
        indexes.getValue(Level.DONG).regions.filter { it.name.endsWith("읍") || it.name.endsWith("면") }.forEach { r ->
            val (lat, lon) = r.center
            val place = Place(r.name, lat, lon, r.parent)
            add(r.name, place)
            add(r.name.dropLast(1), place, DONG_SHORT)
        }
        Landmarks.all.forEach { lm ->
            val place = Place(lm.name, lm.lat, lm.lon, sgg.find(lm.lat, lm.lon)?.code)
            add(lm.name, place)
            add(lm.name.replace(" ", ""), place)
        }
        entries = map
    }

    /** 문장 속 지명을 나온 순서대로 찾는다. 긴 이름을 먼저 맞춰 "속초 해변"이 "속초"로 잘리지 않게 한다. */
    fun findAll(text: String, near: Pair<Double, Double>?): List<Pair<Int, Place>> {
        val compact = text.replace(" ", "")
        val found = mutableListOf<Pair<Int, Place>>()
        val taken = BooleanArray(compact.length)
        entries.keys.sortedByDescending { it.length }.forEach { key ->
            var from = 0
            while (true) {
                val i = compact.indexOf(key, from)
                if (i < 0) break
                from = i + 1
                if ((i until i + key.length).any { taken[it] }) continue
                (i until i + key.length).forEach { taken[it] = true }
                found += i to pick(entries.getValue(key), near)
            }
        }
        return found.sortedBy { it.first }
    }

    fun find(name: String, near: Pair<Double, Double>?): Place? = findAll(name, near).firstOrNull()?.second

    private fun pick(candidates: List<Pair<Int, Place>>, near: Pair<Double, Double>?): Place {
        val best = candidates.minOf { it.first }
        val top = candidates.filter { it.first == best }.map { it.second }
        return if (near == null || top.size == 1) top.first() else top.minBy { it.distanceKmTo(near.first, near.second) }
    }

    companion object {
        private const val EXACT = 0
        private const val SGG_SHORT = 1
        private const val DONG_SHORT = 2

        private val METRO = listOf(
            Triple("서울", 37.5663, 126.9779), Triple("부산", 35.1798, 129.0750), Triple("대구", 35.8714, 128.6014),
            Triple("인천", 37.4563, 126.7052), Triple("광주", 35.1601, 126.8514), Triple("대전", 36.3504, 127.3845),
            Triple("울산", 35.5384, 129.3114), Triple("세종", 36.4800, 127.2890),
        )

        /** "속초시" → "속초". 한 글자만 남거나("중구"→"중") 시·군·구가 아니면 null. */
        fun shortName(name: String): String? =
            if (name.length >= 3 && (name.endsWith("시") || name.endsWith("군") || name.endsWith("구"))) name.dropLast(1) else null
    }
}
