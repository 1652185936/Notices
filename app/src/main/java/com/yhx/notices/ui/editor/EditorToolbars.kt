package com.yhx.notices.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yhx.notices.domain.richtext.InlineStyles
import com.yhx.notices.domain.richtext.TextKind
import com.yhx.notices.ui.icons.HwIcons

private val palette = listOf(
    0x182431, 0xFA2A2D, 0xFF7500, 0xFFBB00, 0x21A675, 0x007DFF, 0x4C2FBF, 0x8E8E93
)
private val highlights = listOf(0xFFF59D, 0xC8E6C9, 0xBBDEFB, 0xF8BBD0, 0xFFE0B2)

/** 工具条墨色（随深浅色模式）。 */
@Composable
private fun barInk(): Color = if (isSystemInDarkTheme()) Color(0xFFE6E8EA) else Color(0xFF1B1D1F)

/** 选中底色（华为淡蓝）。 */
@Composable
private fun barSelBg(): Color = if (isSystemInDarkTheme()) Color(0xFF234A77) else Color(0xFFD6E6FF)

/** 工具条底色。 */
@Composable
private fun barBg(): Color = if (isSystemInDarkTheme()) Color(0xFF26282B) else Color(0xFFFCFCFE)

/** 格式工具条：字形按钮(B/I/U/S) + 标题/列表 + 颜色/高亮（华为样式）。 */
@Composable
fun FormatToolbar(vm: NoteEditorViewModel) {
    val ink = barInk()
    Column(Modifier.background(barBg())) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14000000)))
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphButton("B", TextStyle(fontWeight = FontWeight.Bold), vm.isStyleActive(InlineStyles.BOLD), ink) {
                vm.applyStyle(InlineStyles.BOLD)
            }
            GlyphButton("I", TextStyle(fontStyle = FontStyle.Italic), vm.isStyleActive(InlineStyles.ITALIC), ink) {
                vm.applyStyle(InlineStyles.ITALIC)
            }
            GlyphButton("U", TextStyle(textDecoration = TextDecoration.Underline), vm.isStyleActive(InlineStyles.UNDERLINE), ink) {
                vm.applyStyle(InlineStyles.UNDERLINE)
            }
            GlyphButton("S", TextStyle(textDecoration = TextDecoration.LineThrough), vm.isStyleActive(InlineStyles.STRIKE), ink) {
                vm.applyStyle(InlineStyles.STRIKE)
            }
            BarDivider()
            BarIcon(HwIcons.TitleSize, "标题", false, ink) { vm.changeKind(TextKind.H2) }
            BarIcon(HwIcons.ViewList, "无序列表", false, ink) { vm.changeKind(TextKind.BULLET) }
            BarIcon(HwIcons.NumberList, "有序列表", false, ink) { vm.changeKind(TextKind.NUMBERED) }
            BarIcon(HwIcons.TodoCheck, "清单", false, ink) { vm.toggleChecklistKind() }
            BarDivider()
            palette.forEach { rgb ->
                InkDot(Color(0xFF000000 or rgb.toLong())) { vm.applyStyle(InlineStyles.color(rgb)) }
            }
            BarDivider()
            highlights.forEach { rgb ->
                HighlightDot(Color(0xFF000000 or rgb.toLong())) { vm.applyStyle(InlineStyles.highlight(rgb)) }
            }
        }
    }
}

/** 插入工具条：图片/拍照/清单/手写/录音/语音/表格/分割线（华为样式）。 */
@Composable
fun InsertBar(
    onImage: () -> Unit,
    onCamera: () -> Unit,
    onChecklist: () -> Unit,
    onSketch: () -> Unit,
    onAudio: () -> Unit,
    onVoice: () -> Unit,
    onTable: () -> Unit,
    onDivider: () -> Unit,
) {
    val ink = barInk()
    Column(Modifier.background(barBg())) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14000000)))
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(HwIcons.Image, "插入图片", false, ink, onImage)
            BarIcon(HwIcons.Camera, "拍照", false, ink, onCamera)
            BarIcon(HwIcons.TodoCheck, "插入清单", false, ink, onChecklist)
            BarIcon(HwIcons.Sketch, "手写", false, ink, onSketch)
            BarIcon(HwIcons.Mic, "录音", false, ink, onAudio)
            BarIcon(HwIcons.Waveform, "语音转文字", false, ink, onVoice)
            BarIcon(HwIcons.Table, "表格", false, ink, onTable)
            BarIcon(HwIcons.DividerLine, "分割线", false, ink, onDivider)
        }
    }
}

/** 字形格式按钮（B/I/U/S 直接用排版字形呈现）。 */
@Composable
private fun GlyphButton(glyph: String, style: TextStyle, active: Boolean, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) barSelBg() else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = style.copy(fontSize = 17.sp, color = ink))
    }
}

/** 图标按钮（36dp 圆形点击域，选中淡蓝底）。 */
@Composable
private fun BarIcon(icon: ImageVector, desc: String, active: Boolean, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) barSelBg() else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = ink, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun BarDivider() {
    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(18.dp).background(Color(0x1F000000)))
}

/** 文字颜色点。 */
@Composable
private fun InkDot(color: Color, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(32.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.dp).background(color, CircleShape))
    }
}

/** 高亮色块（圆角方块示意荧光底色）。 */
@Composable
private fun HighlightDot(color: Color, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.dp).background(color, RoundedCornerShape(5.dp))) {
            Text("A", fontSize = 11.sp, color = Color(0xCC1B1D1F), modifier = Modifier.align(Alignment.Center))
        }
    }
}
