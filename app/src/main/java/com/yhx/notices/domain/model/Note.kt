package com.yhx.notices.domain.model

import com.yhx.notices.domain.richtext.NoteContent

/** 编辑态完整笔记（含解析后的富文本）。 */
data class Note(
    val id: Long,
    val folderId: Long?,
    val title: String,
    val content: NoteContent,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val isEncrypted: Boolean,
    val isTodoNote: Boolean,
    val skin: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 列表页轻量投影（不含完整富文本，见 docs/02 §6）。 */
data class NoteListItem(
    val id: Long,
    val title: String,
    val excerpt: String,
    val firstImagePath: String?,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val isEncrypted: Boolean,
    val isTodoNote: Boolean,
    val isCanvas: Boolean,
    val skin: String,
    val updatedAt: Long,
)
