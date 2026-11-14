package com.carnect.roadcanvas.data

import com.carnect.roadcanvas.trip.TrackPoint
import com.carnect.roadcanvas.trip.TripTracker
import org.json.JSONArray
import java.io.InputStream

/** 시·군·구 추천 여행지 하나. [kind]는 스티커 종류(StickerCatalog 키). */
data class Spot(
    val id: String,
    val sggCode: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val kind: String,
    val note: String,
) {
    fun distanceKmTo(lat: Double, lon: Double) = TripTracker.distanceM(TrackPoint(0, this.lat, this.lon), TrackPoint(0, lat, lon)) / 1000
}

/**
 * 전국 시·군·구별 추천 여행지와 추천 코스(들르는 순서). `assets/spots.json`
 * (공개된 관광 정보를 바탕으로 정리한 대표 명소, 좌표는 근사).
 */
class Spots(private val bySgg: Map<String, List<Spot>>, private val courses: Map<String, List<String>>) {

    val all: List<Spot> = bySgg.values.flatten()

    fun inRegion(sggCode: String): List<Spot> = bySgg[sggCode].orEmpty()

    /** 추천 코스: 그 지역 여행지를 들르는 순서. */
    fun course(sggCode: String): List<Spot> {
        val spots = inRegion(sggCode)
        val order = courses[sggCode].orEmpty()
        val ordered = order.mapNotNull { n -> spots.firstOrNull { it.name == n } }
        return ordered + spots.filterNot { it in ordered }
    }

    /** 좌표 근처(반경 km)의 추천 여행지, 가까운 순. */
    fun near(lat: Double, lon: Double, km: Double): List<Spot> =
        all.asSequence().map { it to it.distanceKmTo(lat, lon) }.filter { it.second <= km }.sortedBy { it.second }.map { it.first }.toList()

    fun byId(id: String): Spot? = all.firstOrNull { it.id == id }

    companion object {
        /** 경로가 이 거리 안을 지나면 그 여행지에 들른 것으로 본다(m). */
        const val VISIT_RADIUS_M = 1_000.0

        val EMPTY = Spots(emptyMap(), emptyMap())

        fun load(input: InputStream): Spots {
            val arr = JSONArray(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val bySgg = HashMap<String, List<Spot>>()
            val courses = HashMap<String, List<String>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val code = o.getString("code")
                val sp = o.getJSONArray("spots")
                bySgg[code] = (0 until sp.length()).map { j ->
                    val s = sp.getJSONObject(j)
                    Spot("$code-$j", code, s.getString("name"), s.getDouble("lat"), s.getDouble("lon"), s.getString("kind"), s.optString("note"))
                }
                o.optJSONArray("course")?.let { c -> courses[code] = (0 until c.length()).map(c::getString) }
            }
            return Spots(bySgg, courses)
        }
    }
}
