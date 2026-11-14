package com.carnect.roadcanvas.deco

import org.junit.Assert.assertEquals
import org.junit.Test

class DecorationJsonTest {

    @Test
    fun `낙서·도형·스티커가 저장 형식을 오가도 그대로다`() {
        val list = listOf(
            Decoration.Stroke(1, listOf(37.5 to 127.0, 37.6 to 127.1), 0xFFE53935.toInt(), 8f),
            Decoration.Shape(2, ShapeKind.CIRCLE, 38.1 to 128.5, 38.2 to 128.6, 0xFF1E88E5.toInt(), 6f),
            Decoration.Sticker(3, 37.8 to 128.9, "🏖️", "경포 바다", 0xFFFFB300.toInt()),
        )
        val back = list.map { DecorStore.fromJson(DecorStore.toJson(it)) }
        assertEquals(list, back)
    }
}
