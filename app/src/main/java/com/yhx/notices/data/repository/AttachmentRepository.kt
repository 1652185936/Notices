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

    /** 保存任意位图为 JPEG 附件（用于 PDF 转图等），返回 attachmentId。 */
    suspend fun importBitmap(noteId: Long, bmp: android.graphics.Bitmap): Long = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        val dest = File(noteDir(noteId), name)
        dest.outputStream().use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        attachmentDao.insert(
            AttachmentEntity(
                noteId = noteId,
                type = AttachmentType.IMAGE,
                fileName = "attachments/$noteId/$name",
                mimeType = "image/jpeg",
                sizeBytes = dest.length(),
                width = bmp.width,
                height = bmp.height,
            )
        )
    }

    /**
     * 用系统 PdfRenderer（无 GMS）把 PDF 各页渲染为白底位图。
     * 最多渲染 [maxPages] 页，每页目标宽 [targetWidth] px 等比缩放。
     */
    suspend fun renderPdfToBitmaps(
        uri: Uri,
        targetWidth: Int = 1500,
        maxPages: Int = 30,
    ): List<android.graphics.Bitmap> = withContext(Dispatchers.IO) {
        val result = ArrayList<android.graphics.Bitmap>()
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            try {
                val count = minOf(renderer.pageCount, maxPages)
                for (idx in 0 until count) {
                    val page = renderer.openPage(idx)
                    try {
                        val srcW = page.width.coerceAtLeast(1)
                        val srcH = page.height.coerceAtLeast(1)
                        val w = targetWidth.coerceAtLeast(1)
                        val h = (srcH.toFloat() / srcW * w).toInt().coerceAtLeast(1)
                        val bmp = android.graphics.Bitmap.createBitmap(
                            w, h, android.graphics.Bitmap.Config.ARGB_8888,
                        )
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(
                            bmp, null, null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                        )
                        result.add(bmp)
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
            }
        }
        result
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
