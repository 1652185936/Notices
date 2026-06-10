package com.yhx.notices.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yhx.notices.domain.richtext.InlineStyles
import com.yhx.notices.domain.richtext.TextKind

private val palette = listOf(
    0x182431, 0xFA2A2D, 0xFF7500, 0xFFBB00, 0x21A675, 0x007DFF, 0x4C2FBF, 0x8E8E93
)
private val highlights = listOf(0xFFF59D, 0xC8E6C9, 0xBBDEFB, 0xF8BBD0, 0xFFE0B2)

@Composable
fun FormatToolbar(vm: NoteEditorViewModel) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            ToggleIcon(Icons.Default.FormatBold, "加粗", vm.isStyleActive(InlineStyles.BOLD)) {
                vm.applyStyle(InlineStyles.BOLD)
            }
            ToggleIcon(Icons.Default.FormatItalic, "斜体", vm.isStyleActive(InlineStyles.ITALIC)) {
                vm.applyStyle(InlineStyles.ITALIC)
            }
            ToggleIcon(Icons.Default.FormatUnderlined, "下划线", vm.isStyleActive(InlineStyles.UNDERLINE)) {
                vm.applyStyle(InlineStyles.UNDERLINE)
            }
            ToggleIcon(Icons.Default.FormatStrikethrough, "删除线", vm.isStyleActive(InlineStyles.STRIKE)) {
                vm.applyStyle(InlineStyles.STRIKE)
            }
            ToggleIcon(Icons.Default.Title, "标题", false) { vm.changeKind(TextKind.H2) }
            ToggleIcon(Icons.Default.FormatListBulleted, "无序列表", false) { vm.changeKind(TextKind.BULLET) }
            ToggleIcon(Icons.Default.FormatListNumbered, "有序列表", false) { vm.changeKind(TextKind.NUMBERED) }
            ToggleIcon(Icons.Default.CheckCircle, "清单", false) { vm.toggleChecklistKind() }

            palette.forEach { rgb ->
                ColorDot(Color(0xFF000000 or rgb.toLong())) { vm.applyStyle(InlineStyles.color(rgb)) }
            }
            highlights.forEach { rgb ->
                ColorDot(Color(0xFF000000 or rgb.toLong()), ring = true) {
                    vm.applyStyle(InlineStyles.highlight(rgb))
                }
            }
        }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorDot(color: Color, ring: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .background(color, if (ring) RoundedCornerShape(4.dp) else CircleShape)
                .padding(10.dp)
        ) {}
    }
}

@Composable
fun InsertBar(
    onImage: () -> Unit,
    onChecklist: () -> Unit,
    onDivider: () -> Unit,
) {
    Surface {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            IconButton(onClick = onImage) {
                Icon(Icons.Default.Image, contentDescription = "插入图片")
            }
            IconButton(onClick = onChecklist) {
                Icon(Icons.Default.CheckCircle, contentDescription = "插入清单")
            }
            IconButton(onClick = onDivider) {
                Icon(Icons.Default.Remove, contentDescription = "分割线")
            }
        }
    }
}
