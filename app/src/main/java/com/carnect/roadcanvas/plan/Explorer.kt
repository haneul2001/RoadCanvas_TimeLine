package com.carnect.roadcanvas.plan

import com.carnect.roadcanvas.data.Landmark
import com.carnect.roadcanvas.data.Landmarks
import com.carnect.roadcanvas.region.Region
import com.carnect.roadcanvas.region.RegionIndex
import com.carnect.roadcanvas.trip.TrackPoint
import com.carnect.roadcanvas.trip.TripTracker

/** 추천 하나. [extraKm]는 우회 추천에서 늘어나는 거리(직선 거리 기준 추정). */
data class Suggestion(
    val region: Region,
    val distanceKm: Double,
    val landmarks: List<Landmark>,
    val extraKm: Double? = null,
    /** 그 지역 추천 여행지(추천 코스 순서). */
    val spots: List<com.carnect.roadcanvas.data.Spot> = emptyList(),
) {
    val place: Place
        get() = spots.firstOrNull()?.let { Place(it.name, it.lat, it.lon, region.code) }
            ?: landmarks.firstOrNull()?.let { Place(it.name, it.lat, it.lon, region.code) }
            ?: Place(region.name, region.center.first, region.center.second, region.code)
}

/**
 * 아직 안 가 본 시·군·구를 추천한다(Canvas Quest, Smart Detour).
 * 관광지 정보는 앱에 넣어 둔 대표 명소만 쓴다(외부 관광 API 없이 동작).
 */
class Explorer(private val sgg: RegionIndex) {

    /** 시·군·구 추천 여행지(앱 시작 때 넣음). */
    @Volatile
    var spots: com.carnect.roadcanvas.data.Spots = com.carnect.roadcanvas.data.Spots.EMPTY

    /** 그 지역을 대표하는 지점: 추천 코스 첫 여행지 → 명소 → 구역 중심. */
    private fun anchor(r: Region): Pair<Double, Double> =
        spots.course(r.code).firstOrNull()?.let { it.lat to it.lon }
            ?: landmarksByRegion[r.code]?.firstOrNull()?.let { it.lat to it.lon }
            ?: r.center

    private val landmarksByRegion: Map<String, List<Landmark>> =
        Landmarks.all.mapNotNull { lm -> sgg.find(lm.lat, lm.lon)?.code?.let { it to lm } }.groupBy({ it.first }, { it.second })

    private fun km(aLat: Double, aLon: Double, bLat: Double, bLon: Double) =
        TripTracker.distanceM(TrackPoint(0, aLat, aLon), TrackPoint(0, bLat, bLon)) / 1000

    /**
     * Canvas Quest: 기준 위치에서 가까운 미방문 시·군·구. [province]가 있으면 그 시·도 이름(앞부분 일치) 안에서만.
     * 명소가 있는 곳을 조금 앞으로 당긴다(같은 거리면 볼거리가 있는 곳 먼저).
     */
    fun quest(visited: Set<String>, from: Pair<Double, Double>, province: String? = null, limit: Int = 6): List<Suggestion> =
        sgg.regions.asSequence()
            .filter { it.code !in visited }
            .filter { province == null || it.province.startsWith(province) }
            .map { r ->
                val (lat, lon) = anchor(r)
                Suggestion(r, km(from.first, from.second, lat, lon), landmarksByRegion[r.code].orEmpty(), spots = spots.course(r.code))
            }
            .sortedBy { it.distanceKm * if (it.landmarks.isNotEmpty()) 0.8 else 1.0 }
            .take(limit)
            .toList()

    /** 미방문 지역이 남은 시·도 목록(이름, 남은 수, 전체 수). */
    fun provincesLeft(visited: Set<String>): List<Triple<String, Int, Int>> =
        sgg.regions.groupBy { it.province }.map { (p, rs) -> Triple(p, rs.count { it.code !in visited }, rs.size) }
            .filter { it.second > 0 }.sortedBy { it.second.toFloat() / it.third }

    /**
     * Smart Detour: 지금 위치 → 목적지 사이에서 [maxExtraKm] 안쪽만 돌아가면 들를 수 있는 미방문 지역.
     * 실제 도로 거리·시간은 내비 경로를 받아야 알 수 있으므로 여기 값은 직선 거리 기준 추정이다.
     */
    fun detour(visited: Set<String>, from: Pair<Double, Double>, dest: Place, maxExtraKm: Double, limit: Int = 6): List<Suggestion> {
        val direct = km(from.first, from.second, dest.lat, dest.lon)
        return sgg.regions.asSequence()
            .filter { it.code !in visited && it.code != dest.regionCode }
            .map { r ->
                val p = anchor(r)
                val extra = km(from.first, from.second, p.first, p.second) + km(p.first, p.second, dest.lat, dest.lon) - direct
                Suggestion(r, km(from.first, from.second, p.first, p.second), landmarksByRegion[r.code].orEmpty(), extra, spots.course(r.code))
            }
            .filter { (it.extraKm ?: 0.0) <= maxExtraKm }
            .sortedBy { (it.extraKm ?: 0.0) * if (it.landmarks.isNotEmpty()) 0.7 else 1.0 }
            .take(limit)
            .toList()
    }
}
