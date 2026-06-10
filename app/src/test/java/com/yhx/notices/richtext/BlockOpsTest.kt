package com.yhx.notices.richtext

import com.yhx.notices.domain.richtext.BlockOps
import com.yhx.notices.domain.richtext.ChecklistBlock
import com.yhx.notices.domain.richtext.Span
import com.yhx.notices.domain.richtext.SpanOps
import com.yhx.notices.domain.richtext.TextBlock
import com.yhx.notices.domain.richtext.TextKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockOpsTest {

    @Test fun splitBlock_splitsParagraphAtOffset() {
        val b = TextBlock(id = "1", spans = listOf(Span("helloworld")))
        val r = BlockOps.splitBlock(listOf(b), "1", 5)
        assertEquals(2, r.blocks.size)
        assertEquals("hello", SpanOps.text((r.blocks[0] as TextBlock).spans))
        assertEquals("world", SpanOps.text((r.blocks[1] as TextBlock).spans))
        assertEquals(r.blocks[1].id, r.focusBlockId)
    }

    @Test fun splitBlock_checklistContinuesAsChecklist() {
        val b = ChecklistBlock(id = "1", spans = listOf(Span("milk")))
        val r = BlockOps.splitBlock(listOf(b), "1", 4)
        assertTrue(r.blocks[1] is ChecklistBlock)
    }

    @Test fun splitBlock_emptyChecklistDemotesToParagraph() {
        val b = ChecklistBlock(id = "1", spans = listOf(Span("")))
        val r = BlockOps.splitBlock(listOf(b), "1", 0)
        assertEquals(1, r.blocks.size)
        assertTrue(r.blocks[0] is TextBlock)
        assertEquals(TextKind.PARAGRAPH, (r.blocks[0] as TextBlock).kind)
    }

    @Test fun backspaceAtStart_mergesWithPrevious() {
        val a = TextBlock(id = "1", spans = listOf(Span("foo")))
        val b = TextBlock(id = "2", spans = listOf(Span("bar")))
        val r = BlockOps.backspaceAtStart(listOf(a, b), "2")
        assertEquals(1, r.blocks.size)
        assertEquals("foobar", SpanOps.text((r.blocks[0] as TextBlock).spans))
        assertEquals(3, r.focusOffset)
    }

    @Test fun backspaceAtStart_listDemotesBeforeMerge() {
        val a = TextBlock(id = "1", spans = listOf(Span("x")))
        val b = TextBlock(id = "2", kind = TextKind.BULLET, spans = listOf(Span("y")))
        val r = BlockOps.backspaceAtStart(listOf(a, b), "2")
        assertEquals(2, r.blocks.size)
        assertEquals(TextKind.PARAGRAPH, (r.blocks[1] as TextBlock).kind)
    }

    @Test fun autoConvert_dashBecomesBullet() {
        val b = TextBlock(id = "1", spans = listOf(Span("- ")))
        val out = BlockOps.autoConvert(b)
        assertTrue(out is TextBlock && out.kind == TextKind.BULLET)
    }

    @Test fun autoConvert_bracketBecomesChecklist() {
        val b = TextBlock(id = "1", spans = listOf(Span("[] ")))
        assertTrue(BlockOps.autoConvert(b) is ChecklistBlock)
    }

    @Test fun removeBlock_neverEmpty() {
        val b = TextBlock(id = "1", spans = listOf(Span("only")))
        val out = BlockOps.removeBlock(listOf(b), "1")
        assertEquals(1, out.size)
    }

    @Test fun move_reorders() {
        val a = TextBlock(id = "1"); val b = TextBlock(id = "2"); val c = TextBlock(id = "3")
        val out = BlockOps.move(listOf(a, b, c), 0, 2)
        assertEquals(listOf("2", "3", "1"), out.map { it.id })
    }
}
