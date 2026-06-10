package com.yhx.notices.domain.richtext

/**
 * 内联样式 token 约定（见 docs/03 §1.1）：
 *  - "b" 加粗 / "i" 斜体 / "u" 下划线 / "s" 删除线
 *  - "c:RRGGBB" 文字颜色（无 alpha，6 位十六进制）
 *  - "h:RRGGBB" 高亮背景
 *  - "z:0.85" / "z:1.25" 字号缩放（缺省 1.0 即标准）
 */
object InlineStyles {
    const val BOLD = "b"
    const val ITALIC = "i"
    const val UNDERLINE = "u"
    const val STRIKE = "s"

    private const val COLOR_PREFIX = "c:"
    private const val HIGHLIGHT_PREFIX = "h:"
    private const val SIZE_PREFIX = "z:"

    fun color(argb: Int): String = COLOR_PREFIX + hex6(argb)
    fun highlight(argb: Int): String = HIGHLIGHT_PREFIX + hex6(argb)
    fun size(scale: Float): String = SIZE_PREFIX + scale

    fun isColor(token: String) = token.startsWith(COLOR_PREFIX)
    fun isHighlight(token: String) = token.startsWith(HIGHLIGHT_PREFIX)
    fun isSize(token: String) = token.startsWith(SIZE_PREFIX)

    /** 同类互斥：颜色/高亮/字号同一时刻只能有一个值。 */
    fun isSameFamily(a: String, b: String): Boolean = when {
        isColor(a) -> isColor(b)
        isHighlight(a) -> isHighlight(b)
        isSize(a) -> isSize(b)
        else -> a == b
    }

    /** 解析颜色 token → ARGB（不透明）；非颜色返回 null。 */
    fun parseColor(token: String): Int? = parseHex(token, COLOR_PREFIX)
    fun parseHighlight(token: String): Int? = parseHex(token, HIGHLIGHT_PREFIX)
    fun parseSize(token: String): Float? =
        if (isSize(token)) token.removePrefix(SIZE_PREFIX).toFloatOrNull() else null

    private fun parseHex(token: String, prefix: String): Int? {
        if (!token.startsWith(prefix)) return null
        val hex = token.removePrefix(prefix)
        val rgb = hex.toLongOrNull(16) ?: return null
        return (0xFF000000L or (rgb and 0xFFFFFF)).toInt()
    }

    private fun hex6(argb: Int): String =
        String.format("%06X", argb and 0xFFFFFF)
}
