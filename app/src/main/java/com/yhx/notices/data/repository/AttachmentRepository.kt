package com.yhx.notices.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.yhx.notices.data.local.dao.AttachmentDao
import com.yhx.notices.data.local.entity.AttachmentEntity
import com.yhx.notices.domain.model.AttachmentType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentDao: AttachmentDao,
) {
    private fun noteDir(noteId: Long): File =
        File(context.filesDir, "attachments/$noteId").apply { mkdirs() }

    /** 导入图片：拷贝到私有目录并入库，返回 attachmentId。 */
    suspend fun importImage(noteId: Long, uri: Uri): Long = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        val dest = File(noteDir(noteId), name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(dest.absolutePath, opts)
        attachmentDao.insert(
            AttachmentEntity(
                noteId = noteId,
                type = AttachmentType.IMAGE,
                fileName = "attachments/$noteId/$name",
                mimeType = "image/jpeg",
                sizeBytes = dest.length(),
                width = opts.outWidth.takeIf { it > 0 },
                height = opts.outHeight.takeIf { it > 0 },
            )
        )
    }

    /** 保存手写位图为 PNG 附件，返回 attachmentId。 */
    suspend fun saveSketch(noteId: Long, bitmap: android.graphics.Bitmap): Long = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.png"
        val dest = File(noteDir(noteId), name)
        dest.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        attachmentDao.insert(
            AttachmentEntity(
                noteId = noteId,
                type = AttachmentType.SKETCH,
                fileName = "attachments/$noteId/$name",
                mimeType = "image/png",
                sizeBytes = dest.length(),
                width = bitmap.width,
                height = bitmap.height,
            )
        )
    }

    /** 保存录音文件为附件，返回 attachmentId。 */
    suspend fun saveAudio(noteId: Long, source: File, durationMs: Long): Long = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.m4a"
        val dest = File(noteDir(noteId), name)
        source.copyTo(dest, overwrite = true)
        runCatching { source.delete() }
        attachmentDao.insert(
            AttachmentEntity(
                noteId = noteId,
                type = AttachmentType.AUDIO,
                fileName = "attachments/$noteId/$name",
                mimeType = "audio/mp4",
                sizeBytes = dest.length(),
                durationMs = durationMs,
            )
        )
    }

    suspend fun audioDuration(attachmentId: Long): Long = withContext(Dispatchers.IO) {
        attachmentDao.getById(attachmentId)?.durationMs ?: 0L
    }

    suspend fun resolvePath(attachmentId: Long): File? = withContext(Dispatchers.IO) {
        val entity = attachmentDao.getById(attachmentId) ?: return@withContext null
        File(context.filesDir, entity.fileName)
    }

    fun fileOf(relativePath: String): File = File(context.filesDir, relativePath)
}
