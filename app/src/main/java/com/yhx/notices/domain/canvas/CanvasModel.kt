package com.yhx.notices.domain.canvas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 无界笔记（无限画布）内容模型。所有元素以「世界坐标」存储，画布无边界，
 * 视口通过 offset+scale 平移缩放。对标华为笔记的"无界笔记2"。
 */
@Serializable
data class CanvasContent(
    val version: Int = 1,
    val background: String = "blank",   // 见 PaperStyles.ALL：blank/grid/lines/dots/cornell/legal/graph/tianzige/staff/cream-dots
    val elements: List<CanvasElement> = emptyList(),
)

@Serializable
sealed interface CanvasElement {
    val id: String
}

/** 一笔手写笔迹：points 为世界坐标点序列 [x0,y0,x1,y1,...]。 */
@Serializable
@SerialName("stroke")
data class StrokeElement(
    override val id: String = newId(),
    val tool: String,            // pen / pencil / highlighter / eraser
    val color: Int,              // ARGB
    val width: Float,
    val points: List<Float>,     // 扁平化的 x,y 序列，省空间
    val widths: List<Float> = emptyList(), // 每点笔宽（笔速/压感动态宽），空 = 恒定 width
) : CanvasElement

/** 画布上的文字框。 */
@Serializable
@SerialName("text")
data class TextElement(
    override val id: String = newId(),
    val x: Float,
    val y: Float,
    val text: String,
    val fontSize: Float = 18f,
    val color: Int = 0xFF182431.toInt(),
    val rotation: Float = 0f,    // 旋转角度（度），绕元素中心
) : CanvasElement

/** 画布上的图片。 */
@Serializable
@SerialName("image")
data class ImageElement(
    override val id: String = newId(),
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val attachmentId: Long,
    val rotation: Float = 0f,    // 旋转角度（度），绕元素中心
) : CanvasElement

fun newId(): String = java.util.UUID.randomUUID().toString()

object CanvasJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(content: CanvasContent): String = json.encodeToString(content)

    fun decode(raw: String): CanvasContent =
        if (raw.isBlank()) CanvasContent()
        else runCatching { json.decodeFromString<CanvasContent>(raw) }.getOrDefault(CanvasContent())
}
