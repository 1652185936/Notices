package com.yhx.notices.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

enum class SketchTool(val label: String) { PEN("钢笔"), PENCIL("铅笔"), HIGHLIGHTER("荧光笔"), ERASER("橡皮") }

class InkStroke(val tool: SketchTool, val color: Color, val width: Float) {
    val points = mutableStateListOf<Offset>()
}

private val penColors = listOf(
    Color(0xFF182431), Color(0xFFFA2A2D), Color(0xFFFF7500), Color(0xFF21A675),
    Color(0xFF007DFF), Color(0xFF4C2FBF), Color(0xFF8E8E93), Color(0xFFFFFFFF),
)
private val penWidths = listOf(3f, 6f, 12f, 20f)

/**
 * 全屏手写画板。钢笔/铅笔/荧光笔/橡皮 + 颜色 + 粗细 + 撤销/清空。
 * 完成时把笔迹渲染成位图回调出去（见 docs/01 N-06）。
 */
@Composable
fun SketchEditor(
    onCancel: () -> Unit,
    onDone: (android.graphics.Bitmap) -> Unit,
) {
    val strokes = remember { mutableStateListOf<InkStroke>() }
    var current by remember { mutableStateOf<InkStroke?>(null) }
    var tool by remember { mutableStateOf(SketchTool.PEN) }
    var color by remember { mutableStateOf(penColors[0]) }
    var width by remember { mutableStateOf(6f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(tool, color, width) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val s = InkStroke(tool, effectiveColor(tool, color), effectiveWidth(tool, width))
                                s.points.add(offset)
                                current = s
                            },
                            onDrag = { change, _ ->
                                current?.points?.add(change.position)
                                change.consume()
                            },
                            onDragEnd = { current?.let { strokes.add(it) }; current = null },
                            onDragCancel = { current = null },
                        )
                    }
            ) {
                strokes.forEach { drawInk(it) }
                current?.let { drawInk(it) }
            }

            // 顶栏：取消 / 撤销 / 清空 / 完成
            Surface(
                Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = Color(0xF2FFFFFF),
                tonalElevation = 2.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "取消") }
                    Box(Modifier.weight(1f))
                    IconButton(
                        onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                        enabled = strokes.isNotEmpty(),
                    ) { Icon(Icons.AutoMirrored.Filled.Undo, "撤销") }
                    IconButton(
                        onClick = { strokes.clear() },
                        enabled = strokes.isNotEmpty(),
                    ) { Icon(Icons.Default.DeleteOutline, "清空") }
                    IconButton(onClick = {
                        renderToBitmap(strokes, canvasSize)?.let(onDone) ?: onCancel()
                    }) {
                        Icon(Icons.Default.Check, "完成", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 底部工具条：笔型 + 颜色 + 粗细
            Surface(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color(0xF7FFFFFF),
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SketchTool.entries.forEach { t ->
                            FilterChip(
                                selected = tool == t,
                                onClick = { tool = t },
                                label = { Text(t.label) },
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        penColors.forEach { c ->
                            Box(
                                Modifier
                                    .padding(end = 8.dp)
                                    .size(if (c == color) 30.dp else 26.dp)
                                    .background(c, CircleShape)
                                    .border(
                                        width = if (c == color) 2.dp else 1.dp,
                                        color = if (c == color) MaterialTheme.colorScheme.primary
                                        else Color(0x22000000),
                                        shape = CircleShape,
                                    )
                                    .clickable { color = c }
                            )
                        }
                        Box(Modifier.weight(1f))
                        penWidths.forEach { w ->
                            Box(
                                Modifier
                                    .padding(start = 6.dp)
                                    .size(32.dp)
                                    .background(
                                        if (w == width) Color(0x22007DFF) else Color.Transparent,
                                        CircleShape,
                                    )
                                    .clickable { width = w },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size((w / 2f + 4f).dp)
                                        .background(Color(0xFF182431), CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun effectiveColor(tool: SketchTool, color: Color): Color = when (tool) {
    SketchTool.HIGHLIGHTER -> color.copy(alpha = 0.35f)
    SketchTool.ERASER -> Color.White
    else -> color
}

private fun effectiveWidth(tool: SketchTool, width: Float): Float = when (tool) {
    SketchTool.HIGHLIGHTER -> width * 3f
    SketchTool.ERASER -> width * 4f
    SketchTool.PENCIL -> width * 0.7f
    else -> width
}

private fun DrawScope.drawInk(s: InkStroke) {
    if (s.points.isEmpty()) return
    if (s.points.size == 1) {
        drawCircle(s.color, radius = s.width / 2f, center = s.points.first())
        return
    }
    val path = Path().apply {
        moveTo(s.points.first().x, s.points.first().y)
        for (i in 1 until s.points.size) lineTo(s.points[i].x, s.points[i].y)
    }
    drawPath(path, color = s.color, style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun renderToBitmap(strokes: List<InkStroke>, size: IntSize): android.graphics.Bitmap? {
    if (size.width <= 0 || size.height <= 0 || strokes.isEmpty()) return null
    val bmp = android.graphics.Bitmap.createBitmap(size.width, size.height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    strokes.forEach { s ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = s.color.toArgb()
            strokeWidth = s.width
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        if (s.points.size == 1) {
            val dot = android.graphics.Paint(paint).apply { style = android.graphics.Paint.Style.FILL }
            canvas.drawCircle(s.points.first().x, s.points.first().y, s.width / 2f, dot)
        } else {
            val path = android.graphics.Path().apply {
                moveTo(s.points.first().x, s.points.first().y)
                for (i in 1 until s.points.size) lineTo(s.points[i].x, s.points[i].y)
            }
            canvas.drawPath(path, paint)
        }
    }
    return bmp
}
