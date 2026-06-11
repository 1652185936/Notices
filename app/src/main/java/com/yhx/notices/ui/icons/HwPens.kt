package com.yhx.notices.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 拟物笔托盘插画（28×64 视口、笔尖朝下、多色矢量原创绘制）。
 * 使用时 Icon/Image 需传 tint = Color.Unspecified 保留原色。
 */
object HwPens {

    /** 钢笔（深蓝杆 + 银色笔尖） */
    val Fountain: ImageVector by lazy {
        pen("Pen.Fountain") {
            fill("M13.5,5 h1 a5,5 0 0 1 5,5 v23 a5,5 0 0 1 -5,5 h-1 a5,5 0 0 1 -5,-5 V10 a5,5 0 0 1 5,-5 Z", 0xFF2E3440)
            fill("M17.7,7.5 a1.1,1.1 0 0 1 1.1,1.1 v15.8 a1.1,1.1 0 0 1 -2.2,0 V8.6 a1.1,1.1 0 0 1 1.1,-1.1 Z", 0xFFAEB6C2)
            fill("M9.5,37 h9 v7 a4.5,3 0 0 1 -9,0 Z", 0xFF4C566A)
            fill("M10.2,45 H17.8 L14,60 Z", 0xFFD8DEE9)
            strokePath("M14,48.5 V56.5", 0xFF7A8290, 0.9f)
            fill("M14,49 a1,1 0 1 1 -0.01,0 Z", 0xFF7A8290)
        }
    }

    /** 秀丽笔（米白杆 + 黑色软毫） */
    val Calligraphy: ImageVector by lazy {
        pen("Pen.Calligraphy") {
            fill("M13.5,5 h1 a5,5 0 0 1 5,5 v24 a5,5 0 0 1 -5,5 h-1 a5,5 0 0 1 -5,-5 V10 a5,5 0 0 1 5,-5 Z", 0xFFF4EFE6)
            strokePath("M13.5,5 h1 a5,5 0 0 1 5,5 v24 a5,5 0 0 1 -5,5 h-1 a5,5 0 0 1 -5,-5 V10 a5,5 0 0 1 5,-5 Z", 0xFFD8D2C6, 0.8f)
            fill("M10,36.5 h8 a1.5,1.5 0 0 1 1.5,1.5 v2.5 a1.5,1.5 0 0 1 -1.5,1.5 h-8 a1.5,1.5 0 0 1 -1.5,-1.5 v-2.5 a1.5,1.5 0 0 1 1.5,-1.5 Z", 0xFF2B2B2B)
            fill("M10.5,42 C10.5,46 11.5,50 14,59.5 C16.5,50 17.5,46 17.5,42 Z", 0xFF1A1A1A)
            strokePath("M14,59.5 C13,53 12.6,48 12.8,42.5", 0xFF3A3A3A, 0.6f)
        }
    }

    /** 铅笔（粉色橡皮头 + 黄杆 + 木削尖） */
    val Pencil: ImageVector by lazy {
        pen("Pen.Pencil") {
            fill("M9.5,4.5 h9 a2,2 0 0 1 2,2 V10 h-13 V6.5 a2,2 0 0 1 2,-2 Z", 0xFFF2A6A6)
            fill("M7.5,10 h13 v4.5 h-13 Z", 0xFFC9CDD4)
            strokePath("M7.5,11.4 h13 M7.5,13 h13", 0xFFAEB4BC, 0.7f)
            fill("M7.5,14.5 h13 v31 h-13 Z", 0xFFF7C873)
            fill("M11.2,14.5 h1.6 v31 h-1.6 Z", 0xFFE8AF52)
            fill("M15.6,14.5 h1.6 v31 h-1.6 Z", 0xFFE8AF52)
            fill("M7.5,45.5 H20.5 L14,58.5 Z", 0xFFEDD3AC)
            fill("M12.2,54.2 L14,58.5 L15.8,54.2 Z", 0xFF4A4A4A)
        }
    }

    /** 马克笔（红色帽杆 + 楔形头） */
    val Marker: ImageVector by lazy {
        pen("Pen.Marker") {
            fill("M10.5,4.5 h7 a2.5,2.5 0 0 1 2.5,2.5 v6.5 h-12 V7 a2.5,2.5 0 0 1 2.5,-2.5 Z", 0xFFC03030)
            fill("M10,13.5 h8 a2,2 0 0 1 2,2 v27 h-12 v-27 a2,2 0 0 1 2,-2 Z", 0xFFE05050)
            fill("M8,20 h12 v13 h-12 Z", 0xFFF3F3F3)
            fill("M10,42.5 h8 l-0.6,5 h-6.8 Z", 0xFFC9CDD4)
            fill("M11.2,47.5 h5.6 L15.6,58 h-3.2 Z", 0xFFC03030)
        }
    }

    /** 荧光笔（黄色粗杆 + 方头） */
    val Highlighter: ImageVector by lazy {
        pen("Pen.Highlighter") {
            fill("M11,5 h6 a3,3 0 0 1 3,3 v31 h-12 V8 a3,3 0 0 1 3,-3 Z", 0xFFFFD84D)
            fill("M8,33.5 h12 v5.5 h-12 Z", 0xFFE6B800)
            fill("M10,39 h8 v4 h-8 Z", 0xFFD9D9D9)
            fill("M10.6,43 h6.8 L16.2,57.5 h-4.4 Z", 0xFFFFC107)
        }
    }

    /** 橡皮（蓝色套纸 + 白色胶体） */
    val Eraser: ImageVector by lazy {
        pen("Pen.Eraser") {
            fill("M8,16 h12 a2.5,2.5 0 0 1 2.5,2.5 V34 h-17 V18.5 A2.5,2.5 0 0 1 8,16 Z", 0xFF5B8DEF)
            fill("M5.5,34 h17 V51 a2.5,2.5 0 0 1 -2.5,2.5 H8 A2.5,2.5 0 0 1 5.5,51 Z", 0xFFFAFAFA)
            strokePath("M5.5,34 h17 V51 a2.5,2.5 0 0 1 -2.5,2.5 H8 A2.5,2.5 0 0 1 5.5,51 Z", 0xFFE2E4E8, 0.7f)
            strokePath("M8.5,22 h11 M8.5,26 h7", 0xFFBFD2F8, 1.6f)
        }
    }
}

/** 构建 28×64 视口的笔插画。 */
private fun pen(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 28.dp,
        defaultHeight = 64.dp,
        viewportWidth = 28f,
        viewportHeight = 64f,
    ).apply(build).build()

/** 填充路径（指定颜色）。 */
private fun ImageVector.Builder.fill(d: String, argb: Long) {
    addPath(pathData = addPathNodes(d), fill = SolidColor(Color(argb)))
}

/** 描边路径（指定颜色与线宽）。 */
private fun ImageVector.Builder.strokePath(d: String, argb: Long, width: Float) {
    addPath(
        pathData = addPathNodes(d),
        fill = null,
        stroke = SolidColor(Color(argb)),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}
