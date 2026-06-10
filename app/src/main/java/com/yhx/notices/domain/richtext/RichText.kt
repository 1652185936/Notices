package com.yhx.notices.domain.richtext

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 富文本块模型，见 docs/03-富文本编辑器详细设计.md。
 *
 * 实现取舍：内联样式以「字符串 token」承载（而非多态 sealed 类），
 * 以最大化 kotlinx.serialization 的稳定性与 round-trip 可靠性。
 * token 约定见 [InlineStyles]。
 */
@Serializable
data class NoteContent(
    val version: Int = 1,
    val blocks: List<Block> = listOf(TextBlock(kind = TextKind.PARAGRAPH)),
) {
    companion object {
        /** 空文档：单个空段落（满足"文档至少含一个块"的不变式）。 */
        fun empty(): NoteContent = NoteContent()
    }
}

enum class TextKind { PARAGRAPH, H1, H2, H3, BULLET, NUMBERED, QUOTE }

@Serializable
sealed interface Block {
    val id: String
}

@Serializable
@SerialName("text")
data class TextBlock(
    override val id: String = newId(),
    val kind: TextKind = TextKind.PARAGRAPH,
    val spans: List<Span> = listOf(Span("")),
    val indent: Int = 0,
) : Block

@Serializable
@SerialName("checklist")
data class ChecklistBlock(
    override val id: String = newId(),
    val checked: Boolean = false,
    val spans: List<Span> = listOf(Span("")),
    val indent: Int = 0,
) : Block

@Serializable
@SerialName("image")
data class ImageBlock(
    override val id: String = newId(),
    val attachmentId: Long,
) : Block

@Serializable
@SerialName("audio")
data class AudioBlock(
    override val id: String = newId(),
    val attachmentId: Long,
) : Block

@Serializable
@SerialName("sketch")
data class SketchBlock(
    override val id: String = newId(),
    val attachmentId: Long,
) : Block

@Serializable
@SerialName("divider")
data class DividerBlock(
    override val id: String = newId(),
) : Block

@Serializable
@SerialName("table")
data class TableBlock(
    override val id: String = newId(),
    val rows: List<List<String>> = listOf(listOf("", ""), listOf("", "")),
) : Block

@Serializable
data class Span(
    val text: String,
    val styles: Set<String> = emptySet(),
)

/** 生成块级稳定 id。 */
fun newId(): String = java.util.UUID.randomUUID().toString()
