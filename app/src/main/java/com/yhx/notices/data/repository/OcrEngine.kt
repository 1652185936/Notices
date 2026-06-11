package com.yhx.notices.data.repository

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** 端侧中文 OCR（ML Kit，离线模型）。见 docs/01 S-04。 */
@Singleton
class OcrEngine @Inject constructor() {

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /** 识别图片文件中的文字，返回识别文本（失败返回空串）。 */
    suspend fun recognize(path: String): String = suspendCancellableCoroutine { cont ->
        val bitmap = BitmapFactory.decodeFile(File(path).absolutePath)
        if (bitmap == null) {
            cont.resume("")
            return@suspendCancellableCoroutine
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result.text) }
            .addOnFailureListener { cont.resume("") }
    }
}
