package com.yhx.notices.domain.richtext

/** 从富文本派生纯文本镜像（用于 FTS 搜索与列表预览），见 docs/03 §1.3。 */
object ContentDerive {

    fun plainText(content: NoteContent): String = buildString {
        for (block in content.blocks) {
            when (block) {
                is TextBlock -> appendLine(SpanOps.text(block.spans))
                is ChecklistBlock -> {
                    append(if (block.checked) "[x] " else "[ ] ")
                    appendLine(SpanOps.text(block.spans))
                }
                is ImageBlock -> appendLine("[图片]")
                is AudioBlock -> appendLine("[录音]")
                is SketchBlock -> appendLine("[手写]")
                is TableBlock -> block.rows.forEach { appendLine(it.joinToString("\t")) }
                is DividerBlock -> {}
            }
        }
    }.trim()

    /** 列表预览摘要：取前若干行非空文本。 */
    fun excerpt(content: NoteContent, maxChars: Int = 120): String =
        plainText(content).replace("\n", " ").take(maxChars)

    /** 是否为空文档（无文字、无附件块），用于"空笔记自动丢弃"。 */
    fun isEmpty(content: NoteContent): Boolean = content.blocks.all { block ->
        when (block) {
            is TextBlock -> SpanOps.text(block.spans).isBlank()
            is ChecklistBlock -> SpanOps.text(block.spans).isBlank()
            else -> false // 图片/录音/表格等视为非空
        }
    }
}
