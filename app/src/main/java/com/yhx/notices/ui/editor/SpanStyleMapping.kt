package com.yhx.notices.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import com.yhx.notices.domain.richtext.InlineStyles
import com.yhx.notices.domain.richtext.Span

/** 把 token 化样式集合转成 Compose [SpanStyle]。 */
fun styleTokensToSpanStyle(tokens: Set<String>): SpanStyle {
    var weight: FontWeight? = null
    var italic: FontStyle? = null
    val decorations = mutableListOf<TextDecoration>()
    var color = Color.Unspecified
    var background = Color.Unspecified
    var fontScale: Float? = null

    for (t in tokens) {
        when {
            t == InlineStyles.BOLD -> weight = FontWeight.Bold
            t == InlineStyles.ITALIC -> italic = FontStyle.Italic
            t == InlineStyles.UNDERLINE -> decorations.add(TextDecoration.Underline)
            t == InlineStyles.STRIKE -> decorations.add(TextDecoration.LineThrough)
            InlineStyles.isColor(t) -> InlineStyles.parseColor(t)?.let { color = Color(it) }
            InlineStyles.isHighlight(t) -> InlineStyles.parseHighlight(t)?.let { background = Color(it) }
            InlineStyles.isSize(t) -> fontScale = InlineStyles.parseSize(t)
        }
    }
    return SpanStyle(
        fontWeight = weight,
        fontStyle = italic,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        color = color,
        background = background,
        fontSize = fontScale?.let { it.em } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
    )
}

/** spans → AnnotatedString（编辑器与只读渲染共用）。 */
fun spansToAnnotated(spans: List<Span>): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
        if (span.styles.isEmpty()) {
            append(span.text)
        } else {
            pushStyle(styleTokensToSpanStyle(span.styles))
            append(span.text)
            pop()
        }
    }
}
