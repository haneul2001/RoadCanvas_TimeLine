package com.carnect.roadcanvas.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 화면 모드(설정). SYSTEM은 차량(시스템) 설정을 따른다. */
enum class ThemeMode(val label: String) { LIGHT("밝게"), DARK("어둡게"), SYSTEM("시스템 따라") }

/**
 * 앱 색. 밝은/어두운 화면에 따라 값이 바뀐다([dark]는 Compose 상태라 바꾸면 화면이 바로 다시 그려진다).
 * 강조색(Primary·Teal·Amber·Green·Orange)은 두 모드에서 같고, 바탕·글자·선만 바뀐다.
 */
object Palette {
    var dark by mutableStateOf(false)

    private fun pick(light: Long, darkValue: Long) = Color(if (dark) darkValue else light)

    val Background get() = pick(0xFFF4F7FB, 0xFF0F1622)
    val Card get() = pick(0xFFFFFFFF, 0xFF18212F)
    /** 지도 위에 떠 있는 작은 카드·칩 바탕. */
    val Surface get() = pick(0xFFFFFFFF, 0xFF1E2938)
    val Primary = Color(0xFF1F6FEB)
    val PrimarySoft get() = pick(0xFFE6F0FF, 0xFF1A2D4D)
    val Teal = Color(0xFF2BB5A4)
    val TealSoft get() = pick(0xFFE5F7F4, 0xFF143430)
    val Amber = Color(0xFFF5A524)
    val AmberSoft get() = pick(0xFFFFF4E0, 0xFF3A2E14)
    val Ink get() = pick(0xFF16233A, 0xFFE8EEF7)
    val Muted get() = pick(0xFF6B7A90, 0xFF95A3B8)
    val Line get() = pick(0xFFE3E9F2, 0xFF2C384A)
    val Sea get() = pick(0xFFEFF4FA, 0xFF0B1220)
    val Unvisited get() = pick(0xFFE6E9EE, 0xFF2A3445)
    /** 지도 구역 경계선. */
    val MapBorder get() = pick(0xFFFFFFFF, 0xFF0F1622)
    val Green = Color(0xFF2E9E6B)
    val GreenSoft get() = pick(0xFFE8F6EE, 0xFF13321F)
    val OrangeSoft get() = pick(0xFFFFF1E6, 0xFF3A2414)
    val Orange = Color(0xFFE07A2E)
}
