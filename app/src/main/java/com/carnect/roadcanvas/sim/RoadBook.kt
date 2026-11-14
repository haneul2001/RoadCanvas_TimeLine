package com.carnect.roadcanvas.sim

import com.carnect.roadcanvas.trip.TrackPoint
import com.carnect.roadcanvas.trip.TripTracker
import org.json.JSONObject
import java.io.InputStream

typealias LatLon = Pair<Double, Double>

/**
 * 테스트 주행용 도로 모양 모음. 주요 도시 사이의 실제 도로 좌표(OpenStreetMap, OSRM으로 미리 받음,
 * scripts/fetch_city_roads.py)를 앱에 넣어 두고, 출발지·목적지가 각각 어떤 도시 근처면 그 도로를 따라간다.
 * 실행 중에는 외부 서버를 부르지 않는다(실제 차 위치가 밖으로 나가지 않게).
 */
class RoadBook(
    private val cities: List<Pair<String, LatLon>>,
    /** (출발 도시 번호, 도착 도시 번호) → a에서 b 방향 좌표. */
    private val routes: Map<Pair<Int, Int>, List<LatLon>>,
) {
    val cityNames: List<String> get() = cities.map { it.first }

    /**
     * [from] → [to] 경로 좌표(마지막 점은 [to]). 두 곳 모두 도시 [SNAP_KM] 안이면 도로를 따르고,
     * 아니면 [STEP_KM] 간격 직선. 두 번째 값은 도로를 따랐는지.
     */
    fun route(from: LatLon, to: LatLon): Pair<List<LatLon>, Boolean> {
        val a = nearestCity(from)
        val b = nearestCity(to)
        if (a != null && b != null && a != b) {
            val road = routes[a to b] ?: routes[b to a]?.reversed()
            if (road != null) return (road + to) to true
        }
        return straight(from, to) to false
    }

    /** 도시 이름으로 도로 좌표(a → b). 없으면 null. */
    fun cityRoute(a: String, b: String): List<LatLon>? {
        val i = cities.indexOfFirst { it.first == a }
        val j = cities.indexOfFirst { it.first == b }
        if (i < 0 || j < 0 || i == j) return null
        return routes[i to j] ?: routes[j to i]?.reversed()
    }

    private fun nearestCity(p: LatLon): Int? =
        cities.indices.minByOrNull { km(p, cities[it].second) }?.takeIf { km(p, cities[it].second) <= SNAP_KM }

    companion object {
        const val SNAP_KM = 20.0
        private const val STEP_KM = 0.3

        fun km(a: LatLon, b: LatLon) =
            TripTracker.distanceM(TrackPoint(0, a.first, a.second), TrackPoint(0, b.first, b.second)) / 1000

        fun straight(from: LatLon, to: LatLon): List<LatLon> {
            val n = (km(from, to) / STEP_KM).toInt().coerceAtLeast(1)
            return (1..n).map { i ->
                val t = i.toDouble() / n
                from.first + (to.first - from.first) * t to from.second + (to.second - from.second) * t
            }
        }

        fun load(input: InputStream): RoadBook {
            val json = JSONObject(input.bufferedReader().readText())
            val cs = json.getJSONArray("cities")
            val cities = (0 until cs.length()).map { i -> cs.getJSONArray(i).let { it.getString(0) to (it.getDouble(1) to it.getDouble(2)) } }
            val rs = json.getJSONArray("routes")
            val routes = HashMap<Pair<Int, Int>, List<LatLon>>()
            for (i in 0 until rs.length()) {
                val r = rs.getJSONObject(i)
                val pts = r.getJSONArray("points")
                routes[r.getInt("a") to r.getInt("b")] = (0 until pts.length()).map { k -> pts.getJSONArray(k).let { it.getDouble(0) to it.getDouble(1) } }
            }
            return RoadBook(cities, routes)
        }
    }
}
