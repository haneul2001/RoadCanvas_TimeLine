package com.carnect.roadcanvas.deco

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 위도, 경도. 꾸미기는 지도 좌표에 붙여 두어 지도를 옮기거나 확대해도 같은 자리에 있다. */
typealias Geo = Pair<Double, Double>

enum class ShapeKind(val label: String) { RECT("네모"), CIRCLE("동그라미"), ARROW("화살표") }

/** 지도 꾸미기 하나. 선 굵기·스티커 크기는 화면 기준(확대해도 같은 크기). */
sealed interface Decoration {
    val id: Long

    data class Stroke(override val id: Long, val points: List<Geo>, val color: Int, val width: Float) : Decoration
    data class Shape(override val id: Long, val kind: ShapeKind, val a: Geo, val b: Geo, val color: Int, val width: Float) : Decoration
    data class Sticker(override val id: Long, val at: Geo, val emoji: String, val text: String, val color: Int) : Decoration
}

/** 직접 만든 스티커(이모지 + 짧은 글 + 색). */
data class CustomSticker(val id: Long, val emoji: String, val text: String, val color: Int)

/** 꾸미기와 커스텀 스티커를 기기 안 파일에 저장한다(decorations.json, custom_stickers.json). */
class DecorStore(context: Context) {
    private val decoFile = File(context.filesDir, "decorations.json")
    private val customFile = File(context.filesDir, "custom_stickers.json")

    fun load(): List<Decoration> = runCatching {
        if (!decoFile.exists()) return emptyList()
        val a = JSONArray(decoFile.readText())
        (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun save(list: List<Decoration>) {
        val a = JSONArray()
        list.forEach { a.put(toJson(it)) }
        decoFile.writeText(a.toString())
    }

    fun loadCustom(): List<CustomSticker> = runCatching {
        if (!customFile.exists()) return emptyList()
        val a = JSONArray(customFile.readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map { CustomSticker(it.getLong("id"), it.getString("emoji"), it.getString("text"), it.getInt("color")) }
    }.getOrDefault(emptyList())

    fun saveCustom(list: List<CustomSticker>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("emoji", it.emoji).put("text", it.text).put("color", it.color)) }
        customFile.writeText(a.toString())
    }

    fun clear() {
        decoFile.delete()
        customFile.delete()
    }

    companion object {
        private fun geo(g: Geo) = JSONArray().put(g.first).put(g.second)
        private fun geo(a: JSONArray) = a.getDouble(0) to a.getDouble(1)

        fun toJson(d: Decoration): JSONObject = when (d) {
            is Decoration.Stroke -> JSONObject().put("t", "stroke").put("id", d.id).put("c", d.color).put("w", d.width.toDouble())
                .put("p", JSONArray().also { arr -> d.points.forEach { arr.put(geo(it)) } })
            is Decoration.Shape -> JSONObject().put("t", "shape").put("id", d.id).put("k", d.kind.name).put("c", d.color).put("w", d.width.toDouble())
                .put("a", geo(d.a)).put("b", geo(d.b))
            is Decoration.Sticker -> JSONObject().put("t", "sticker").put("id", d.id).put("at", geo(d.at)).put("e", d.emoji).put("x", d.text).put("c", d.color)
        }

        fun fromJson(o: JSONObject): Decoration? = when (o.optString("t")) {
            "stroke" -> o.getJSONArray("p").let { p ->
                Decoration.Stroke(o.getLong("id"), (0 until p.length()).map { geo(p.getJSONArray(it)) }, o.getInt("c"), o.getDouble("w").toFloat())
            }
            "shape" -> Decoration.Shape(o.getLong("id"), ShapeKind.valueOf(o.getString("k")), geo(o.getJSONArray("a")), geo(o.getJSONArray("b")), o.getInt("c"), o.getDouble("w").toFloat())
            "sticker" -> Decoration.Sticker(o.getLong("id"), geo(o.getJSONArray("at")), o.getString("e"), o.getString("x"), o.getInt("c"))
            else -> null
        }
    }
}
