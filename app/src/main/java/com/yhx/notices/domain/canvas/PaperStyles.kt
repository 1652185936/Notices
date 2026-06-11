package com.yhx.notices.domain.canvas

/**
 * 纸张底纹样式定义（纯 Kotlin / 无平台依赖）。
 * 屏幕端（CanvasScreen.drawCanvasBackground）与导出端（ExportManager.renderCanvas）
 * 共用本文件的 id 列表、底色与标签，保证「所见即所得」。
 *
 * 设计原则（高级感）：线条细、低对比、低透明；主线略深；部分样式带暖纸底色。
 */
object PaperStyles {

    /** 全部可选底纹 id，顺序即菜单展示顺序。 */
    val ALL: List<String> = listOf(
        "blank", "grid", "lines", "dots",
        "cornell", "legal", "graph", "tianzige", "staff", "cream-dots",
    )

    /** 世界坐标基础间距（与屏幕端 spacing=48 世界单位、导出端一致）。 */
    const val BASE_SPACING = 48f

    /** 田字格 / 工程主格等较大格的世界尺寸。 */
    const val GRID_SPACING = 96f

    /** 五线谱单条间距与组间距（世界单位）。 */
    const val STAFF_LINE = 14f
    const val STAFF_GROUP_GAP = 64f

    /** 纹路线色（普通 / 主线 / 暖色边距线）。ARGB。 */
    const val LINE = 0x14000000
    const val LINE_MAJOR = 0x22000000
    const val MARGIN_RED = 0x33D86A5A.toInt()   // 信纸左侧淡红边距线

    /** 纸张底色（ARGB）。空白 / 网格等为纯白；暖纸样式为米白。 */
    fun paperBaseColor(style: String): Int = when (style) {
        "legal", "tianzige", "cream-dots" -> 0xFFFCFBF7.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    fun paperLabel(style: String): String = when (style) {
        "blank" -> "空白"
        "grid" -> "网格"
        "lines" -> "横线"
        "dots" -> "点阵"
        "cornell" -> "康奈尔"
        "legal" -> "信纸"
        "graph" -> "工程格"
        "tianzige" -> "田字格"
        "staff" -> "五线谱"
        "cream-dots" -> "暖点阵"
        else -> style
    }
}
