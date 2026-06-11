package com.yhx.notices.data.repository

import com.yhx.notices.data.local.dao.FolderDao
import com.yhx.notices.data.local.dao.FolderWithCount
import com.yhx.notices.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val folderDao: FolderDao,
) {
    fun observeFolders(): Flow<List<FolderWithCount>> = folderDao.observeFoldersWithCount()

    suspend fun create(name: String, color: Int, parentId: Long? = null): Long =
        folderDao.insert(FolderEntity(name = name, color = color, parentId = parentId))

    suspend fun rename(folder: FolderEntity, name: String) =
        folderDao.update(folder.copy(name = name))

    suspend fun recolor(folder: FolderEntity, color: Int) =
        folderDao.update(folder.copy(color = color))

    suspend fun delete(folder: FolderEntity) = folderDao.delete(folder)
}
