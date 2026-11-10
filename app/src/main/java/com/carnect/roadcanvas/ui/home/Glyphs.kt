package com.carnect.roadcanvas.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/** 앱 아이콘: 둥근 사각형 안의 산과 길. */
@Composable
fun AppIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF2BB5A4), Color(0xFF1F6FEB)), Offset.Zero, Offset(w, h)),
            cornerRadius = CornerRadius(w * 0.24f),
        )
        val mountain = Path().apply {
            moveTo(w * 0.14f, h * 0.74f)
            lineTo(w * 0.42f, h * 0.30f)
            lineTo(w * 0.56f, h * 0.50f)
            lineTo(w * 0.66f, h * 0.38f)
            lineTo(w * 0.88f, h * 0.74f)
            close()
        }
        drawPath(mountain, Color.White)
        val road = Path().apply {
            moveTo(w * 0.42f, h * 0.90f)
            quadraticTo(w * 0.62f, h * 0.78f, w * 0.48f, h * 0.68f)
        }
        drawPath(road, Color(0xFF1F6FEB), style = Stroke(width = w * 0.07f, cap = StrokeCap.Round))
    }
}

/** 마이크 모양(기본 아이콘 세트에 없어 직접 그린다). */
@Composable
fun MicGlyph(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, Offset(w * 0.34f, h * 0.06f), Size(w * 0.32f, h * 0.56f), CornerRadius(w * 0.16f))
        drawArc(color, 0f, 180f, false, Offset(w * 0.20f, h * 0.22f), Size(w * 0.60f, h * 0.56f), style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.5f, h * 0.78f), Offset(w * 0.5f, h * 0.94f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
    }
}
