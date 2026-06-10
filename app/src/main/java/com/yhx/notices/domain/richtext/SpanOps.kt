package com.yhx.notices.domain.richtext

/**
 * Span 级文本与样式操作。所有函数纯函数、可单测（见 docs/03 §1.1 不变式、§2.4 样式算法）。
 */
object SpanOps {

    /** 块内全部 span 拼接的纯文本长度。 */
    fun text(spans: List<Span>): String = buildString { spans.forEach { append(it.text) } }

    /**
     * 规范化：移除空 span（保留至少一个）、合并相邻同样式 span。
     * 保证不变式：相邻 span styles 不同；spans 非空。
     */
    fun normalize(spans: List<Span>): List<Span> {
        val nonEmpty = spans.filter { it.text.isNotEmpty() }
        if (nonEmpty.isEmpty()) return listOf(Span(""))
        val result = ArrayList<Span>()
        for (span in nonEmpty) {
            val last = result.lastOrNull()
            if (last != null && last.styles == span.styles) {
                result[result.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                result.add(span)
            }
        }
        return result
    }

    /**
     * 在字符偏移 [offset] 处把 spans 切成两段，返回 (左, 右)。
     * 用于劈块、样式区间切分。offset 越界自动夹取。
     */
    fun splitAt(spans: List<Span>, offset: Int): Pair<List<Span>, List<Span>> {
        val total = text(spans).length
        val pos = offset.coerceIn(0, total)
        val left = ArrayList<Span>()
        val right = ArrayList<Span>()
        var cursor = 0
        for (span in spans) {
            val end = cursor + span.text.length
            when {
                end <= pos -> left.add(span)
                cursor >= pos -> right.add(span)
                else -> {
                    val cut = pos - cursor
                    left.add(span.copy(text = span.text.substring(0, cut)))
                    right.add(span.copy(text = span.text.substring(cut)))
                }
            }
            cursor = end
        }
        return normalize(left) to normalize(right)
    }

    /** 拼接两段 spans 并规范化（用于退格合并块）。 */
    fun concat(a: List<Span>, b: List<Span>): List<Span> = normalize(a + b)

    /**
     * 对 [start, end) 区间应用/取消样式 [style]（见 docs/03 §2.4）。
     * 语义：区间内全部已含该样式 → 移除；否则 → 添加。
     * 颜色/高亮/字号同类替换。collapsed 区间原样返回（粘性样式由上层处理）。
     */
    fun applyStyle(spans: List<Span>, start: Int, end: Int, style: String): List<Span> {
        if (start >= end) return spans
        val (left, restA) = splitAt(spans, start)
        val (mid, right) = splitAt(restA, end - start)
        val shouldRemove = mid.all { it.styles.any { s -> s == style } }
        val newMid = mid.map { span ->
            val cleaned = span.styles.filterNot { InlineStyles.isSameFamily(it, style) }.toMutableSet()
            if (!shouldRemove) cleaned.add(style)
            span.copy(styles = cleaned)
        }
        return normalize(left + newMid + right)
    }

    /** 查询 [start, end) 区间是否整体含某样式（用于工具栏高亮态）。 */
    fun hasStyle(spans: List<Span>, start: Int, end: Int, style: String): Boolean {
        if (start >= end) return false
        val (_, restA) = splitAt(spans, start)
        val (mid, _) = splitAt(restA, end - start)
        return mid.isNotEmpty() && mid.all { it.styles.contains(style) }
    }

    /**
     * 文本变更后重建 spans：给定旧 spans、新整段纯文本、变更区间。
     * 采用最长公共前后缀 diff（输入法单次变更必为连续区间）。
     * 新插入文本继承「光标左侧 span 样式 ∪ stickyStyles」。
     */
    fun applyTextChange(
        oldSpans: List<Span>,
        newText: String,
        stickyStyles: Set<String>,
    ): List<Span> {
        val oldText = text(oldSpans)
        if (oldText == newText) return oldSpans

        // 公共前缀
        var prefix = 0
        val minLen = minOf(oldText.length, newText.length)
        while (prefix < minLen && oldText[prefix] == newText[prefix]) prefix++
        // 公共后缀（不与前缀重叠）
        var suffix = 0
        while (suffix < minLen - prefix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++

        val removedEnd = oldText.length - suffix
        val inserted = newText.substring(prefix, newText.length - suffix)

        // 删除 [prefix, removedEnd)
        val (left, afterRemoved) = run {
            val (l, rest) = splitAt(oldSpans, prefix)
            val (_, r) = splitAt(rest, removedEnd - prefix)
            l to r
        }
        // 插入文本继承左侧样式
        val inheritStyles = left.lastOrNull()?.styles ?: emptySet()
        val insertStyles = if (inserted.isEmpty()) emptySet() else (inheritStyles + stickyStyles)
        val insertSpan = if (inserted.isEmpty()) emptyList() else listOf(Span(inserted, insertStyles))

        return normalize(left + insertSpan + afterRemoved)
    }
}
