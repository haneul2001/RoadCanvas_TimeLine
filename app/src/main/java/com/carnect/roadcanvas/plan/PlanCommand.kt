package com.carnect.roadcanvas.plan

/** 말 한마디를 해석한 결과. */
sealed interface PlanCommand {
    /** "속초, 양양, 강릉 들렀다가 서울로 갈게" → 들를 곳들 + 마지막 목적지(있으면 순서를 바꾸지 않는다). */
    data class NewPlan(val stops: List<Place>, val finalStop: Place?) : PlanCommand
    /** "양양은 빼 줘" */
    data class Remove(val place: Place) : PlanCommand
    /** "주문진 먼저 갈래" / "양양 말고 주문진 먼저" (앞의 것은 빼고 뒤의 것을 다음 목적지로) */
    data class Prioritize(val place: Place, val instead: Place?) : PlanCommand
    data object Depart : PlanCommand
    data object Unknown : PlanCommand
}

/** 규칙 기반 해석기. 경유 순서 계산은 [RouteOrder]가 맡는다. */
object PlanParser {

    fun parse(text: String, places: Places, near: Pair<Double, Double>?): PlanCommand {
        val compact = text.replace(" ", "")
        val found = places.findAll(text, near)
        val names = found.map { it.second }

        if (names.isEmpty()) {
            return if ("출발" in compact || "가자" in compact || "다시가" in compact) PlanCommand.Depart else PlanCommand.Unknown
        }

        val notIdx = listOf("말고", "대신").map { compact.indexOf(it) }.filter { it >= 0 }.minOrNull()
        if (notIdx != null) {
            val before = found.filter { it.first < notIdx }.map { it.second }
            val after = found.filter { it.first > notIdx }.map { it.second }
            if (after.isNotEmpty()) return PlanCommand.Prioritize(after.first(), before.lastOrNull())
            if (before.isNotEmpty()) return PlanCommand.Remove(before.last())
        }
        if ("빼" in compact || "취소" in compact || "안갈" in compact) return PlanCommand.Remove(names.last())
        if ("먼저" in compact && names.size == 1) return PlanCommand.Prioritize(names.first(), null)

        // 마지막 지명 바로 뒤가 "로/으로"면 그곳이 마지막 목적지("서울로 갈게").
        val (lastIdx, last) = found.last()
        val lastKeyLen = compact.drop(lastIdx).let { rest -> rest.indexOfFirst { it == '로' }.takeIf { it in 1..6 } }
        val isFinal = lastKeyLen != null && compact.getOrNull(lastIdx + lastKeyLen) == '로' && names.size >= 2
        return if (isFinal) PlanCommand.NewPlan(names.dropLast(1).distinct(), last) else PlanCommand.NewPlan(names.distinct(), null)
    }
}

/** 출발점에서 모든 곳을 한 번씩 들르는 가장 짧은 순서(직선 거리 기준). */
object RouteOrder {

    fun order(start: Pair<Double, Double>, stops: List<Place>, finalStop: Place?): List<Place> {
        if (stops.size <= 1) return stops + listOfNotNull(finalStop)
        val best = if (stops.size <= 7) permutations(stops).minBy { cost(start, it, finalStop) } else nearestNeighbor(start, stops)
        return best + listOfNotNull(finalStop)
    }

    fun cost(start: Pair<Double, Double>, route: List<Place>, finalStop: Place?): Double {
        var total = 0.0
        var lat = start.first
        var lon = start.second
        (route + listOfNotNull(finalStop)).forEach { p ->
            total += p.distanceKmTo(lat, lon)
            lat = p.lat
            lon = p.lon
        }
        return total
    }

    private fun nearestNeighbor(start: Pair<Double, Double>, stops: List<Place>): List<Place> {
        val left = stops.toMutableList()
        val out = mutableListOf<Place>()
        var lat = start.first
        var lon = start.second
        while (left.isNotEmpty()) {
            val next = left.minBy { it.distanceKmTo(lat, lon) }
            out += next
            left -= next
            lat = next.lat
            lon = next.lon
        }
        return out
    }

    private fun permutations(items: List<Place>): Sequence<List<Place>> = sequence {
        if (items.size <= 1) {
            yield(items)
            return@sequence
        }
        for (i in items.indices) {
            val rest = items.toMutableList().also { it.removeAt(i) }
            for (p in permutations(rest)) yield(listOf(items[i]) + p)
        }
    }
}
