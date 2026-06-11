package com.yhx.notices.di

import android.content.Context
import androidx.room.Room
import com.yhx.notices.data.local.NoticesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NoticesDatabase =
        Room.databaseBuilder(context, NoticesDatabase::class.java, NoticesDatabase.NAME)
            .addMigrations(NoticesDatabase.MIGRATION_1_2, NoticesDatabase.MIGRATION_2_3)
            .build()

    @Provides fun provideNoteDao(db: NoticesDatabase) = db.noteDao()
    @Provides fun provideFolderDao(db: NoticesDatabase) = db.folderDao()
    @Provides fun provideTodoDao(db: NoticesDatabase) = db.todoDao()
    @Provides fun provideTagDao(db: NoticesDatabase) = db.tagDao()
    @Provides fun provideAttachmentDao(db: NoticesDatabase) = db.attachmentDao()
    @Provides fun provideReminderDao(db: NoticesDatabase) = db.reminderDao()
}
