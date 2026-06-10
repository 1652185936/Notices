package com.yhx.notices.domain.richtext

/**
 * 块级结构操作（劈/合/转换/插入）。纯函数，返回新块列表与建议焦点。
 * 见 docs/03 §2.5 键盘语义、§2.6 块操作。
 */
object BlockOps {

    data class Result(
        val blocks: List<Block>,
        val focusBlockId: String,
        val focusOffset: Int,
    )

    private fun spansOf(b: Block): List<Span>? = when (b) {
        is TextBlock -> b.spans
        is ChecklistBlock -> b.spans
        else -> null
    }

    private fun withSpans(b: Block, spans: List<Span>): Block = when (b) {
        is TextBlock -> b.copy(spans = spans)
        is ChecklistBlock -> b.copy(spans = spans)
        else -> b
    }

    /** 回车：在 [blockId] 的 [offset] 处劈成两块。列表/清单续延同类型，空项则降级段落。 */
    fun splitBlock(blocks: List<Block>, blockId: String, offset: Int): Result {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return Result(blocks, blockId, offset)
        val block = blocks[index]
        val spans = spansOf(block) ?: return Result(blocks, blockId, offset)

        // 空的列表/清单项回车 → 降级为普通段落（二段退出）
        if (SpanOps.text(spans).isEmpty() && isListLike(block)) {
            val demoted = TextBlock(id = block.id, kind = TextKind.PARAGRAPH)
            return Result(blocks.toMutableList().also { it[index] = demoted }, block.id, 0)
        }

        val (left, right) = SpanOps.splitAt(spans, offset)
        val leftBlock = withSpans(block, left)
        val rightBlock = newContinuation(block, right)

        val newList = blocks.toMutableList().apply {
            set(index, leftBlock)
            add(index + 1, rightBlock)
        }
        return Result(newList, rightBlock.id, 0)
    }

    private fun isListLike(b: Block): Boolean = when (b) {
        is ChecklistBlock -> true
        is TextBlock -> b.kind == TextKind.BULLET || b.kind == TextKind.NUMBERED
        else -> false
    }

    /** 续延块：列表/清单延续同类型（清单 checked 重置），其余新块为普通段落。 */
    private fun newContinuation(source: Block, spans: List<Span>): Block = when (source) {
        is ChecklistBlock -> ChecklistBlock(checked = false, spans = spans, indent = source.indent)
        is TextBlock -> when (source.kind) {
            TextKind.BULLET, TextKind.NUMBERED ->
                TextBlock(kind = source.kind, spans = spans, indent = source.indent)
            else -> TextBlock(kind = TextKind.PARAGRAPH, spans = spans)
        }
        else -> TextBlock(kind = TextKind.PARAGRAPH, spans = spans)
    }

    /**
     * 退格于块首：列表/清单先降级为段落；普通块则与上一文本块合并。
     * 上一块为非文本（图片等）时仅返回原状（焦点移动由 UI 处理）。
     */
    fun backspaceAtStart(blocks: List<Block>, blockId: String): Result {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return Result(blocks, blockId, 0)
        val block = blocks[index]

        if (isListLike(block)) {
            val demoted = TextBlock(id = block.id, kind = TextKind.PARAGRAPH, spans = spansOf(block)!!)
            return Result(blocks.toMutableList().also { it[index] = demoted }, block.id, 0)
        }
        if (index == 0) return Result(blocks, blockId, 0)

        val prev = blocks[index - 1]
        val prevSpans = spansOf(prev)
        val curSpans = spansOf(block)
        if (prevSpans == null || curSpans == null) {
            return Result(blocks, blockId, 0) // 上块非文本，交给 UI 处理焦点
        }
        val mergeOffset = SpanOps.text(prevSpans).length
        val merged = withSpans(prev, SpanOps.concat(prevSpans, curSpans))
        val newList = blocks.toMutableList().apply {
            set(index - 1, merged)
            removeAt(index)
        }
        return Result(newList, merged.id, mergeOffset)
    }

    /** 转换块类型（保留 spans）。 */
    fun changeKind(blocks: List<Block>, blockId: String, kind: TextKind): List<Block> {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return blocks
        val spans = spansOf(blocks[index]) ?: return blocks
        return blocks.toMutableList().also {
            it[index] = TextBlock(id = blockId, kind = kind, spans = spans)
        }
    }

    /** 转为清单块（勾选项）。 */
    fun toChecklist(blocks: List<Block>, blockId: String): List<Block> {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return blocks
        val spans = spansOf(blocks[index]) ?: return blocks
        return blocks.toMutableList().also {
            it[index] = ChecklistBlock(id = blockId, spans = spans)
        }
    }

    /** 在指定块后插入新块；返回新列表。 */
    fun insertAfter(blocks: List<Block>, blockId: String, newBlock: Block): List<Block> {
        val index = blocks.indexOfFirst { it.id == blockId }
        val at = if (index < 0) blocks.size else index + 1
        return blocks.toMutableList().also { it.add(at, newBlock) }
    }

    /** 删除块；若删空则补一个空段落。 */
    fun removeBlock(blocks: List<Block>, blockId: String): List<Block> {
        val filtered = blocks.filterNot { it.id == blockId }
        return filtered.ifEmpty { listOf(TextBlock(kind = TextKind.PARAGRAPH)) }
    }

    /** 移动块（拖拽排序）。 */
    fun move(blocks: List<Block>, from: Int, to: Int): List<Block> {
        if (from !in blocks.indices || to !in blocks.indices) return blocks
        return blocks.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    /** Markdown 式自动转换："- " / "1. " / "[] " 开头。返回转换后块与剩余文本，未命中返回 null。 */
    fun autoConvert(block: TextBlock): Block? {
        if (block.kind != TextKind.PARAGRAPH) return null
        val t = SpanOps.text(block.spans)
        return when {
            t == "- " || t == "* " -> TextBlock(id = block.id, kind = TextKind.BULLET)
            t == "1. " -> TextBlock(id = block.id, kind = TextKind.NUMBERED)
            t == "[] " || t == "[ ] " -> ChecklistBlock(id = block.id)
            else -> null
        }
    }
}
