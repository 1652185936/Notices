package com.yhx.notices.data.repository

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 录音 + 播放（MediaRecorder / MediaPlayer 封装）。见 docs/01 N-05。 */
@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordStartAt: Long = 0
    private var player: MediaPlayer? = null

    fun startRecording(): Boolean {
        stopPlayback()
        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recordingFile = file
            recordStartAt = System.currentTimeMillis()
            true
        }.getOrElse { runCatching { r.release() }; false }
    }

    /** 停止录音，返回 (文件, 时长ms)；失败返回 null。 */
    fun stopRecording(): Pair<File, Long>? {
        val r = recorder ?: return null
        val file = recordingFile
        val duration = System.currentTimeMillis() - recordStartAt
        recorder = null
        recordingFile = null
        return runCatching {
            r.stop(); r.release()
            if (file != null && file.exists()) file to duration else null
        }.getOrElse { runCatching { r.release() }; null }
    }

    fun play(file: File, onComplete: () -> Unit) {
        stopPlayback()
        player = MediaPlayer().apply {
            runCatching {
                setDataSource(file.absolutePath)
                setOnCompletionListener { onComplete(); stopPlayback() }
                prepare()
                start()
            }.onFailure { onComplete() }
        }
    }

    fun stopPlayback() {
        player?.runCatching { stop(); release() }
        player = null
    }
}
