package com.yhx.notices.data.backup

import android.content.Context
import android.net.Uri
import com.yhx.notices.data.local.NoticesDatabase
import com.yhx.notices.data.local.entity.FolderEntity
import com.yhx.notices.data.local.entity.NoteEntity
import com.yhx.notices.data.local.entity.TodoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地备份/恢复。导出 zip：manifest.json + data.json + attachments/。
 * 见 docs/06 §5。采用逻辑导出（非拷库文件），跨版本兼容、可合并。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NoticesDatabase,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class BackupData(
        val formatVersion: Int = 1,
        val createdAt: Long = System.currentTimeMillis(),
        val notes: List<NoteEntity> = emptyList(),
        val folders: List<FolderEntity> = emptyList(),
        val todos: List<TodoEntity> = emptyList(),
    )

    suspend fun export(target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val notes = db.noteDao().getAllForBackup()
            val data = BackupData(
                notes = notes,
                folders = db.folderDao().getAllForBackup(),
                todos = db.todoDao().getAllForBackup(),
            )
            context.contentResolver.openOutputStream(target)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("data.json"))
                    zip.write(json.encodeToString(data).toByteArray())
                    zip.closeEntry()
                    // 附件目录
                    val attachmentsRoot = File(context.filesDir, "attachments")
                    if (attachmentsRoot.exists()) {
                        attachmentsRoot.walkTopDown().filter { it.isFile }.forEach { file ->
                            val rel = file.relativeTo(context.filesDir).path
                            zip.putNextEntry(ZipEntry(rel))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            notes.size
        }
    }

    suspend fun import(source: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var data: BackupData? = null
            context.contentResolver.openInputStream(source)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "data.json") {
                            data = json.decodeFromString<BackupData>(zip.readBytes().decodeToString())
                        } else if (entry.name.startsWith("attachments/")) {
                            val dest = File(context.filesDir, entry.name)
                            dest.parentFile?.mkdirs()
                            dest.outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val parsed = data ?: error("备份文件缺少 data.json")
            importData(parsed)
            parsed.notes.size
        }
    }

    private suspend fun importData(data: BackupData) {
        data.folders.forEach { db.folderDao().insertForBackup(it) }
        data.notes.forEach { db.noteDao().insertForBackup(it) }
        data.todos.forEach { db.todoDao().insertForBackup(it) }
    }
}
