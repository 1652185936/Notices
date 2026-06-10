package com.yhx.notices.domain.richtext

import kotlinx.serialization.json.Json

/** NoteContent ↔ JSON 序列化。容错：解析失败返回 null，由上层降级处理。 */
object RichTextJson {

    val json = Json {
        ignoreUnknownKeys = true        // 向前兼容：旧版忽略新字段
        encodeDefaults = true
        classDiscriminator = "type"     // 与 docs/03 的 JSON 示例一致
    }

    fun encode(content: NoteContent): String = json.encodeToString(content)

    fun decode(raw: String): NoteContent? =
        if (raw.isBlank()) NoteContent.empty()
        else runCatching { json.decodeFromString<NoteContent>(raw) }.getOrNull()
}
