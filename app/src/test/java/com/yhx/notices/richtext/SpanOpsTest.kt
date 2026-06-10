package com.yhx.notices.richtext

import com.yhx.notices.domain.richtext.InlineStyles
import com.yhx.notices.domain.richtext.Span
import com.yhx.notices.domain.richtext.SpanOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanOpsTest {

    @Test fun normalize_mergesAdjacentSameStyle() {
        val input = listOf(Span("a"), Span("b"), Span("c", setOf("b")))
        val out = SpanOps.normalize(input)
        assertEquals(listOf(Span("ab"), Span("c", setOf("b"))), out)
    }

    @Test fun normalize_dropsEmptyButKeepsOne() {
        assertEquals(listOf(Span("")), SpanOps.normalize(listOf(Span(""), Span(""))))
    }

    @Test fun splitAt_inMiddleOfSpan() {
        val (l, r) = SpanOps.splitAt(listOf(Span("hello")), 2)
        assertEquals("he", SpanOps.text(l))
        assertEquals("llo", SpanOps.text(r))
    }

    @Test fun splitAt_preservesStyleAcrossCut() {
        val (l, r) = SpanOps.splitAt(listOf(Span("bold", setOf("b"))), 2)
        assertEquals(setOf("b"), l.first().styles)
        assertEquals(setOf("b"), r.first().styles)
    }

    @Test fun applyStyle_addsThenRemovesOnSecondApply() {
        val spans = listOf(Span("hello"))
        val bold = SpanOps.applyStyle(spans, 0, 5, InlineStyles.BOLD)
        assertTrue(SpanOps.hasStyle(bold, 0, 5, InlineStyles.BOLD))
        val unbold = SpanOps.applyStyle(bold, 0, 5, InlineStyles.BOLD)
        assertFalse(SpanOps.hasStyle(unbold, 0, 5, InlineStyles.BOLD))
    }

    @Test fun applyStyle_partialRange() {
        val spans = listOf(Span("hello"))
        val out = SpanOps.applyStyle(spans, 0, 2, InlineStyles.BOLD)
        assertTrue(SpanOps.hasStyle(out, 0, 2, InlineStyles.BOLD))
        assertFalse(SpanOps.hasStyle(out, 2, 5, InlineStyles.BOLD))
    }

    @Test fun applyStyle_colorIsMutuallyExclusive() {
        val spans = listOf(Span("x"))
        val red = SpanOps.applyStyle(spans, 0, 1, InlineStyles.color(0xFF0000))
        val blue = SpanOps.applyStyle(red, 0, 1, InlineStyles.color(0x0000FF))
        val colors = blue.first().styles.filter { InlineStyles.isColor(it) }
        assertEquals(1, colors.size)
        assertEquals(0xFF0000FF.toInt(), InlineStyles.parseColor(colors.first()))
    }

    @Test fun applyTextChange_insertInheritsLeftStyle() {
        val spans = listOf(Span("ab", setOf("b")))
        // 在末尾插入 "c" → "abc"
        val out = SpanOps.applyTextChange(spans, "abc", emptySet())
        assertEquals("abc", SpanOps.text(out))
        assertTrue(out.all { it.styles.contains("b") })
    }

    @Test fun applyTextChange_deleteMiddle() {
        val spans = listOf(Span("hello"))
        val out = SpanOps.applyTextChange(spans, "hlo", emptySet())
        assertEquals("hlo", SpanOps.text(out))
    }

    @Test fun applyTextChange_stickyStyleAppliesToInsertion() {
        val spans = listOf(Span("a"))
        val out = SpanOps.applyTextChange(spans, "ab", setOf(InlineStyles.BOLD))
        // 新插入的 "b" 带 sticky bold
        assertTrue(SpanOps.hasStyle(out, 1, 2, InlineStyles.BOLD))
    }
}
