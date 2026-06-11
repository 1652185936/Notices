package com.yhx.notices.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yhx.notices.domain.model.AttachmentType
import com.yhx.notices.domain.model.ReminderTarget
import com.yhx.notices.domain.model.RepeatRule
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = FolderEntity::class,
        parentColumns = ["id"], childColumns = ["folderId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index("folderId"),
        Index("updatedAt"),
        Index("deletedAt"),
        Index(value = ["isPinned", "updatedAt"]),
    ]
)
@Serializable
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val title: String = "",
    val contentJson: String = "",
    val plainText: String = "",
    val excerptImagePath: String? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isEncrypted: Boolean = false,
    val isTodoNote: Boolean = false,
    val isCanvas: Boolean = false,
    val skin: String = "default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncEtag: String? = null,
    val syncDirty: Boolean = true,
)

@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    val plainText: String,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
)

@Entity(
    tableName = "note_tag_cross_ref",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class, parentColumns = ["id"],
            childColumns = ["noteId"], onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class, parentColumns = ["id"],
            childColumns = ["tagId"], onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("tagId")]
)
data class NoteTagCrossRef(val noteId: Long, val tagId: Long)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class, parentColumns = ["id"],
        childColumns = ["noteId"], onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val type: AttachmentType,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sketchDataPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "todos",
    indices = [Index("dueAt"), Index("parentId"), Index(value = ["isDone", "dueAt"])]
)
@Serializable
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val content: String,
    val isDone: Boolean = false,
    val doneAt: Long? = null,
    val dueAt: Long? = null,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "reminders",
    indices = [Index("triggerAt"), Index(value = ["targetType", "targetId"])]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: ReminderTarget,
    val targetId: Long,
    val triggerAt: Long,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val geoRadius: Float? = null,
    val geoOnEnter: Boolean = true,
    val isActive: Boolean = true,
)
