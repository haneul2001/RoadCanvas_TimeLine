package com.carnect.roadcanvas.plan

import com.carnect.roadcanvas.region.Level
import com.carnect.roadcanvas.region.RegionIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class PlanParserTest {

    companion object {
        lateinit var places: Places
        private val SEOUL = 37.5759 to 126.9768

        @BeforeClass
        @JvmStatic
        fun load() {
            val indexes = Level.entries.associateWith { l -> File("src/main/assets/${l.asset}").inputStream().use { RegionIndex.load(l, it) } }
            places = Places(indexes)
        }
    }

    private fun parse(text: String) = PlanParser.parse(text, places, SEOUL)

    @Test
    fun `여러 목적지와 마지막 목적지를 나눈다`() {
        val cmd = parse("속초, 양양, 강릉 들렀다가 서울로 갈게") as PlanCommand.NewPlan
        assertEquals(listOf("속초시", "양양군", "강릉시"), cmd.stops.map { it.name })
        assertEquals("서울", cmd.finalStop?.name)
    }

    @Test
    fun `서울에서 출발하면 속초 양양 강릉 순서가 가장 짧다`() {
        val cmd = parse("강릉이랑 속초, 양양 들러서 서울로 돌아올게") as PlanCommand.NewPlan
        val route = RouteOrder.order(SEOUL, cmd.stops, cmd.finalStop)
        assertEquals(listOf("속초시", "양양군", "강릉시", "서울"), route.map { it.name })
    }

    @Test
    fun `빼 달라는 말과 먼저 가자는 말을 구분한다`() {
        assertEquals("양양군", (parse("양양은 빼줘") as PlanCommand.Remove).place.name)
        val p = parse("양양 말고 주문진 먼저 갈래") as PlanCommand.Prioritize
        assertEquals("주문진읍", p.place.name)
        assertEquals("양양군", p.instead?.name)
    }

    @Test
    fun `출발 명령과 명소 이름을 알아듣는다`() {
        assertTrue(parse("출발") is PlanCommand.Depart)
        val cmd = parse("속초 해변 갔다가 낙산사") as PlanCommand.NewPlan
        assertEquals(listOf("속초 해변", "낙산사"), cmd.stops.map { it.name })
    }

    @Test
    fun `부산은 부산광역시로 찾는다(장흥 부산면이 아님)`() {
        val p = places.find("부산으로", 37.5663 to 126.9779)!!
        org.junit.Assert.assertEquals("부산", p.name)
    }

    @Test
    fun `뭐가 유명해는 명소 질문으로 해석한다`() {
        val cmd = PlanParser.parse("속초 뭐가 유명해?", places, 37.5663 to 126.9779)
        org.junit.Assert.assertTrue(cmd is PlanCommand.Ask)
        org.junit.Assert.assertEquals("속초시", (cmd as PlanCommand.Ask).place.name)
    }
}
