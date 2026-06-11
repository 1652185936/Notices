package com.yhx.notices.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yhx.notices.data.local.dao.AttachmentDao
import com.yhx.notices.data.local.dao.FolderDao
import com.yhx.notices.data.local.dao.NoteDao
import com.yhx.notices.data.local.dao.ReminderDao
import com.yhx.notices.data.local.dao.TagDao
import com.yhx.notices.data.local.dao.TodoDao
import com.yhx.notices.data.local.entity.AttachmentEntity
import com.yhx.notices.data.local.entity.FolderEntity
import com.yhx.notices.data.local.entity.NoteEntity
import com.yhx.notices.data.local.entity.NoteFtsEntity
import com.yhx.notices.data.local.entity.NoteTagCrossRef
import com.yhx.notices.data.local.entity.ReminderEntity
import com.yhx.notices.data.local.entity.TagEntity
import com.yhx.notices.data.local.entity.TodoEntity

@Database(
    version = 3,
    entities = [
        NoteEntity::class, NoteFtsEntity::class, FolderEntity::class,
        TagEntity::class, NoteTagCrossRef::class, AttachmentEntity::class,
        TodoEntity::class, ReminderEntity::class,
    ],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NoticesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun todoDao(): TodoDao
    abstract fun tagDao(): TagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "notices.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isCanvas INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN parentId INTEGER")
            }
        }
    }
}
