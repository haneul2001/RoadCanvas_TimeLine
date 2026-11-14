package com.carnect.roadcanvas.data

/**
 * 시연용 '집'. 테스트 주행·샘플 여행이 여기서 출발한다(서울 광진구 자양로 117, 광진구청 부근).
 * 실제 사용자 집이 아니라 시연을 위한 고정 위치다.
 */
object Home {
    const val NAME = "집 (서울 광진구)"
    const val LAT = 37.5385
    const val LON = 127.0823
    val geo: Pair<Double, Double> get() = LAT to LON
}
