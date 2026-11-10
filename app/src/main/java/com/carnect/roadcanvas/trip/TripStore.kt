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

    private fun visitsFile(level: Level) = File(dir, "visits_${level.name.lowercase()}.json")

    fun loadVisits(level: Level): Map<String, VisitRecord> = runCatching {
        val file = visitsFile(level)
        if (!file.exists()) return emptyMap()
        val json = JSONObject(file.readText())
        json.keys().asSequence().associateWith {
            val o = json.getJSONObject(it)
            VisitRecord(VisitState.valueOf(o.getString("s")), o.getLong("t"), o.getLong("trip"))
        }
    }.getOrDefault(emptyMap())

    fun saveVisits(level: Level, visits: Map<String, VisitRecord>) {
        val json = JSONObject()
        visits.forEach { (code, v) ->
            json.put(code, JSONObject().put("s", v.state.name).put("t", v.firstMs).put("trip", v.tripId))
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

    /** 사용자가 기록을 지울 때. */
    fun clear() {
        (Level.entries.map(::visitsFile) + listOf(tripsFile, eventsFile, trackFile, File(dir, "visits.json"))).forEach { it.delete() }
    }
}
