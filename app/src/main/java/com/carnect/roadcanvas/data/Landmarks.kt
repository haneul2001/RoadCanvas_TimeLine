package com.carnect.roadcanvas.data

/**
 * 장소 스티커를 주는 대표 관광지. 좌표는 각 장소의 대표 지점(근사)이고,
 * 여행 경로가 [radiusM] 안을 지나간 지역의 방문을 확정하면 스티커를 준다.
 * [group]은 미션(예: 동해안 여행지 탐험) 묶음.
 */
data class Landmark(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val group: String,
    val radiusM: Double = 1_000.0,
    /** 음성 브리핑용 한 줄 소개. */
    val note: String = "",
)

object Landmarks {
    const val EAST_COAST = "동해안"

    val all = listOf(
        Landmark("gyeongbokgung", "경복궁", 37.5796, 126.9770, "서울", note = "조선 시대의 으뜸 궁궐"),
        Landmark("namsan_tower", "남산서울타워", 37.5512, 126.9882, "서울", note = "서울 시내를 내려다보는 전망대"),
        Landmark("nami_island", "남이섬", 37.7905, 127.5257, "강원 내륙", note = "북한강에 떠 있는 섬 관광지"),
        Landmark("seorak", "설악산 소공원", 38.1739, 128.4887, EAST_COAST, note = "설악산 국립공원 입구, 케이블카와 신흥사"),
        Landmark("sokcho_beach", "속초 해변", 38.1906, 128.6025, EAST_COAST, note = "속초 시내 가까운 해수욕장"),
        Landmark("naksansa", "낙산사", 38.1243, 128.6278, EAST_COAST, note = "바다를 내려다보는 절"),
        Landmark("gyeongpo", "경포해변", 37.8055, 128.9087, EAST_COAST, note = "강릉의 대표 해수욕장과 경포호"),
        Landmark("jeongdongjin", "정동진", 37.6912, 129.0346, EAST_COAST, note = "해돋이 명소이자 바닷가 기차역"),
        Landmark("danyang", "단양 도담삼봉", 36.9990, 128.3440, "충청", note = "남한강 위에 솟은 세 봉우리"),
        Landmark("hahoe", "안동 하회마을", 36.5390, 128.5180, "경상", note = "유네스코 세계유산인 전통 마을"),
        Landmark("bulguksa", "경주 불국사", 35.7900, 129.3320, "경상", note = "유네스코 세계유산인 신라 절"),
        Landmark("haeundae", "해운대", 35.1587, 129.1604, "부산", note = "부산의 대표 해수욕장"),
        Landmark("gamcheon", "감천문화마을", 35.0975, 129.0106, "부산", note = "산비탈에 알록달록한 집이 모인 마을"),
        Landmark("jeonju_hanok", "전주 한옥마을", 35.8150, 127.1530, "전라", note = "한옥이 모여 있는 골목 마을"),
        Landmark("suncheonman", "순천만습지", 34.8870, 127.5090, "전라", note = "갈대밭이 펼쳐진 바닷가 습지"),
        Landmark("odongdo", "여수 오동도", 34.7450, 127.7650, "전라", note = "동백꽃으로 이름난 섬"),
        Landmark("seongsan", "성산일출봉", 33.4590, 126.9425, "제주", note = "유네스코 세계자연유산, 해돋이 명소"),
        Landmark("hallasan", "한라산 성판악", 33.3850, 126.6200, "제주", note = "한라산 정상으로 가는 등산로 입구"),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
