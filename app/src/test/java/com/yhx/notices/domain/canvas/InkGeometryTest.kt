package com.yhx.notices.domain.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkGeometryTest {

    @Test
    fun `单点输出圆形轮廓`() {
        val outline = InkGeometry.strokeOutline(listOf(10f, 10f), listOf(4f))
        assertEquals(32, outline.size) // 16 边形
        // 所有点到圆心距离 ≈ 半径
        var i = 0
        while (i + 1 < outline.size) {
            val d = Math.hypot((outline[i] - 10f).toDouble(), (outline[i + 1] - 10f).toDouble())
            assertEquals(4.0, d, 0.01)
            i += 2
        }
    }

    @Test
    fun `水平直线轮廓高度约等于直径且闭合可填充`() {
        val outline = InkGeometry.strokeOutline(
            points = listOf(0f, 0f, 100f, 0f),
            radii = listOf(3f, 3f),
        )
        assertTrue(outline.size >= 12)
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var i = 0
        while (i + 1 < outline.size) {
            minX = minOf(minX, outline[i]); maxX = maxOf(maxX, outline[i])
            minY = minOf(minY, outline[i + 1]); maxY = maxOf(maxY, outline[i + 1])
            i += 2
        }
        assertEquals(6f, maxY - minY, 0.2f)      // 高 ≈ 直径
        assertEquals(106f, maxX - minX, 0.5f)    // 长 ≈ 线长 + 两端圆头
    }

    @Test
    fun `变宽半径反映在轮廓宽度上`() {
        val outline = InkGeometry.strokeOutline(
            points = listOf(0f, 0f, 50f, 0f, 100f, 0f),
            radii = listOf(2f, 6f, 2f),
        )
        // 中段（x≈50）处轮廓 |y| 应接近 6
        var midHalf = 0f
        var i = 0
        while (i + 1 < outline.size) {
            if (outline[i] in 45f..55f) midHalf = maxOf(midHalf, Math.abs(outline[i + 1]))
            i += 2
        }
        assertTrue("中段半宽应≈6，实际 $midHalf", midHalf > 4.5f)
    }

    @Test
    fun `收笔笔锋递减末端半径`() {
        val radii = mutableListOf(3f, 3f, 3f, 3f, 3f, 3f)
        InkGeometry.taperTail(radii)
        assertTrue(radii[5] < radii[4] && radii[4] < radii[3] && radii[3] < radii[2])
    }
}
