package com.carnect.roadcanvas.trip

import android.content.Context
import com.carnect.roadcanvas.region.Level
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 여행 기록을 기기 안에만 저장한다(외부 전송 없음).
 * - visits_<단계>.json  {"코드": {"s": 상태, "t": 첫 방문 시각, "trip": 여행 id}}
 * - trips.json          [{"id", "end", "dist"}]
 * - events.jsonl        타임라인 한 줄에 하나
 * - track.csv           시각,위도,경도,여행 id
 */
class TripStore(context: Context) {

    private val dir = context.filesDir
    private val tripsFile = File(dir, "trips.json")
    private val eventsFile = File(dir, "events.jsonl")
    private val trackFile = File(dir, "track.csv")
    private val stickersFile = File(dir, "stickers.json")
    private val zonesFile = File(dir, "zones.json")

    private fun visitsFile(level: Level) = File(dir, "visits_${level.name.lowercase()}.json")

    fun loadStickers(): List<Sticker> = runCatching {
        if (!stickersFile.exists()) return emptyList()
        val array = JSONArray(stickersFile.readText())
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            Sticker(o.getString("id"), o.getString("title"), o.getLong("t"), o.getLong("trip"))
        }
    }.getOrDefault(emptyList())

    fun saveStickers(stickers: List<Sticker>) {
        val array = JSONArray()
        stickers.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("t", it.timeMs).put("trip", it.tripId)) }
        stickersFile.writeText(array.toString())
    }

    fun loadVisits(level: Level): Map<String, VisitRecord> = runCatching {
        val file = visitsFile(level)
        if (!file.exists()) return emptyMap()
        val json = JSONObject(file.readText())
        json.keys().asSequence().associateWith {
            val o = json.getJSONObject(it)
            val trip = o.getLong("trip")
            val ids = o.optJSONArray("trips")?.let { a -> (0 until a.length()).map(a::getLong) } ?: listOf(trip)
            VisitRecord(VisitState.valueOf(o.getString("s")), o.getLong("t"), trip, ids)
        }
    }.getOrDefault(emptyMap())

    fun saveVisits(level: Level, visits: Map<String, VisitRecord>) {
        val json = JSONObject()
        visits.forEach { (code, v) ->
            json.put(code, JSONObject().put("s", v.state.name).put("t", v.firstMs).put("trip", v.tripId).put("trips", JSONArray(v.tripIds)))
        }
        visitsFile(level).writeText(json.toString())
    }

    fun loadTrips(): List<Trip> = runCatching {
        if (!tripsFile.exists()) return emptyList()
        val array = JSONArray(tripsFile.readText())
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            Trip(o.getLong("id"), if (o.has("end")) o.getLong("end") else null, o.optDouble("dist", 0.0))
        }
    }.getOrDefault(emptyList())

    fun saveTrips(trips: List<Trip>) {
        val array = JSONArray()
        trips.forEach { t ->
            val o = JSONObject().put("id", t.id).put("dist", t.distanceM)
            t.endMs?.let { o.put("end", it) }
            array.put(o)
        }
        tripsFile.writeText(array.toString())
    }

    fun appendEvent(e: TimelineEvent) {
        val o = JSONObject().put("t", e.timeMs).put("trip", e.tripId).put("kind", e.kind.name)
            .put("title", e.title).put("detail", e.detail)
        e.level?.let { o.put("level", it.name) }
        eventsFile.appendText(o.toString() + "\n")
    }

    fun loadEvents(tripId: Long): List<TimelineEvent> = runCatching {
        if (!eventsFile.exists()) return emptyList()
        eventsFile.readLines().filter { it.isNotBlank() }.map(::JSONObject)
            .filter { it.getLong("trip") == tripId }
            .map {
                TimelineEvent(
                    it.getLong("t"), tripId, EventKind.valueOf(it.getString("kind")), it.getString("title"), it.getString("detail"),
                    it.optString("level").takeIf(String::isNotEmpty)?.let(Level::valueOf),
                )
            }
    }.getOrDefault(emptyList())

    fun appendPoint(p: TrackPoint, tripId: Long) {
        trackFile.appendText("${p.timeMs},${p.lat},${p.lon},$tripId\n")
    }

    fun loadTrack(tripId: Long): List<TrackPoint> = runCatching {
        if (!trackFile.exists()) return emptyList()
        trackFile.readLines().mapNotNull { line ->
            val parts = line.split(',')
            if (parts.size != 4 || parts[3].toLong() != tripId) return@mapNotNull null
            TrackPoint(parts[0].toLong(), parts[1].toDouble(), parts[2].toDouble())
        }
    }.getOrDefault(emptyList())

    /** 여행 하나를 지운다(경로·타임라인·그 여행에서 처음 칠한 방문·스티커). */
    fun deleteTrip(tripId: Long) {
        if (trackFile.exists()) {
            trackFile.writeText(trackFile.readLines().filter { it.isNotBlank() && !it.endsWith(",$tripId") }.joinToString("") { it + "\n" })
        }
        if (eventsFile.exists()) {
            eventsFile.writeText(
                eventsFile.readLines().filter { it.isNotBlank() && runCatching { JSONObject(it).getLong("trip") != tripId }.getOrDefault(false) }
                    .joinToString("") { it + "\n" }
            )
        }
    }

    /** 기록 제외 구역(집·회사 등). 이 원 안의 위치는 저장하지 않는다. */
    fun loadZones(): List<ExcludedZone> = runCatching {
        if (!zonesFile.exists()) return emptyList()
        val a = JSONArray(zonesFile.readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map { ExcludedZone(it.getString("name"), it.getDouble("lat"), it.getDouble("lon"), it.getDouble("r")) }
    }.getOrDefault(emptyList())

    fun saveZones(zones: List<ExcludedZone>) {
        val a = JSONArray()
        zones.forEach { a.put(JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon).put("r", it.radiusM)) }
        zonesFile.writeText(a.toString())
    }

    /** 사용자가 기록을 지울 때. */
    fun clear() {
        (Level.entries.map(::visitsFile) + listOf(tripsFile, eventsFile, trackFile, stickersFile, File(dir, "visits.json"))).forEach { it.delete() }
    }
}
