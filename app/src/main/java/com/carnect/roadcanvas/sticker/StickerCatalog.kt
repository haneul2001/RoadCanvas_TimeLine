package com.carnect.roadcanvas.sticker

import androidx.annotation.DrawableRes
import com.carnect.roadcanvas.R

/** 스티커 분류. [collectible]이 아니면 모으는 대상이 아니라 꾸미기에서 늘 쓸 수 있는 스티커. */
enum class StickerCategory(val label: String, val collectible: Boolean) {
    NATURE("자연", true),
    TOUR("관광지", true),
    FOOD("음식", true),
    ACTIVITY("액티비티", true),
    SEASON("계절", true),
    REGION("지역", true),
    ACHIEVE("업적", true),
    WEATHER("날씨", false),
    EMOTION("감정", false),
}

/** 스티커 한 종류. [how]는 얻는 방법(도감에 표시). */
data class StickerDef(val key: String, val label: String, val category: StickerCategory, @DrawableRes val res: Int, val how: String)

private fun S(key: String, label: String, category: StickerCategory, @DrawableRes res: Int, how: String) = StickerDef(key, label, category, res, how)

/**
 * 스티커 도감. 그림은 팀이 만든 스티커 시트에서 잘라 넣었다.
 * 경북·경남 지역 스티커는 시트에 그림이 없어 유적지·다리 그림을 함께 쓴다.
 */
object StickerCatalog {
    val all: List<StickerDef> = listOf(
        S("mountain", "산", StickerCategory.NATURE, R.drawable.stk_mountain, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("sea", "바다", StickerCategory.NATURE, R.drawable.stk_sea, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("lake", "호수", StickerCategory.NATURE, R.drawable.stk_lake, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("river", "강", StickerCategory.NATURE, R.drawable.stk_river, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("forest", "숲", StickerCategory.NATURE, R.drawable.stk_forest, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("waterfall", "폭포", StickerCategory.NATURE, R.drawable.stk_waterfall, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("valley", "계곡", StickerCategory.NATURE, R.drawable.stk_valley, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("field", "들판", StickerCategory.NATURE, R.drawable.stk_field, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("beach", "해변", StickerCategory.NATURE, R.drawable.stk_beach, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("sunset", "일몰", StickerCategory.NATURE, R.drawable.stk_sunset, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("coffee", "커피", StickerCategory.FOOD, R.drawable.stk_coffee, "이 종류의 추천 여행지(시장·먹거리) 근처에서 방문이 확정되면"),
        S("food", "음식", StickerCategory.FOOD, R.drawable.stk_food, "이 종류의 추천 여행지(시장·먹거리) 근처에서 방문이 확정되면"),
        S("restaurant", "맛집", StickerCategory.FOOD, R.drawable.stk_restaurant, "이 종류의 추천 여행지(시장·먹거리) 근처에서 방문이 확정되면"),
        S("dessert", "디저트", StickerCategory.FOOD, R.drawable.stk_dessert, "이 종류의 추천 여행지(시장·먹거리) 근처에서 방문이 확정되면"),
        S("drink", "음료", StickerCategory.FOOD, R.drawable.stk_drink, "이 종류의 추천 여행지(시장·먹거리) 근처에서 방문이 확정되면"),
        S("snack", "간식", StickerCategory.FOOD, R.drawable.stk_snack, "이 종류의 추천 여행지(시장·먹거리) 근처에서 방문이 확정되면"),
        S("bread", "빵", StickerCategory.FOOD, R.drawable.stk_bread, "이 종류의 추천 여행지(시장·먹거리) 근처에서 방문이 확정되면"),
        S("fruit", "과일", StickerCategory.FOOD, R.drawable.stk_fruit, "이 종류의 추천 여행지(시장·먹거리) 근처에서 방문이 확정되면"),
        S("nightfood", "야식", StickerCategory.FOOD, R.drawable.stk_nightfood, "이 종류의 추천 여행지(시장·먹거리) 근처에서 방문이 확정되면"),
        S("bbq", "바비큐", StickerCategory.FOOD, R.drawable.stk_bbq, "이 종류의 추천 여행지(시장·먹거리) 근처에서 방문이 확정되면"),
        S("camping", "캠핑", StickerCategory.ACTIVITY, R.drawable.stk_camping, "이 종류의 추천 여행지 근처에서 방문이 확정되면"),
        S("hiking", "등산", StickerCategory.ACTIVITY, R.drawable.stk_hiking, "이 종류의 추천 여행지 근처에서 방문이 확정되면"),
        S("fishing", "낚시", StickerCategory.ACTIVITY, R.drawable.stk_fishing, "이 종류의 추천 여행지 근처에서 방문이 확정되면"),
        S("swimming", "수영", StickerCategory.ACTIVITY, R.drawable.stk_swimming, "이 종류의 추천 여행지 근처에서 방문이 확정되면"),
        S("surfing", "서핑", StickerCategory.ACTIVITY, R.drawable.stk_surfing, "이 종류의 추천 여행지 근처에서 방문이 확정되면"),
        S("ski", "스키", StickerCategory.ACTIVITY, R.drawable.stk_ski, "이 종류의 추천 여행지 근처에서 방문이 확정되면"),
        S("snowboard", "스노우보드", StickerCategory.ACTIVITY, R.drawable.stk_snowboard, "이 종류의 추천 여행지 근처에서 방문이 확정되면"),
        S("bicycle", "자전거", StickerCategory.ACTIVITY, R.drawable.stk_bicycle, "이 종류의 추천 여행지 근처에서 방문이 확정되면"),
        S("activity", "액티비티", StickerCategory.ACTIVITY, R.drawable.stk_activity, "이 종류의 추천 여행지 근처에서 방문이 확정되면"),
        S("exhibition", "전시회", StickerCategory.ACTIVITY, R.drawable.stk_exhibition, "이 종류의 추천 여행지 근처에서 방문이 확정되면"),
        S("observatory", "전망대", StickerCategory.TOUR, R.drawable.stk_observatory, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("palace", "궁궐", StickerCategory.TOUR, R.drawable.stk_palace, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("temple", "사찰", StickerCategory.TOUR, R.drawable.stk_temple, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("museum", "박물관", StickerCategory.TOUR, R.drawable.stk_museum, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("ruins", "유적지", StickerCategory.TOUR, R.drawable.stk_ruins, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("bridge", "다리", StickerCategory.TOUR, R.drawable.stk_bridge, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("lighthouse", "등대", StickerCategory.TOUR, R.drawable.stk_lighthouse, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("fountain", "분수", StickerCategory.TOUR, R.drawable.stk_fountain, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("park", "공원", StickerCategory.TOUR, R.drawable.stk_park, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("sunrise", "해돋이 명소", StickerCategory.TOUR, R.drawable.stk_sunrise, "이 종류의 추천 여행지 근처를 지나며 그 지역 방문이 확정되면"),
        S("sunny", "맑음", StickerCategory.WEATHER, R.drawable.stk_sunny, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("cloudy", "흐림", StickerCategory.WEATHER, R.drawable.stk_cloudy, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("rain", "비", StickerCategory.WEATHER, R.drawable.stk_rain, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("snow", "눈", StickerCategory.WEATHER, R.drawable.stk_snow, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("wind", "바람", StickerCategory.WEATHER, R.drawable.stk_wind, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("fog", "안개", StickerCategory.WEATHER, R.drawable.stk_fog, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("rainbow", "무지개", StickerCategory.WEATHER, R.drawable.stk_rainbow, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("moon", "달", StickerCategory.WEATHER, R.drawable.stk_moon, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("star", "별", StickerCategory.WEATHER, R.drawable.stk_star, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("cloud", "구름", StickerCategory.WEATHER, R.drawable.stk_cloud, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("happy", "행복", StickerCategory.EMOTION, R.drawable.stk_happy, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("love", "설렘", StickerCategory.EMOTION, R.drawable.stk_love, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("moved", "감동", StickerCategory.EMOTION, R.drawable.stk_moved, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("rest", "휴식", StickerCategory.EMOTION, R.drawable.stk_rest, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("healing", "힐링", StickerCategory.EMOTION, R.drawable.stk_healing, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("photo", "인생샷", StickerCategory.EMOTION, R.drawable.stk_photo, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("anniversary", "기념일", StickerCategory.EMOTION, R.drawable.stk_anniversary, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("luck", "행운", StickerCategory.EMOTION, R.drawable.stk_luck, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("challenge", "도전", StickerCategory.EMOTION, R.drawable.stk_challenge, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("special", "특별함", StickerCategory.EMOTION, R.drawable.stk_special, "지도 꾸미기에서 언제든 쓸 수 있어요"),
        S("first_visit", "첫 방문", StickerCategory.ACHIEVE, R.drawable.stk_first_visit, "처음으로 지역 방문이 확정되면"),
        S("revisit", "재방문", StickerCategory.ACHIEVE, R.drawable.stk_revisit, "같은 시·군·구를 다른 여행에서 다시 방문하면"),
        S("complete", "완주", StickerCategory.ACHIEVE, R.drawable.stk_complete, "계획한 목적지를 모두 들르면"),
        S("explorer", "탐험가", StickerCategory.ACHIEVE, R.drawable.stk_explorer, "시·군·구 10곳 방문을 확정하면"),
        S("collector", "컬렉터", StickerCategory.ACHIEVE, R.drawable.stk_collector, "스티커 10개를 모으면"),
        S("streak", "연속 방문", StickerCategory.ACHIEVE, R.drawable.stk_streak, "사흘 연속으로 여행하면"),
        S("local_explorer", "지방 탐험", StickerCategory.ACHIEVE, R.drawable.stk_local_explorer, "시·도 5곳을 방문하면"),
        S("nation_complete", "전국 완주", StickerCategory.ACHIEVE, R.drawable.stk_nation_complete, "전국 모든 시·도를 방문하면"),
        S("memory", "추억 저장", StickerCategory.ACHIEVE, R.drawable.stk_memory, "지도 꾸미기에 처음 무언가 붙이면"),
        S("level_up", "레벨 업", StickerCategory.ACHIEVE, R.drawable.stk_level_up, "여행 레벨 3이 되면"),
        S("spring", "봄(벚꽃)", StickerCategory.SEASON, R.drawable.stk_spring, "3~5월에 방문을 확정하면"),
        S("summer", "여름(해바라기)", StickerCategory.SEASON, R.drawable.stk_summer, "6~8월에 방문을 확정하면"),
        S("autumn", "가을(단풍)", StickerCategory.SEASON, R.drawable.stk_autumn, "9~11월에 방문을 확정하면"),
        S("winter", "겨울(눈꽃)", StickerCategory.SEASON, R.drawable.stk_winter, "12~2월에 방문을 확정하면"),
        S("christmas", "크리스마스", StickerCategory.SEASON, R.drawable.stk_christmas, "12월 24~25일에 여행하면"),
        S("halloween", "할로윈", StickerCategory.SEASON, R.drawable.stk_halloween, "10월 31일에 여행하면"),
        S("newyear", "새해", StickerCategory.SEASON, R.drawable.stk_newyear, "1월 1일에 여행하면"),
        S("space", "우주", StickerCategory.SEASON, R.drawable.stk_space, "누적 1,000km를 달리면"),
        S("silvergrass", "가을(억새)", StickerCategory.SEASON, R.drawable.stk_silvergrass, "10~11월에 방문을 확정하면"),
        S("santa", "크리스마스(산타모자)", StickerCategory.SEASON, R.drawable.stk_santa, "12월에 방문을 확정하면"),
        S("region_seoul", "서울", StickerCategory.REGION, R.drawable.stk_region_seoul, "서울에서 방문이 확정되면"),
        S("region_busan", "부산", StickerCategory.REGION, R.drawable.stk_region_busan, "부산에서 방문이 확정되면"),
        S("region_daegu", "대구", StickerCategory.REGION, R.drawable.stk_region_daegu, "대구에서 방문이 확정되면"),
        S("region_incheon", "인천", StickerCategory.REGION, R.drawable.stk_region_incheon, "인천에서 방문이 확정되면"),
        S("region_gwangju", "광주", StickerCategory.REGION, R.drawable.stk_region_gwangju, "광주에서 방문이 확정되면"),
        S("region_daejeon", "대전", StickerCategory.REGION, R.drawable.stk_region_daejeon, "대전에서 방문이 확정되면"),
        S("region_ulsan", "울산", StickerCategory.REGION, R.drawable.stk_region_ulsan, "울산에서 방문이 확정되면"),
        S("region_sejong", "세종", StickerCategory.REGION, R.drawable.stk_region_sejong, "세종에서 방문이 확정되면"),
        S("region_gyeonggi", "경기", StickerCategory.REGION, R.drawable.stk_region_gyeonggi, "경기에서 방문이 확정되면"),
        S("region_gangwon", "강원", StickerCategory.REGION, R.drawable.stk_region_gangwon, "강원에서 방문이 확정되면"),
        S("region_chungbuk", "충북", StickerCategory.REGION, R.drawable.stk_region_chungbuk, "충북에서 방문이 확정되면"),
        S("region_chungnam", "충남", StickerCategory.REGION, R.drawable.stk_region_chungnam, "충남에서 방문이 확정되면"),
        S("region_jeonbuk", "전북", StickerCategory.REGION, R.drawable.stk_region_jeonbuk, "전북에서 방문이 확정되면"),
        S("region_jeonnam", "전남", StickerCategory.REGION, R.drawable.stk_region_jeonnam, "전남에서 방문이 확정되면"),
        S("region_jeju", "제주", StickerCategory.REGION, R.drawable.stk_region_jeju, "제주에서 방문이 확정되면"),
        S("region_gyeongbuk", "경북", StickerCategory.REGION, R.drawable.stk_ruins, "경북에서 방문이 확정되면"),
        S("region_gyeongnam", "경남", StickerCategory.REGION, R.drawable.stk_bridge, "경남에서 방문이 확정되면"),
    )

    private val byKey = all.associateBy { it.key }

    fun byKey(key: String): StickerDef? = byKey[key]

    val collectible: List<StickerDef> get() = all.filter { it.category.collectible }

    /** 저장된 스티커 id "kind:<key>"에서 종류를 꺼낸다. */
    fun ofStickerId(id: String): StickerDef? = if (id.startsWith(PREFIX)) byKey[id.removePrefix(PREFIX)] else null

    const val PREFIX = "kind:"
}
