package com.yhx.notices.richtext

import com.yhx.notices.domain.richtext.ChecklistBlock
import com.yhx.notices.domain.richtext.ContentDerive
import com.yhx.notices.domain.richtext.ImageBlock
import com.yhx.notices.domain.richtext.NoteContent
import com.yhx.notices.domain.richtext.RichTextJson
import com.yhx.notices.domain.richtext.Span
import com.yhx.notices.domain.richtext.TextBlock
import com.yhx.notices.domain.richtext.TextKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {

    private val sample = NoteContent(
        blocks = listOf(
            TextBlock(id = "a1", kind = TextKind.H1, spans = listOf(Span("周末计划"))),
            ChecklistBlock(id = "a2", checked = true, spans = listOf(Span("买牛奶"))),
            TextBlock(
                id = "a3", kind = TextKind.PARAGRAPH,
                spans = listOf(Span("记得"), Span("重要", setOf("b", "c:FF0000")))
            ),
            ImageBlock(id = "a4", attachmentId = 1024),
        )
    )

    @Test fun roundTrip_isLossless() {
        val json = RichTextJson.encode(sample)
        val back = RichTextJson.decode(json)
        assertEquals(sample, back)
    }

    @Test fun decode_blankReturnsEmptyDoc() {
        val c = RichTextJson.decode("")
        assertNotNull(c)
        assertEquals(1, c!!.blocks.size)
    }

    @Test fun decode_garbageReturnsNull() {
        assertEquals(null, RichTextJson.decode("{not valid json"))
    }

    @Test fun decode_unknownKeysIgnored() {
        val json = """{"version":1,"blocks":[{"type":"text","id":"x","kind":"PARAGRAPH",""" +
            """"spans":[{"text":"hi","styles":[]}],"indent":0,"futureField":42}]}"""
        val c = RichTextJson.decode(json)
        assertNotNull(c)
        assertEquals("hi", (c!!.blocks[0] as TextBlock).spans[0].text)
    }

    @Test fun plainText_includesChecklistMarkers() {
        val text = ContentDerive.plainText(sample)
        assertTrue(text.contains("周末计划"))
        assertTrue(text.contains("[x] 买牛奶"))
        assertTrue(text.contains("[图片]"))
    }

    @Test fun isEmpty_trueForBlankParagraph() {
        assertTrue(ContentDerive.isEmpty(NoteContent.empty()))
    }

    @Test fun isEmpty_falseWhenHasImage() {
        val c = NoteContent(blocks = listOf(ImageBlock(attachmentId = 1)))
        assertTrue(!ContentDerive.isEmpty(c))
    }
}
