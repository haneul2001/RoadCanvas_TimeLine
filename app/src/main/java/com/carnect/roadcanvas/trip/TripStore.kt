package com.carnect.roadcanvas.trip

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 방문 상태와 주행 좌표를 기기 안에만 저장한다(외부 전송 없음).
 * - 방문 상태: files/visits.json  {"코드": "PASSED", ...}
 * - 좌표: files/track.csv  시각(ms),위도,경도
 */
class TripStore(context: Context) {

    private val visitsFile = File(context.filesDir, "visits.json")
    private val trackFile = File(context.filesDir, "track.csv")

    fun loadVisits(): Map<String, VisitState> {
        if (!visitsFile.exists()) return emptyMap()
        val json = JSONObject(visitsFile.readText())
        return json.keys().asSequence().associateWith { VisitState.valueOf(json.getString(it)) }
    }

    fun saveVisits(states: Map<String, VisitState>) {
        val json = JSONObject()
        states.forEach { (code, state) -> json.put(code, state.name) }
        visitsFile.writeText(json.toString())
    }

    fun appendPoint(p: TrackPoint) {
        trackFile.appendText("${p.timeMs},${p.lat},${p.lon}\n")
    }

    fun loadTrack(): List<TrackPoint> {
        if (!trackFile.exists()) return emptyList()
        return trackFile.readLines().mapNotNull { line ->
            val parts = line.split(',')
            if (parts.size != 3) return@mapNotNull null
            TrackPoint(parts[0].toLong(), parts[1].toDouble(), parts[2].toDouble())
        }
    }

    /** 사용자가 기록을 지울 때. */
    fun clear() {
        visitsFile.delete()
        trackFile.delete()
    }
}
