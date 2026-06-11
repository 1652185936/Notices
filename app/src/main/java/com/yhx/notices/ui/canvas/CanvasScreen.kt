package com.yhx.notices.ui.canvas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yhx.notices.domain.canvas.ImageElement
import com.yhx.notices.domain.canvas.InkGeometry
import com.yhx.notices.domain.canvas.StrokeElement
import com.yhx.notices.domain.canvas.TextElement
import com.yhx.notices.ui.icons.HwIcons
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

enum class CanvasTool(val label: String) {
    MOVE("移动"), SELECT("选择"), LASSO("套索"), FOUNTAIN("钢笔"), PEN("秀丽笔"), PENCIL("铅笔"),
    MARKER("马克笔"), HIGHLIGHTER("荧光笔"), SHAPE("一笔成形"), ERASER("橡皮"), TEXT("文字")
}

/** 橡皮模式：整笔擦除（碰到整条删）/ 局部擦除（只擦经过的一段，从中间断开）。 */
enum class EraserMode(val label: String) {
    PIXEL("局部擦除"), STROKE("整笔擦除")
}

/** 射线法判断点是否在多边形（扁平 x,y 序列）内。 */
private fun pointInPolygon(px: Float, py: Float, poly: List<Float>): Boolean {
    if (poly.size < 6) return false
    var inside = false
    val n = poly.size / 2
    var j = n - 1
    for (i in 0 until n) {
        val xi = poly[i * 2]; val yi = poly[i * 2 + 1]
        val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
        if (((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi + 1e-6f) + xi)) inside = !inside
        j = i
    }
    return inside
}

/** 套索选择：笔迹任一点在多边形内即选中；文字/图片按中心点。 */
private fun lassoSelect(elements: List<com.yhx.notices.domain.canvas.CanvasElement>, poly: List<Float>): Set<String> {
    val result = HashSet<String>()
    for (el in elements) {
        when (el) {
            is StrokeElement -> {
                var i = 0
                while (i + 1 < el.points.size) {
                    if (pointInPolygon(el.points[i], el.points[i + 1], poly)) { result.add(el.id); break }
                    i += 2
                }
            }
            is TextElement -> if (pointInPolygon(el.x + 20, el.y + el.fontSize / 2, poly)) result.add(el.id)
            is ImageElement -> if (pointInPolygon(el.x + el.width / 2, el.y + el.height / 2, poly)) result.add(el.id)
        }
    }
    return result
}

/** 选中集合的世界包围盒 [minX,minY,maxX,maxY]，空返回 null。 */
private fun selectionBounds(elements: List<com.yhx.notices.domain.canvas.CanvasElement>, ids: Set<String>): FloatArray? {
    if (ids.isEmpty()) return null
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    fun acc(x: Float, y: Float) { minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y) }
    elements.filter { it.id in ids }.forEach { el ->
        when (el) {
            is StrokeElement -> { var i = 0; while (i + 1 < el.points.size) { acc(el.points[i], el.points[i + 1]); i += 2 } }
            is TextElement -> { acc(el.x, el.y); acc(el.x + 40, el.y + el.fontSize) }
            is ImageElement -> { acc(el.x, el.y); acc(el.x + el.width, el.y + el.height) }
        }
    }
    return if (minX == Float.MAX_VALUE) null else floatArrayOf(minX, minY, maxX, maxY)
}

private val stickerEmojis = listOf(
    "😀", "😄", "😍", "🤔", "😎", "😭", "👍", "👏", "🙏", "💪",
    "❤️", "🔥", "⭐", "✨", "🎉", "✅", "❌", "❗", "❓", "💡",
    "📌", "📎", "🔖", "📝", "📅", "⏰", "🎯", "🚀", "🌟", "☀️",
    "🌈", "🍀", "🎵", "💯", "👀", "🤝", "🥳", "😴", "🤩", "🙌",
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StickerPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("贴纸") },
        text = {
            androidx.compose.foundation.layout.FlowRow {
                stickerEmojis.forEach { e ->
                    Text(
                        e,
                        fontSize = 28.sp,
                        modifier = Modifier.androidx_clickable { onPick(e) }.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun LassoMenu(count: Int, onCopy: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14000000)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
            androidx.compose.material3.TextButton(onClick = onCopy) { Text("复制") }
            Box(Modifier.width(1.dp).height(20.dp).background(Color(0x1F000000)))
            androidx.compose.material3.TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 任意元素的世界包围盒 [minX,minY,maxX,maxY]（含笔迹）。 */
private fun elementBox(el: com.yhx.notices.domain.canvas.CanvasElement): FloatArray? = when (el) {
    is StrokeElement -> {
        if (el.points.size < 2) null else {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            var i = 0
            while (i + 1 < el.points.size) {
                minX = minOf(minX, el.points[i]); maxX = maxOf(maxX, el.points[i])
                minY = minOf(minY, el.points[i + 1]); maxY = maxOf(maxY, el.points[i + 1]); i += 2
            }
            floatArrayOf(minX, minY, maxX, maxY)
        }
    }
    is TextElement -> floatArrayOf(el.x, el.y, el.x + 60, el.y + el.fontSize)
    is ImageElement -> floatArrayOf(el.x, el.y, el.x + el.width, el.y + el.height)
    else -> null
}

@Composable
private fun Minimap(
    elements: List<com.yhx.notices.domain.canvas.CanvasElement>,
    viewport: FloatArray,
    onJump: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var minX = viewport[0]; var minY = viewport[1]; var maxX = viewport[2]; var maxY = viewport[3]
    elements.forEach { el ->
        elementBox(el)?.let { b ->
            minX = minOf(minX, b[0]); minY = minOf(minY, b[1]); maxX = maxOf(maxX, b[2]); maxY = maxOf(maxY, b[3])
        }
    }
    val pad = (maxOf(maxX - minX, maxY - minY)) * 0.05f + 20f
    minX -= pad; minY -= pad; maxX += pad; maxY += pad
    val bw = (maxX - minX).coerceAtLeast(1f); val bh = (maxY - minY).coerceAtLeast(1f)

    androidx.compose.material3.Surface(
        modifier = modifier.size(110.dp, 150.dp),
        color = Color(0xF2FFFFFF),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33000000)),
    ) {
        Canvas(
            Modifier.fillMaxSize().padding(4.dp).pointerInput(bw, bh) {
                detectTapGestures { p ->
                    val s = minOf(size.width / bw, size.height / bh)
                    onJump(minX + p.x / s, minY + p.y / s)
                }
            }
        ) {
            val s = minOf(size.width / bw, size.height / bh)
            elements.forEach { el ->
                elementBox(el)?.let { b ->
                    drawRect(
                        Color(0x66007DFF),
                        topLeft = Offset((b[0] - minX) * s, (b[1] - minY) * s),
                        size = androidx.compose.ui.geometry.Size(((b[2] - b[0]) * s).coerceAtLeast(2f), ((b[3] - b[1]) * s).coerceAtLeast(2f)),
                    )
                }
            }
            drawRect(
                Color(0xFFFA2A2D),
                topLeft = Offset((viewport[0] - minX) * s, (viewport[1] - minY) * s),
                size = androidx.compose.ui.geometry.Size((viewport[2] - viewport[0]) * s, (viewport[3] - viewport[1]) * s),
                style = Stroke(2f),
            )
        }
    }
}

/** 返回元素包围盒 [x, y, w, h]，笔迹不可选返回 null。 */
private fun elementBounds(el: com.yhx.notices.domain.canvas.CanvasElement): FloatArray? = when (el) {
    is ImageElement -> floatArrayOf(el.x, el.y, el.width, el.height)
    is TextElement -> floatArrayOf(el.x, el.y, el.text.length.coerceAtLeast(2) * el.fontSize * 0.6f, el.fontSize * 1.4f)
    else -> null
}

/** 元素旋转角度（度），无旋转概念的返回 0。 */
private fun elementRotation(el: com.yhx.notices.domain.canvas.CanvasElement): Float = when (el) {
    is ImageElement -> el.rotation
    is TextElement -> el.rotation
    else -> 0f
}

/**
 * 元素未旋转 AABB 的四角绕中心按 rotation 旋转后，再经 world→screen 变换得到屏幕坐标。
 * 返回 [TL, TR, BR, BL]（左上、右上、右下、左下）。
 */
private fun elementScreenCorners(
    el: com.yhx.notices.domain.canvas.CanvasElement,
    scale: Float,
    offset: Offset,
): List<Offset>? {
    val b = elementBounds(el) ?: return null
    val x = b[0]; val y = b[1]; val w = b[2]; val h = b[3]
    val cx = x + w / 2f; val cy = y + h / 2f
    val rad = elementRotation(el) * PI.toFloat() / 180f
    val cosA = cos(rad); val sinA = sin(rad)
    val raw = listOf(
        Offset(x, y), Offset(x + w, y), Offset(x + w, y + h), Offset(x, y + h),
    )
    return raw.map { p ->
        val dx = p.x - cx; val dy = p.y - cy
        val rx = cx + dx * cosA - dy * sinA
        val ry = cy + dx * sinA + dy * cosA
        Offset(rx * scale + offset.x, ry * scale + offset.y)
    }
}

/**
 * 选中元素的变换浮层：选择框（旋转后斜框）+ 四角缩放手柄 + 顶部旋转手柄 + 右上删除「×」。
 * 全部用屏幕坐标 offset{} 定位、各自独立 pointerInput，覆盖在画布之上，优先吃触摸。
 */
@Composable
private fun SelectionOverlay(
    element: com.yhx.notices.domain.canvas.CanvasElement,
    scale: Float,
    offset: Offset,
    density: androidx.compose.ui.unit.Density,
    onBegin: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onMove: (Float, Float) -> Unit,
    onFontSize: (Float) -> Unit,
    onRotate: (Float) -> Unit,
    onDelete: () -> Unit,
) {
    val corners = elementScreenCorners(element, scale, offset) ?: return
    val tl = corners[0]; val tr = corners[1]; val br = corners[2]; val bl = corners[3]
    val centerScreen = Offset((tl.x + br.x) / 2f, (tl.y + br.y) / 2f)
    val rotation = elementRotation(element)
    val rad = rotation * PI.toFloat() / 180f
    val isImage = element is ImageElement

    // 拖动期间用最新值（避免重组后闭包读到过期 scale/offset/element）
    val stateScale by androidx.compose.runtime.rememberUpdatedState(scale)
    val stateOffset by androidx.compose.runtime.rememberUpdatedState(offset)
    val stateEl by androidx.compose.runtime.rememberUpdatedState(element)

    fun px(dp: Float): Float = with(density) { dp.dp.toPx() }
    val handleR = px(9f) // 手柄半径（屏幕像素）
    val handleSizeDp = with(density) { (handleR * 2).toDp() }

    val topMid = Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
    val rotHandlePos = Offset(topMid.x + sin(rad) * px(34f), topMid.y - cos(rad) * px(34f))

    // —— 选择框（四角连线）+ 旋转引线 ——
    Canvas(Modifier.fillMaxSize()) {
        val lineC = Color(0xFF007DFF)
        drawLine(lineC, tl, tr, strokeWidth = px(1.5f))
        drawLine(lineC, tr, br, strokeWidth = px(1.5f))
        drawLine(lineC, br, bl, strokeWidth = px(1.5f))
        drawLine(lineC, bl, tl, strokeWidth = px(1.5f))
        drawLine(lineC, topMid, rotHandlePos, strokeWidth = px(1.5f))
    }

    // —— 四角缩放手柄 ——
    // 拖某角时，对角作锚（世界坐标，drag 全程固定）。
    val cornerPositions = listOf(tl, tr, br, bl)
    val anchorCorners = listOf(br, bl, tl, tr) // TL↔BR, TR↔BL, BR↔TL, BL↔TR
    cornerPositions.forEachIndexed { idx, cpos ->
        val anchorScreen = anchorCorners[idx]
        Box(
            Modifier
                .offset { IntOffset((cpos.x - handleR).roundToInt(), (cpos.y - handleR).roundToInt()) }
                .size(handleSizeDp)
                .pointerInput(element.id) {
                    // 拖动起点：快照锚点世界坐标 + 起始指针屏幕位置
                    var anchorWorld = Offset.Zero
                    var startFontSize = 0f
                    var startBaseW = 1f
                    var startBaseH = 1f
                    var dragRad = 0f
                    var curPointer = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            onBegin()
                            val s = stateScale; val o = stateOffset
                            anchorWorld = Offset((anchorScreen.x - o.x) / s, (anchorScreen.y - o.y) / s)
                            curPointer = cpos
                            val b0 = elementBounds(stateEl)
                            startBaseW = (b0?.get(2) ?: 1f).coerceAtLeast(1f)
                            startBaseH = (b0?.get(3) ?: 1f).coerceAtLeast(1f)
                            startFontSize = (stateEl as? TextElement)?.fontSize ?: 0f
                            dragRad = elementRotation(stateEl) * PI.toFloat() / 180f
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            curPointer = Offset(curPointer.x + drag.x, curPointer.y + drag.y)
                            val s = stateScale; val o = stateOffset
                            val pWorld = Offset((curPointer.x - o.x) / s, (curPointer.y - o.y) / s)
                            val dx = pWorld.x - anchorWorld.x
                            val dy = pWorld.y - anchorWorld.y
                            // 去旋转投影到元素轴，绝对值即新对角尺寸
                            val cosA = cos(-dragRad); val sinA = sin(-dragRad)
                            val localW = abs(dx * cosA - dy * sinA)
                            val localH = abs(dx * sinA + dy * cosA)
                            if (isImage) {
                                val ratio = startBaseH / startBaseW
                                val newW = maxOf(localW, localH / ratio).coerceAtLeast(24f)
                                val newH = newW * ratio
                                // 锚点世界坐标固定：新中心 = 锚 + 朝指针方向的半对角
                                val dirLen = hypot(dx, dy).coerceAtLeast(1e-3f)
                                val halfDiag = hypot(newW, newH) / 2f
                                val cxN = anchorWorld.x + dx / dirLen * halfDiag
                                val cyN = anchorWorld.y + dy / dirLen * halfDiag
                                (stateEl as? ImageElement)?.let { img ->
                                    onResize(newW, newH)
                                    onMove((cxN - newW / 2f) - img.x, (cyN - newH / 2f) - img.y)
                                }
                            } else {
                                val factor = maxOf(localW / startBaseW, localH / startBaseH)
                                onFontSize((startFontSize * factor).coerceIn(8f, 200f))
                            }
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White, radius = handleR, center = center)
                drawCircle(Color(0xFF007DFF), radius = handleR, center = center, style = Stroke(px(1.5f)))
            }
        }
    }

    // —— 旋转手柄 ——
    Box(
        Modifier
            .offset { IntOffset((rotHandlePos.x - handleR).roundToInt(), (rotHandlePos.y - handleR).roundToInt()) }
            .size(handleSizeDp)
            .pointerInput(element.id) {
                var center0 = centerScreen
                var cur = rotHandlePos
                detectDragGestures(
                    onDragStart = {
                        onBegin()
                        cur = rotHandlePos
                        // 中心屏幕坐标（用最新 scale/offset 重算）
                        val s = stateScale; val o = stateOffset
                        val b0 = elementBounds(stateEl)
                        if (b0 != null) {
                            val ccx = b0[0] + b0[2] / 2f
                            val ccy = b0[1] + b0[3] / 2f
                            center0 = Offset(ccx * s + o.x, ccy * s + o.y)
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        cur = Offset(cur.x + drag.x, cur.y + drag.y)
                        val ang = atan2(cur.y - center0.y, cur.x - center0.x)
                        var deg = ang * 180f / PI.toFloat() + 90f // 手柄默认在正上方
                        deg = ((deg % 360f) + 360f) % 360f
                        onRotate(deg)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF007DFF), radius = handleR, center = center)
            drawCircle(Color.White, radius = handleR, center = center, style = Stroke(px(1.5f)))
        }
    }

    // —— 删除「×」手柄（右上角外侧）——
    val delPos = Offset(tr.x + px(2f), tr.y - px(2f))
    Box(
        Modifier
            .offset { IntOffset((delPos.x - handleR).roundToInt(), (delPos.y - handleR).roundToInt()) }
            .size(handleSizeDp)
            .clip(CircleShape)
            .background(Color(0xFFFA2A2D))
            .androidx_clickable { onDelete() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(HwIcons.Close, "删除", tint = Color.White, modifier = Modifier.size(handleSizeDp * 0.7f))
    }
}

/**
 * 文字样式条（图5）：字号 -/+ 步进 + 一行色点。圆角白卡 + 阴影，与现有浮层风格一致。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TextStyleBar(
    element: TextElement,
    isSticker: Boolean,
    onFontSize: (Float) -> Unit,
    onColor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = if (isSystemInDarkTheme()) Color(0xFF2A2C2E) else Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 14.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14000000)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // 字号步进
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("字号", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                StepBtn("－") { onFontSize((element.fontSize - 4f).coerceIn(8f, 200f)) }
                Text(
                    "${element.fontSize.roundToInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepBtn("＋") { onFontSize((element.fontSize + 4f).coerceIn(8f, 200f)) }
            }
            if (!isSticker) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    palette.forEach { col ->
                        val selected = col.toArgb() == element.color
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(if (selected) 26.dp else 22.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color(0xFF007DFF) else Color(0x22000000),
                                    shape = CircleShape,
                                )
                                .androidx_clickable { onColor(col.toArgb()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (isSystemInDarkTheme()) Color(0xFF35373B) else Color(0xFFF1F3F5))
            .androidx_clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * 一笔成形：把手绘笔迹识别为直线/矩形/椭圆/三角形/箭头，返回规整后的扁平点 [x0,y0,...]。
 * 识别已大幅收紧——宁可不识别（返回 null = 保留原始手绘）也不误伤有意的曲线/字母数字。
 */
private fun recognizeShape(pts: List<Float>): List<Float>? {
    if (pts.size < 10) return null
    val n = pts.size / 2
    val xs = FloatArray(n) { pts[it * 2] }
    val ys = FloatArray(n) { pts[it * 2 + 1] }
    val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
    val w = maxX - minX; val h = maxY - minY
    val span = maxOf(w, h)
    if (span < 48f) return null // span 不够大不规整，避免误伤小字
    val x0 = xs[0]; val y0 = ys[0]; val x1 = xs[n - 1]; val y1 = ys[n - 1]
    val closeGap = hypot(x1 - x0, y1 - y0)

    // —— 直线：严格直 + 弦够长。10% 太松会拉直有意曲线，收到 3.5%。
    var maxPerp = 0f
    for (i in 0 until n) maxPerp = maxOf(maxPerp, perpDist(xs[i], ys[i], x0, y0, x1, y1))
    val chord = closeGap
    if (chord > span * 0.6f && maxPerp < span * 0.035f) return lineSamples(x0, y0, x1, y1)

    // —— 箭头：近直线主干 + 末端有明显折返/勾（未闭合）。
    recognizeArrow(xs, ys, span)?.let { return it }

    // —— 闭合图形（矩形/椭圆/三角）：首尾必须接近闭合。
    if (closeGap > span * 0.22f) return null
    val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
    val rx = w / 2; val ry = h / 2
    var ellRes = 0f; var rectRes = 0f
    for (i in 0 until n) {
        val nx = (xs[i] - cx) / (rx + 1e-3f); val ny = (ys[i] - cy) / (ry + 1e-3f)
        ellRes += abs(hypot(nx, ny) - 1f)
        rectRes += distToRectEdge(xs[i], ys[i], minX, minY, maxX, maxY) / span
    }
    ellRes /= n; rectRes /= n

    // —— 三角形：三个主导转角，闭合多边形拟合。残差更优才取。
    val tri = recognizeTriangle(xs, ys, span)
    if (tri != null) {
        val triRes = tri.second
        if (triRes < ellRes && triRes < rectRes && triRes < 0.16f) return tri.first
    }

    // 椭圆 / 矩形取更优者，且必须足够小才接受，否则保留原笔迹。
    val best = minOf(ellRes, rectRes)
    if (best >= 0.16f) return null
    return if (ellRes < rectRes) ellipseSamples(cx, cy, rx, ry) else rectSamples(minX, minY, maxX, maxY)
}

/**
 * 三角形识别：在闭合笔迹上找 3 个主导转角（离首末点连线方向偏折最大的点），
 * 以包围盒尺度归一化的边残差衡量拟合度。返回 (规整点, 残差)，不像三角形返回 null。
 */
private fun recognizeTriangle(xs: FloatArray, ys: FloatArray, span: Float): Pair<List<Float>, Float>? {
    val n = xs.size
    if (n < 6) return null
    // 起点固定为顶点 A，再找离 A 最远的点 B，再找离直线 AB 最远的点 C。
    var bIdx = 0; var bDist = -1f
    for (i in 1 until n) {
        val d = hypot(xs[i] - xs[0], ys[i] - ys[0])
        if (d > bDist) { bDist = d; bIdx = i }
    }
    var cIdx = 0; var cDist = -1f
    for (i in 0 until n) {
        val d = perpDist(xs[i], ys[i], xs[0], ys[0], xs[bIdx], ys[bIdx])
        if (d > cDist) { cDist = d; cIdx = i }
    }
    if (cDist < span * 0.2f) return null // 太扁，不是三角
    val ax = xs[0]; val ay = ys[0]
    val bx = xs[bIdx]; val by = ys[bIdx]
    val cx = xs[cIdx]; val cy = ys[cIdx]
    // 三顶点必须分得开（避免退化）。
    if (hypot(bx - cx, by - cy) < span * 0.2f || hypot(ax - cx, ay - cy) < span * 0.2f) return null
    // 残差：每个采样点到三角形三条边的最近距离，按 span 归一化。
    var res = 0f
    for (i in 0 until n) {
        val d = minOf(
            perpDistSeg(xs[i], ys[i], ax, ay, bx, by),
            perpDistSeg(xs[i], ys[i], bx, by, cx, cy),
            perpDistSeg(xs[i], ys[i], cx, cy, ax, ay),
        )
        res += d / span
    }
    res /= n
    return triangleSamples(ax, ay, bx, by, cx, cy) to res
}

/**
 * 箭头识别：笔迹主体近直线（主干），但末端一小段明显折返（勾）。
 * 找尾部偏离主干方向最大的折点，若折角足够大则识别为箭头。
 */
private fun recognizeArrow(xs: FloatArray, ys: FloatArray, span: Float): List<Float>? {
    val n = xs.size
    if (n < 12) return null
    // 取前 70% 作为主干，要求其近直线。
    val trunkEnd = (n * 0.7f).toInt().coerceIn(2, n - 1)
    val sx = xs[0]; val sy = ys[0]
    val tx = xs[trunkEnd]; val ty = ys[trunkEnd]
    val trunkLen = hypot(tx - sx, ty - sy)
    if (trunkLen < span * 0.6f) return null
    var trunkPerp = 0f
    for (i in 0..trunkEnd) trunkPerp = maxOf(trunkPerp, perpDist(xs[i], ys[i], sx, sy, tx, ty))
    if (trunkPerp > span * 0.08f) return null // 主干不够直
    // 末端 30% 必须明显偏离主干方向（勾）。计算尾段相对主干的最大反向偏离。
    val dirx = (tx - sx) / (trunkLen + 1e-3f)
    val diry = (ty - sy) / (trunkLen + 1e-3f)
    var tailPerp = 0f
    for (i in trunkEnd until n) tailPerp = maxOf(tailPerp, perpDist(xs[i], ys[i], sx, sy, tx, ty))
    if (tailPerp < span * 0.12f) return null // 末端没有明显勾，是普通直线（交给直线分支）
    // 箭头尖端取主干末端 t 点，箭翼按主干方向回折 ±28°，长度约主干 22%。
    val headLen = (trunkLen * 0.22f).coerceAtLeast(span * 0.12f)
    val baseAngle = atan2(diry, dirx)
    val wing = (28f * PI.toFloat() / 180f)
    val a1 = baseAngle + PI.toFloat() - wing
    val a2 = baseAngle + PI.toFloat() + wing
    val w1x = tx + headLen * cos(a1); val w1y = ty + headLen * sin(a1)
    val w2x = tx + headLen * cos(a2); val w2y = ty + headLen * sin(a2)
    return arrowSamples(sx, sy, tx, ty, w1x, w1y, w2x, w2y)
}

private fun perpDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay; val len = hypot(dx, dy)
    return if (len < 1e-3f) hypot(px - ax, py - ay) else abs((px - ax) * dy - (py - ay) * dx) / len
}

/** 点到线段（非整条直线）的最近距离。 */
private fun perpDistSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val len2 = dx * dx + dy * dy
    if (len2 < 1e-6f) return hypot(px - ax, py - ay)
    var t = ((px - ax) * dx + (py - ay) * dy) / len2
    if (t < 0f) t = 0f else if (t > 1f) t = 1f
    val qx = ax + t * dx; val qy = ay + t * dy
    return hypot(px - qx, py - qy)
}

private fun distToRectEdge(px: Float, py: Float, minX: Float, minY: Float, maxX: Float, maxY: Float): Float =
    minOf(abs(px - minX), abs(px - maxX), abs(py - minY), abs(py - maxY))

private fun lineSamples(x0: Float, y0: Float, x1: Float, y1: Float): List<Float> {
    val out = ArrayList<Float>(); val steps = 16
    for (i in 0..steps) { val t = i / steps.toFloat(); out.add(x0 + (x1 - x0) * t); out.add(y0 + (y1 - y0) * t) }
    return out
}

/** 三角形采样：A→B→C→A 三条边各均匀取点。 */
private fun triangleSamples(
    ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float,
): List<Float> {
    val verts = listOf(ax to ay, bx to by, cx to cy, ax to ay)
    val out = ArrayList<Float>(); val steps = 12
    for (k in 0 until verts.size - 1) {
        val (p0x, p0y) = verts[k]; val (p1x, p1y) = verts[k + 1]
        for (i in 0..steps) { val t = i / steps.toFloat(); out.add(p0x + (p1x - p0x) * t); out.add(p0y + (p1y - p0y) * t) }
    }
    return out
}

/** 箭头采样：主干 s→t，再画两条箭翼 t→w1、回到 t、t→w2。 */
private fun arrowSamples(
    sx: Float, sy: Float, tx: Float, ty: Float,
    w1x: Float, w1y: Float, w2x: Float, w2y: Float,
): List<Float> {
    val out = ArrayList<Float>()
    val steps = 16
    for (i in 0..steps) { val t = i / steps.toFloat(); out.add(sx + (tx - sx) * t); out.add(sy + (ty - sy) * t) }
    val wing = 6
    for (i in 0..wing) { val t = i / wing.toFloat(); out.add(tx + (w1x - tx) * t); out.add(ty + (w1y - ty) * t) }
    for (i in 0..wing) { val t = i / wing.toFloat(); out.add(w1x + (tx - w1x) * t); out.add(w1y + (ty - w1y) * t) }
    for (i in 0..wing) { val t = i / wing.toFloat(); out.add(tx + (w2x - tx) * t); out.add(ty + (w2y - ty) * t) }
    return out
}

private fun ellipseSamples(cx: Float, cy: Float, rx: Float, ry: Float): List<Float> {
    val out = ArrayList<Float>(); val steps = 48
    for (i in 0..steps) { val a = 2f * PI.toFloat() * i / steps; out.add(cx + rx * cos(a)); out.add(cy + ry * sin(a)) }
    return out
}

private fun rectSamples(minX: Float, minY: Float, maxX: Float, maxY: Float): List<Float> {
    val corners = listOf(minX to minY, maxX to minY, maxX to maxY, minX to maxY, minX to minY)
    val out = ArrayList<Float>(); val steps = 8
    for (k in 0 until corners.size - 1) {
        val (ax, ay) = corners[k]; val (bx, by) = corners[k + 1]
        for (i in 0..steps) { val t = i / steps.toFloat(); out.add(ax + (bx - ax) * t); out.add(ay + (by - ay) * t) }
    }
    return out
}

private fun hitTest(elements: List<com.yhx.notices.domain.canvas.CanvasElement>, w: Offset): String? {
    for (el in elements.asReversed()) {
        when (el) {
            is ImageElement ->
                if (w.x in el.x..(el.x + el.width) && w.y in el.y..(el.y + el.height)) return el.id
            is TextElement -> {
                val tw = el.text.length.coerceAtLeast(2) * el.fontSize * 0.6f
                if (w.x in el.x..(el.x + tw) && w.y in el.y..(el.y + el.fontSize * 1.4f)) return el.id
            }
            else -> {}
        }
    }
    return null
}

private val palette = listOf(
    Color(0xFF182431), Color(0xFFFA2A2D), Color(0xFFFF7500), Color(0xFF21A675),
    Color(0xFF007DFF), Color(0xFF4C2FBF), Color(0xFF8E8E93),
)
private val penTools = setOf(
    CanvasTool.FOUNTAIN, CanvasTool.PEN, CanvasTool.PENCIL, CanvasTool.MARKER, CanvasTool.HIGHLIGHTER,
)

/** 取色网格调色板（华为为 100+，此处精选 36 色）。 */
private val gridColors = listOf(
    0x182431, 0x4E5969, 0x86909C, 0xC9CDD4, 0xE5E6EB, 0xFFFFFF,
    0xFA2A2D, 0xFF5252, 0xFF7875, 0xFF9A2C, 0xFFA940, 0xFFD666,
    0xFFBB00, 0xFADB14, 0xD3F261, 0x95DE64, 0x52C41A, 0x21A675,
    0x13C2C2, 0x36CFC9, 0x5CDBD3, 0x40A9FF, 0x007DFF, 0x1D39C4,
    0x4C2FBF, 0x722ED1, 0x9254DE, 0xB37FEB, 0xEB2F96, 0xFF85C0,
    0x8B4513, 0xA0522D, 0xC68642, 0xD2B48C, 0x5C3A21, 0x000000,
).map { Color(0xFF000000 or it.toLong()) }

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    onBack: () -> Unit,
    viewModel: CanvasViewModel = hiltViewModel(),
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var tool by remember { mutableStateOf(CanvasTool.PEN) }
    var color by remember { mutableStateOf(palette[0]) }
    var width by remember { mutableStateOf(6f) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val lassoPoints = remember { mutableStateListOf<Offset>() }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lassoMoving by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showStickers by remember { mutableStateOf(false) }
    var showBrushPanel by remember { mutableStateOf(false) }
    var showEraserPanel by remember { mutableStateOf(false) }
    var palmBlock by remember { mutableStateOf(false) }
    var minimapOn by remember { mutableStateOf(true) }
    var opacity by remember { mutableStateOf(1f) }
    // 橡皮：默认局部（像素）擦除；橡皮大小（屏幕像素直径）
    var eraserMode by remember { mutableStateOf(EraserMode.PIXEL) }
    var eraserSize by remember { mutableStateOf(24f) }

    // 选笔：换笔即切换并收起设置；再点已选中的笔/橡皮 = 开关对应设置浮层
    val selectTool: (CanvasTool) -> Unit = { t ->
        when {
            t == CanvasTool.ERASER && tool == CanvasTool.ERASER -> showEraserPanel = !showEraserPanel
            t == tool && t in penTools -> showBrushPanel = !showBrushPanel
            else -> { tool = t; showBrushPanel = false; showEraserPanel = false }
        }
    }

    val livePoints = remember { mutableStateListOf<Offset>() }
    val liveWidths = remember { mutableStateListOf<Float>() }
    var eraseCursor by remember { mutableStateOf<Offset?>(null) }
    var pendingErase by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 一笔成形：仅当书写末端停顿后才规整。shapePreview 非空 = 已达成停顿且识别成功的规整点（世界坐标扁平 [x,y,...]）
    var shapePreview by remember { mutableStateOf<List<Float>?>(null) }
    // 分层渲染：已落墨内容烘焙为位图（书写时每帧只画位图+当前一笔）
    var inkLayer by remember { mutableStateOf<Pair<ImageBitmap, BakeStamp>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 已落墨笔迹的轮廓 Path 缓存（key 含首末点，套索平移后自动失效重建；铅笔为双层）
    val inkCache = remember { HashMap<String, List<Path>>() }
    val bitmaps = remember { mutableStateMapOf<Long, ImageBitmap?>() }
    val density = LocalDensity.current

    fun screenToWorld(p: Offset) = Offset((p.x - offset.x) / scale, (p.y - offset.y) / scale)

    // 加载图片元素位图
    androidx.compose.runtime.LaunchedEffect(viewModel.elements.size) {
        viewModel.elements.filterIsInstance<ImageElement>().forEach { img ->
            if (!bitmaps.containsKey(img.attachmentId)) {
                bitmaps[img.attachmentId] = null
                val path = viewModel.attachmentPath(img.attachmentId)
                if (path != null) {
                    val bmp = android.graphics.BitmapFactory.decodeFile(path)
                    bitmaps[img.attachmentId] = bmp?.asImageBitmap()
                }
            }
        }
    }

    // —— 烘焙基底层：内容/变换稳定 60ms 后重烘，期间矢量兜底 ——
    val bakeStamp = BakeStamp(
        revision = viewModel.revision,
        offsetX = offset.x, offsetY = offset.y, scale = scale,
        width = canvasSize.width, height = canvasSize.height,
        background = viewModel.background,
        editingId = editingId,
        loadedImages = bitmaps.count { it.value != null },
        fading = pendingErase.size,
    )
    androidx.compose.runtime.LaunchedEffect(bakeStamp) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        kotlinx.coroutines.delay(60)
        val bmp = ImageBitmap(canvasSize.width, canvasSize.height)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            density,
            androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(bmp),
            androidx.compose.ui.geometry.Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
        ) {
            drawRect(Color.White)
            drawCommittedWorld(
                viewModel.elements.toList(), bitmaps, viewModel.background,
                offset, scale, editingId, inkCache, pendingErase,
            )
        }
        inkLayer = bmp to bakeStamp
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            // 插入到当前视口中心对应的世界坐标，级联偏移排列
            val world = screenToWorld(Offset(canvasSize.width / 2f, canvasSize.height / 2f))
            viewModel.insertImages(uris, world.x, world.y)
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val tl = screenToWorld(Offset(canvasSize.width / 2f, 120f))
            viewModel.importPdf(it, tl.x, tl.y)
        }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            HwCanvasTopBar(
                title = viewModel.title,
                onTitleChange = viewModel::onTitleChange,
                onBack = { viewModel.onExit(); onBack() },
                tool = tool,
                onTool = selectTool,
                penColor = color,
                canUndo = viewModel.canUndo,
                onUndo = viewModel::undo,
                canRedo = viewModel.canRedo,
                onRedo = viewModel::redo,
                palmBlock = palmBlock,
                onTogglePalm = { palmBlock = !palmBlock },
                background = viewModel.background,
                onBackground = viewModel::changeBackground,
                minimapOn = minimapOn,
                onToggleMinimap = { minimapOn = !minimapOn },
                onInsertImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onInsertPdf = {
                    pdfPicker.launch(arrayOf("application/pdf"))
                },
                onInsertSticker = { showStickers = true },
                onInsertSpace = {
                    val cy = screenToWorld(Offset(canvasSize.width / 2f, canvasSize.height / 2f)).y
                    viewModel.insertVerticalSpace(cy, 400f)
                },
                onShareImage = {
                    viewModel.shareAsImage { uri ->
                        uri?.let { com.yhx.notices.ui.editor.shareUri(ctx, it, "image/png") }
                    }
                },
                onExportGallery = {
                    viewModel.exportImageToGallery { ok ->
                        android.widget.Toast.makeText(
                            ctx, if (ok) "已保存到相册" else "导出失败",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onExportPdf = {
                    viewModel.exportPdf { uri ->
                        uri?.let { com.yhx.notices.ui.editor.shareUri(ctx, it, "application/pdf") }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
                .onSizeChanged { canvasSize = it }
                .pointerInput(tool, palmBlock) {
                    when (tool) {
                        CanvasTool.MOVE -> detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(0.1f, 10f)
                            val worldUnder = Offset(
                                (centroid.x - offset.x) / scale,
                                (centroid.y - offset.y) / scale,
                            )
                            scale = newScale
                            offset = Offset(
                                centroid.x - worldUnder.x * newScale + pan.x,
                                centroid.y - worldUnder.y * newScale + pan.y,
                            )
                        }
                        CanvasTool.TEXT -> detectTapGestures { p ->
                            val w = screenToWorld(p)
                            val hit = hitTest(viewModel.elements, w)
                            val hitText = hit?.let { id -> viewModel.elements.firstOrNull { it.id == id } as? TextElement }
                            editingId = hitText?.id ?: viewModel.addText(w.x, w.y)
                        }
                        CanvasTool.LASSO -> detectDragGestures(
                            onDragStart = { p ->
                                val w = screenToWorld(p)
                                val box = selectionBounds(viewModel.elements, selectedIds)
                                if (box != null && w.x in box[0]..box[2] && w.y in box[1]..box[3]) {
                                    lassoMoving = true
                                } else {
                                    lassoMoving = false
                                    selectedIds = emptySet()
                                    lassoPoints.clear(); lassoPoints.add(w)
                                }
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                if (lassoMoving) viewModel.moveElementsBy(selectedIds, drag.x / scale, drag.y / scale)
                                else lassoPoints.add(screenToWorld(change.position))
                            },
                            onDragEnd = {
                                if (!lassoMoving) {
                                    val poly = ArrayList<Float>(lassoPoints.size * 2)
                                    lassoPoints.forEach { poly.add(it.x); poly.add(it.y) }
                                    selectedIds = lassoSelect(viewModel.elements, poly)
                                    lassoPoints.clear()
                                }
                                lassoMoving = false
                            },
                        )
                        CanvasTool.SELECT -> detectDragGestures(
                            onDragStart = { p ->
                                draggingId = hitTest(viewModel.elements, screenToWorld(p))
                                selectedId = draggingId
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                val id = draggingId
                                if (id != null) viewModel.moveElement(id, drag.x / scale, drag.y / scale)
                                else offset += drag
                            },
                            onDragEnd = { draggingId = null },
                        )
                        else -> awaitEachGesture {
                            val down = awaitFirstDown()
                            val fingerPansOnly = palmBlock && down.type != PointerType.Stylus
                            val baseW = strokeWidth(tool, width)
                            var lastPos = down.position
                            var lastTime = down.uptimeMillis
                            var penW = baseW * 0.65f // 起笔渐入
                            var multiTouch = false
                            // 一笔成形「停顿确认」：跟踪最近一次明显移动的时间；末端原地停顿 ≥350ms 才规整
                            var lastMoveTime = down.uptimeMillis
                            var dwellAnchor = down.position
                            var dwellReady = false
                            if (tool == CanvasTool.SHAPE) shapePreview = null
                            if (!fingerPansOnly) {
                                livePoints.clear(); liveWidths.clear()
                                livePoints.add(screenToWorld(down.position)); liveWidths.add(penW)
                                if (tool == CanvasTool.ERASER) eraseCursor = down.position
                            }
                            down.consume()
                            // 单指书写事件环：第二指落下立即转双指缩放平移
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    multiTouch = true
                                    livePoints.clear(); liveWidths.clear()
                                    shapePreview = null
                                    eraseCursor = null; pendingErase = emptySet()
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val zoom = ev.calculateZoom()
                                        val pan = ev.calculatePan()
                                        val centroid = ev.calculateCentroid()
                                        if (centroid.isSpecified && (zoom != 1f || pan != Offset.Zero)) {
                                            val newScale = (scale * zoom).coerceIn(0.1f, 10f)
                                            val worldUnder = Offset(
                                                (centroid.x - offset.x) / scale,
                                                (centroid.y - offset.y) / scale,
                                            )
                                            scale = newScale
                                            offset = Offset(
                                                centroid.x - worldUnder.x * newScale + pan.x,
                                                centroid.y - worldUnder.y * newScale + pan.y,
                                            )
                                        }
                                        ev.changes.forEach { it.consume() }
                                        if (ev.changes.none { it.pressed }) break
                                    }
                                    break
                                }
                                val ch = pressed.firstOrNull() ?: break
                                if (fingerPansOnly) {
                                    offset += ch.positionChange()
                                } else {
                                    val dist = (ch.position - lastPos).getDistance()
                                    val dt = (ch.uptimeMillis - lastTime).coerceAtLeast(1L)
                                    val target = baseW * widthFactor(tool, dist / dt, ch.pressure, ch.type)
                                    penW += (target - penW) * 0.35f // 指数平滑防突变
                                    lastPos = ch.position; lastTime = ch.uptimeMillis
                                    val w = screenToWorld(ch.position)
                                    livePoints.add(w); liveWidths.add(penW)
                                    if (tool == CanvasTool.SHAPE) {
                                        // 末端原地停顿检测：位移超阈值则重置停顿锚点，否则累计停留时长
                                        if ((ch.position - dwellAnchor).getDistance() > 6f) {
                                            dwellAnchor = ch.position
                                            lastMoveTime = ch.uptimeMillis
                                            if (dwellReady) { dwellReady = false; shapePreview = null }
                                        } else if (!dwellReady && ch.uptimeMillis - lastMoveTime >= 350L) {
                                            // 停顿达成：尝试规整，成功则切换实时预览为规整形状
                                            val snap = ArrayList<Float>(livePoints.size * 2)
                                            livePoints.forEach { snap.add(it.x); snap.add(it.y) }
                                            val shaped = recognizeShape(snap)
                                            if (shaped != null) { shapePreview = shaped; dwellReady = true }
                                            else lastMoveTime = ch.uptimeMillis // 识别失败，重置等下一次停顿
                                        }
                                    }
                                    if (tool == CanvasTool.ERASER) {
                                        eraseCursor = ch.position
                                        val r = (eraserSize / 2f) / scale
                                        if (eraserMode == EraserMode.STROKE) {
                                            // 整笔模式：实时命中灰显，先标记，抬手才删
                                            val hits = viewModel.elements.filterIsInstance<StrokeElement>()
                                                .filter { s -> s.id !in pendingErase && strokeHit(s, w.x, w.y, r) }
                                            if (hits.isNotEmpty()) pendingErase = pendingErase + hits.map { it.id }
                                        }
                                        // 局部模式：不灰显整条，仅靠 livePoints 累积橡皮路径，抬手再切割
                                    }
                                }
                                ch.consume()
                                if (event.changes.none { it.pressed }) break
                            }
                            eraseCursor = null
                            if (!multiTouch && !fingerPansOnly && livePoints.size >= 1) {
                                val flat = ArrayList<Float>(livePoints.size * 2)
                                livePoints.forEach { flat.add(it.x); flat.add(it.y) }
                                when (tool) {
                                    CanvasTool.ERASER -> {
                                        if (eraserMode == EraserMode.STROKE) {
                                            if (pendingErase.isNotEmpty()) viewModel.deleteElements(pendingErase)
                                        } else {
                                            // 局部擦除：用橡皮路径把笔迹从中间断开成多段
                                            val r = (eraserSize / 2f) / scale
                                            viewModel.erasePixels(flat, r)
                                        }
                                        pendingErase = emptySet()
                                    }
                                    CanvasTool.SHAPE -> viewModel.addStroke(
                                        // 仅当末端停顿达成（dwellReady 且识别成功）才落规整形状；否则保留原始手绘
                                        StrokeElement(
                                            tool = "pen",
                                            color = color.copy(alpha = opacity).toArgb(),
                                            width = width,
                                            points = (if (dwellReady) shapePreview else null) ?: flat,
                                        )
                                    )
                                    else -> {
                                        // 马克笔/荧光笔为恒宽方杆笔，不存逐点宽；书写类笔收笔出锋
                                        val constantWidth = tool == CanvasTool.HIGHLIGHTER || tool == CanvasTool.MARKER
                                        val ws = ArrayList(liveWidths)
                                        if (!constantWidth) InkGeometry.taperTail(ws)
                                        viewModel.addStroke(
                                            StrokeElement(
                                                tool = tool.name.lowercase(),
                                                color = strokeColor(tool, color, opacity).toArgb(),
                                                width = baseW,
                                                points = flat,
                                                widths = if (constantWidth) emptyList() else ws,
                                            )
                                        )
                                    }
                                }
                            }
                            livePoints.clear(); liveWidths.clear()
                            shapePreview = null
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // 基底：位图命中 → 一次 drawImage；未命中（平移/缩放/刚改动）→ 矢量兜底
                val layer = inkLayer
                if (layer != null && layer.second == bakeStamp) {
                    drawImage(layer.first)
                } else {
                    drawCommittedWorld(
                        viewModel.elements, bitmaps, viewModel.background,
                        offset, scale, editingId, inkCache, pendingErase,
                    )
                }
                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    // 选中元素高亮框已移到 SelectionOverlay（支持旋转后斜框 + 手柄）
                    // 套索路径与多选框
                    if (lassoPoints.size >= 2) {
                        val lp = Path().apply {
                            moveTo(lassoPoints.first().x, lassoPoints.first().y)
                            for (i in 1 until lassoPoints.size) lineTo(lassoPoints[i].x, lassoPoints[i].y)
                        }
                        drawPath(
                            lp, color = Color(0xFF007DFF),
                            style = Stroke(
                                2f / scale,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                            ),
                        )
                    }
                    selectionBounds(viewModel.elements, selectedIds)?.let { b ->
                        drawRect(
                            color = Color(0x22007DFF),
                            topLeft = Offset(b[0], b[1]),
                            size = androidx.compose.ui.geometry.Size(b[2] - b[0], b[3] - b[1]),
                        )
                        drawRect(
                            color = Color(0xFF007DFF),
                            topLeft = Offset(b[0], b[1]),
                            size = androidx.compose.ui.geometry.Size(b[2] - b[0], b[3] - b[1]),
                            style = Stroke(2f / scale),
                        )
                    }
                    // 实时预览笔迹（与落墨共用墨迹引擎，所见即所得）
                    if (livePoints.size >= 2) {
                        when (tool) {
                            CanvasTool.ERASER -> Unit // 橡皮用屏幕光标提示，不画白线
                            CanvasTool.SHAPE -> {
                                // 停顿达成后预览切换为规整形状（shapePreview），否则画原始手绘
                                val preview = shapePreview
                                val path = Path().apply {
                                    if (preview != null && preview.size >= 4) {
                                        moveTo(preview[0], preview[1])
                                        var i = 2
                                        while (i + 1 < preview.size) { lineTo(preview[i], preview[i + 1]); i += 2 }
                                    } else {
                                        moveTo(livePoints.first().x, livePoints.first().y)
                                        for (i in 1 until livePoints.size) lineTo(livePoints[i].x, livePoints[i].y)
                                    }
                                }
                                drawPath(
                                    path, color = color.copy(alpha = opacity),
                                    style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                            else -> {
                                val flat = ArrayList<Float>(livePoints.size * 2)
                                livePoints.forEach { flat.add(it.x); flat.add(it.y) }
                                val radii = ArrayList<Float>(liveWidths.size)
                                liveWidths.forEach { radii.add(it / 2f) }
                                drawLiveInk(tool, flat, radii, strokeColor(tool, color, opacity))
                            }
                        }
                    }
                }

                // 橡皮光标（屏幕坐标），半径随橡皮大小
                eraseCursor?.let { p ->
                    val cr = eraserSize / 2f
                    drawCircle(Color(0x14000000), radius = cr, center = p)
                    drawCircle(Color(0x4D000000), radius = cr, center = p, style = Stroke(1.5f))
                }
            }

            // 选中单个 image/text/贴纸：带手柄的选择框（缩放/旋转/删除）+ 文字样式条
            if (selectedId != null && tool == CanvasTool.SELECT) {
                val sel = viewModel.elements.firstOrNull { it.id == selectedId }
                if (sel is ImageElement || sel is TextElement) {
                    SelectionOverlay(
                        element = sel,
                        scale = scale,
                        offset = offset,
                        density = density,
                        onBegin = { viewModel.pushUndoOnce() },
                        onResize = { newW, newH -> viewModel.resizeImage(sel.id, newW, newH) },
                        onMove = { dx, dy -> viewModel.moveElement(sel.id, dx, dy) },
                        onFontSize = { fs -> viewModel.setTextFontSize(sel.id, fs) },
                        onRotate = { deg -> viewModel.rotateElement(sel.id, deg) },
                        onDelete = { viewModel.deleteElement(sel.id); selectedId = null },
                    )
                    if (sel is TextElement) {
                        TextStyleBar(
                            element = sel,
                            isSticker = sel.fontSize >= 64f && sel.text.length <= 3,
                            onFontSize = { fs -> viewModel.pushUndoOnce(); viewModel.setTextFontSize(sel.id, fs) },
                            onColor = { argb -> viewModel.pushUndoOnce(); viewModel.setTextColor(sel.id, argb) },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                        )
                    }
                }
            }
            if (selectedIds.isNotEmpty() && tool == CanvasTool.LASSO) {
                val sb = selectionBounds(viewModel.elements, selectedIds)
                if (sb != null) {
                    val midX = (sb[0] + sb[2]) / 2 * scale + offset.x
                    val topY = sb[1] * scale + offset.y
                    LassoMenu(
                        count = selectedIds.size,
                        onCopy = { selectedIds = viewModel.duplicateElements(selectedIds) },
                        onDelete = { viewModel.deleteElements(selectedIds); selectedIds = emptySet() },
                        modifier = Modifier.offset {
                            IntOffset(
                                (midX - 130).roundToInt().coerceAtLeast(8),
                                (topY - 64f).coerceAtLeast(8f).roundToInt(),
                            )
                        },
                    )
                }
            }

            // 内容完全跑出视野时浮出「回到内容」，点击平滑飞回适配
            val contentBounds = androidx.compose.runtime.remember(viewModel.revision) {
                var acc: FloatArray? = null
                viewModel.elements.forEach { el ->
                    elementBox(el)?.let { e ->
                        acc = acc?.let {
                            floatArrayOf(
                                minOf(it[0], e[0]), minOf(it[1], e[1]),
                                maxOf(it[2], e[2]), maxOf(it[3], e[3]),
                            )
                        } ?: e
                    }
                }
                acc
            }
            val contentOffscreen = contentBounds?.let { cb ->
                if (canvasSize.width <= 0) false else {
                    val tl = screenToWorld(Offset.Zero)
                    val br = screenToWorld(Offset(canvasSize.width.toFloat(), canvasSize.height.toFloat()))
                    cb[2] < tl.x || cb[0] > br.x || cb[3] < tl.y || cb[1] > br.y
                }
            } ?: false
            if (contentOffscreen) {
                Surface(
                    onClick = {
                        val cb = contentBounds ?: return@Surface
                        scope.launch {
                            val margin = 120f
                            val cw = (cb[2] - cb[0]) + margin * 2
                            val chh = (cb[3] - cb[1]) + margin * 2
                            val targetScale = minOf(
                                canvasSize.width / cw, canvasSize.height / chh, 1f,
                            ).coerceAtLeast(0.1f)
                            val cx = (cb[0] + cb[2]) / 2f
                            val cy = (cb[1] + cb[3]) / 2f
                            val targetOffset = Offset(
                                canvasSize.width / 2f - cx * targetScale,
                                canvasSize.height / 2f - cy * targetScale,
                            )
                            val sO = offset
                            val sS = scale
                            androidx.compose.animation.core.animate(
                                0f, 1f,
                                animationSpec = androidx.compose.animation.core.tween(380),
                            ) { t, _ ->
                                scale = sS + (targetScale - sS) * t
                                offset = Offset(
                                    sO.x + (targetOffset.x - sO.x) * t,
                                    sO.y + (targetOffset.y - sO.y) * t,
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xF2FFFFFF),
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14000000)),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Icon(HwIcons.NoteBadge, null, tint = Color(0xFF007DFF), modifier = Modifier.size(16.dp))
                        Text(
                            "回到内容", fontSize = 13.sp, color = Color(0xFF007DFF),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }

            // 缩放指示胶囊（点按恢复 100%）
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xC0F1F3F5),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14000000)),
            ) {
                Text(
                    "${(scale * 100).roundToInt()}%",
                    fontSize = 12.sp,
                    color = Color(0xFF4A4D50),
                    modifier = Modifier
                        .androidx_clickable { scale = 1f; offset = Offset.Zero }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            // 缩略图导航（小地图）
            if (minimapOn && viewModel.elements.isNotEmpty() && canvasSize.width > 0) {
                val tl = screenToWorld(Offset.Zero)
                val br = screenToWorld(Offset(canvasSize.width.toFloat(), canvasSize.height.toFloat()))
                Minimap(
                    elements = viewModel.elements.toList(),
                    viewport = floatArrayOf(tl.x, tl.y, br.x, br.y),
                    onJump = { wx, wy ->
                        offset = Offset(canvasSize.width / 2f - wx * scale, canvasSize.height / 2f - wy * scale)
                    },
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                )
            }

            if (showStickers) {
                StickerPicker(
                    onPick = { emoji ->
                        val c = screenToWorld(Offset(canvasSize.width / 2f, canvasSize.height / 2f))
                        viewModel.addSticker(emoji, c.x, c.y)
                        showStickers = false
                    },
                    onDismiss = { showStickers = false },
                )
            }

            // 正在编辑的文字框（覆盖在画布上，按世界→屏幕定位）
            editingId?.let { id ->
                val el = viewModel.elements.firstOrNull { it.id == id } as? TextElement
                if (el != null) {
                    var tfv by remember(id) { mutableStateOf(TextFieldValue(el.text)) }
                    val screenX = (el.x * scale + offset.x)
                    val screenY = (el.y * scale + offset.y)
                    BasicTextField(
                        value = tfv,
                        onValueChange = { tfv = it; viewModel.updateText(id, it.text) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color(el.color),
                            fontSize = with(density) { (el.fontSize * scale).toSp() },
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) },
                    )
                }
            }

            // 笔刷设置浮层：从顶部工具栏下方轻缩放+渐显落下（拟物真笔只在此弹框出现）
            androidx.compose.animation.AnimatedVisibility(
                visible = showBrushPanel,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) +
                    androidx.compose.animation.scaleIn(
                        animationSpec = androidx.compose.animation.core.tween(180),
                        initialScale = 0.9f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f),
                    ),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
                    androidx.compose.animation.scaleOut(
                        animationSpec = androidx.compose.animation.core.tween(140),
                        targetScale = 0.94f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f),
                    ),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            ) {
                BrushPanel(
                    tool = tool,
                    // 弹框内换笔：切换工具但保持弹框打开
                    onTool = { tool = it },
                    color = color,
                    onColor = { color = it },
                    width = width,
                    onWidth = { width = it },
                    opacity = opacity,
                    onOpacity = { opacity = it },
                    onClose = { showBrushPanel = false },
                )
            }

            // 橡皮设置浮层：与笔刷浮层同区域，内容精简（模式二选一 + 橡皮大小）
            androidx.compose.animation.AnimatedVisibility(
                visible = showEraserPanel,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) +
                    androidx.compose.animation.scaleIn(
                        animationSpec = androidx.compose.animation.core.tween(180),
                        initialScale = 0.9f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f),
                    ),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
                    androidx.compose.animation.scaleOut(
                        animationSpec = androidx.compose.animation.core.tween(140),
                        targetScale = 0.94f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f),
                    ),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            ) {
                EraserPanel(
                    mode = eraserMode,
                    onMode = { eraserMode = it },
                    size = eraserSize,
                    onSize = { eraserSize = it },
                    onClose = { showEraserPanel = false },
                )
            }
        }
    }
}

/* ====================== 橡皮设置浮层 ====================== */

@Composable
private fun EraserPanel(
    mode: EraserMode,
    onMode: (EraserMode) -> Unit,
    size: Float,
    onSize: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = hwBarColors()
    Surface(
        modifier = modifier.width(280.dp),
        color = if (isSystemInDarkTheme()) Color(0xFF2A2C2E) else Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14000000)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("橡皮", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    HwIcons.Close, "关闭", tint = c.ink,
                    modifier = Modifier.size(30.dp).clip(CircleShape).androidx_clickable(onClose).padding(5.dp),
                )
            }
            // 模式二选一分段控件
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSystemInDarkTheme()) Color(0xFF35373B) else Color(0xFFF1F3F5))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                EraserMode.entries.forEach { m ->
                    val selected = m == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF007DFF) else Color.Transparent)
                            .androidx_clickable { onMode(m) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            m.label,
                            fontSize = 13.sp,
                            color = if (selected) Color.White else c.ink,
                        )
                    }
                }
            }
            // 橡皮大小
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Text("橡皮大小", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${size.roundToInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HwSlider(value = size, onValue = onSize, range = 8f..60f)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCanvasBackground(
    style: String, offset: Offset, scale: Float,
) {
    if (style == "blank") return
    val lineColor = Color(0x14000000)
    val spacing = 48f * scale
    if (spacing < 8f) return
    when (style) {
        "grid", "lines" -> {
            var y = offset.y.mod(spacing)
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f); y += spacing
            }
            if (style == "grid") {
                var x = offset.x.mod(spacing)
                while (x < size.width) {
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1f); x += spacing
                }
            }
        }
        "dots" -> {
            var y = offset.y.mod(spacing)
            while (y < size.height) {
                var x = offset.x.mod(spacing)
                while (x < size.width) { drawCircle(lineColor, 2f, Offset(x, y)); x += spacing }
                y += spacing
            }
        }
    }
}

/** 基底层渲染快照参数：任一字段变化即重烘焙。 */
private data class BakeStamp(
    val revision: Long,
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float,
    val width: Int,
    val height: Int,
    val background: String,
    val editingId: String?,
    val loadedImages: Int,
    val fading: Int,
)

/** 已落墨世界：纸张背景 + 全部元素（供实时绘制兜底与位图烘焙共用）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCommittedWorld(
    elements: List<com.yhx.notices.domain.canvas.CanvasElement>,
    bitmaps: Map<Long, ImageBitmap?>,
    background: String,
    offset: Offset,
    scale: Float,
    editingId: String?,
    inkCache: HashMap<String, List<Path>>,
    pendingErase: Set<String>,
) {
    drawCanvasBackground(background, offset, scale)
    withTransform({
        translate(offset.x, offset.y)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        elements.forEach { el ->
            val faded = el.id in pendingErase
            when (el) {
                is StrokeElement -> drawStrokeElement(el, inkCache, faded)
                is ImageElement -> bitmaps[el.attachmentId]?.let { bmp ->
                    val drawImg: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                        drawImage(
                            image = bmp,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bmp.width, bmp.height),
                            dstOffset = IntOffset(el.x.roundToInt(), el.y.roundToInt()),
                            dstSize = IntSize(el.width.roundToInt(), el.height.roundToInt()),
                            alpha = if (faded) 0.25f else 1f,
                        )
                    }
                    if (el.rotation != 0f) {
                        withTransform({
                            rotate(el.rotation, pivot = Offset(el.x + el.width / 2f, el.y + el.height / 2f))
                        }) { drawImg() }
                    } else drawImg()
                }
                is TextElement -> if (el.id != editingId && el.text.isNotEmpty()) {
                    val paint = android.graphics.Paint().apply {
                        this.color = el.color
                        textSize = el.fontSize
                        isAntiAlias = true
                        if (faded) alpha = 64
                    }
                    if (el.rotation != 0f) {
                        // 与 elementBounds 一致：宽 length*fontSize*0.6、高 fontSize*1.4
                        val tw = el.text.length.coerceAtLeast(2) * el.fontSize * 0.6f
                        val cx = el.x + tw / 2f
                        val cy = el.y + el.fontSize * 0.7f
                        val nc = drawContext.canvas.nativeCanvas
                        nc.save()
                        nc.rotate(el.rotation, cx, cy)
                        nc.drawText(el.text, el.x, el.y + el.fontSize, paint)
                        nc.restore()
                    } else {
                        drawContext.canvas.nativeCanvas.drawText(el.text, el.x, el.y + el.fontSize, paint)
                    }
                }
            }
        }
    }
}

/** 笔迹是否被擦除点（半径 r，含笔宽）命中：按「点到线段」距离判定，快笔稀疏采样也不漏。 */
private fun strokeHit(el: StrokeElement, wx: Float, wy: Float, r: Float): Boolean {
    val reach = r + el.width / 2f
    val r2 = reach * reach
    val pts = el.points
    if (pts.size < 2) return false
    if (pts.size == 2) {
        val dx = pts[0] - wx
        val dy = pts[1] - wy
        return dx * dx + dy * dy <= r2
    }
    var i = 0
    while (i + 3 < pts.size) {
        val ax = pts[i]; val ay = pts[i + 1]
        val bx = pts[i + 2]; val by = pts[i + 3]
        val vx = bx - ax; val vy = by - ay
        val len2 = vx * vx + vy * vy
        val t = if (len2 <= 1e-6f) 0f else (((wx - ax) * vx + (wy - ay) * vy) / len2).coerceIn(0f, 1f)
        val dx = wx - (ax + vx * t)
        val dy = wy - (ay + vy * t)
        if (dx * dx + dy * dy <= r2) return true
        i += 2
    }
    return false
}

/** 落墨渲染：轮廓填充 + Path 缓存（key 随平移变化自动失效）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokeElement(
    el: StrokeElement,
    cache: HashMap<String, List<Path>>,
    faded: Boolean = false,
) {
    if (el.points.size < 2) return
    if (cache.size > 800) cache.clear()
    val key = "${el.id}:${el.points.size}:${el.points.first()}:${el.points.last()}"
    val n = el.points.size / 2
    val paths = cache.getOrPut(key) {
        val radii = if (el.widths.size == n) el.widths.map { it / 2f } else List(n) { el.width / 2f }
        buildInkPaths(el.tool, el.points, radii)
    }
    val base = Color(el.color)
    val c = if (faded) base.copy(alpha = base.alpha * 0.25f) else base
    when (el.tool) {
        "highlighter" -> drawPath(paths[0], c, blendMode = androidx.compose.ui.graphics.BlendMode.Multiply)
        "pencil" -> {
            // 石墨干介质：毛糙宽层淡 + 紧实芯层深
            drawPath(paths[0], c.copy(alpha = c.alpha * 0.45f))
            if (paths.size > 1) drawPath(paths[1], c.copy(alpha = c.alpha * 0.75f))
        }
        else -> drawPath(paths[0], c)
    }
}

/** 按笔刷生成渲染层：铅笔为「毛边宽层 + 紧实芯层」双层，其余单层。 */
private fun buildInkPaths(tool: String, points: List<Float>, radii: List<Float>): List<Path> {
    return if (tool == "pencil") {
        val rough = radii.mapIndexed { i, r -> r * (0.8f + 0.45f * grain(i, points[i * 2])) }
        val core = radii.mapIndexed { i, r -> r * 0.55f * (0.85f + 0.3f * grain(i + 13, points[i * 2 + 1])) }
        listOf(
            outlineToPath(InkGeometry.strokeOutline(points, rough)),
            outlineToPath(InkGeometry.strokeOutline(points, core)),
        )
    } else {
        listOf(outlineToPath(InkGeometry.strokeOutline(points, radii, roundCaps = tool != "highlighter")))
    }
}

/** 实时预览：与落墨同一分笔刷逻辑（不缓存）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiveInk(
    tool: CanvasTool,
    flat: List<Float>,
    radii: List<Float>,
    color: Color,
) {
    val paths = buildInkPaths(tool.name.lowercase(), flat, radii)
    when (tool) {
        CanvasTool.HIGHLIGHTER -> drawPath(paths[0], color, blendMode = androidx.compose.ui.graphics.BlendMode.Multiply)
        CanvasTool.PENCIL -> {
            drawPath(paths[0], color.copy(alpha = color.alpha * 0.45f))
            if (paths.size > 1) drawPath(paths[1], color.copy(alpha = color.alpha * 0.75f))
        }
        else -> drawPath(paths[0], color)
    }
}

/** 确定性伪随机（0..1）：由采样序号与坐标散列，保证重绘稳定不闪烁。 */
private fun grain(i: Int, v: Float): Float {
    var h = i * 374761393 + v.toRawBits() * 668265263
    h = h xor (h shr 13)
    return (h and 0xFFFF) / 65535f
}

/** 闭合轮廓多边形 → 填充 Path。 */
private fun outlineToPath(outline: FloatArray): Path = Path().apply {
    if (outline.size >= 6) {
        moveTo(outline[0], outline[1])
        var i = 2
        while (i + 1 < outline.size) { lineTo(outline[i], outline[i + 1]); i += 2 }
        close()
    }
}

/** 笔速(px/ms)/压感 → 笔宽系数：秀丽笔大幅变宽出笔锋，铅笔微变，其余恒定。 */
private fun widthFactor(
    tool: CanvasTool,
    speedPxPerMs: Float,
    pressure: Float,
    pointerType: PointerType,
): Float = when (tool) {
    CanvasTool.PEN -> {
        if (pointerType == PointerType.Stylus && pressure > 0.01f && pressure <= 1.5f) {
            0.45f + pressure.coerceIn(0f, 1f) * 1.05f // 真实压感优先
        } else {
            val s = (speedPxPerMs / 2.5f).coerceIn(0f, 1f)
            1.45f - s * 0.95f // 慢笔粗、快笔细，模拟提按
        }
    }
    CanvasTool.FOUNTAIN -> {
        // 钢笔：硬尖，几乎恒宽，仅微弱提按
        val s = (speedPxPerMs / 2.5f).coerceIn(0f, 1f)
        1.08f - s * 0.22f
    }
    CanvasTool.PENCIL -> {
        val s = (speedPxPerMs / 2.5f).coerceIn(0f, 1f)
        1.1f - s * 0.25f
    }
    else -> 1f
}

private fun strokeColor(tool: CanvasTool, color: Color, opacity: Float = 1f): Color = when (tool) {
    CanvasTool.HIGHLIGHTER -> color.copy(alpha = 0.35f * opacity)
    CanvasTool.MARKER -> color.copy(alpha = 0.92f * opacity)
    CanvasTool.ERASER -> Color.White
    else -> color.copy(alpha = opacity)
}

private fun strokeWidth(tool: CanvasTool, width: Float): Float = when (tool) {
    CanvasTool.HIGHLIGHTER -> width * 3f
    CanvasTool.MARKER -> width * 2.2f
    CanvasTool.ERASER -> width * 5f
    CanvasTool.PENCIL -> width * 0.7f
    CanvasTool.FOUNTAIN -> width * 0.8f
    else -> width
}

/* ====================== 华为风格顶部栏 ====================== */

/** 顶栏配色（标题行/工具行/圆钮底/墨色/选中底/发丝线），区分深浅色模式。 */
private data class HwBarColors(
    val titleBg: Color,
    val toolBg: Color,
    val circleBg: Color,
    val ink: Color,
    val inkDisabled: Color,
    val selBg: Color,
    val hairline: Color,
)

@Composable
private fun hwBarColors(): HwBarColors = if (isSystemInDarkTheme()) HwBarColors(
    titleBg = Color(0xFF1E2022), toolBg = Color(0xFF26282B), circleBg = Color(0xFF35373B),
    ink = Color(0xFFE6E8EA), inkDisabled = Color(0xFF5A5E62), selBg = Color(0xFF234A77), hairline = Color(0x22FFFFFF),
) else HwBarColors(
    titleBg = Color(0xFFF1F3F5), toolBg = Color(0xFFFCFCFE), circleBg = Color(0xFFE7E9EC),
    ink = Color(0xFF1B1D1F), inkDisabled = Color(0xFFB9BDC1), selBg = Color(0xFFD6E6FF), hairline = Color(0x14000000),
)

/** 笔托盘的笔位（工具 → 拟物插画）。 */
private val trayPens by lazy {
    listOf(
        CanvasTool.FOUNTAIN to com.yhx.notices.ui.icons.HwPens.Fountain,
        CanvasTool.PEN to com.yhx.notices.ui.icons.HwPens.Calligraphy,
        CanvasTool.PENCIL to com.yhx.notices.ui.icons.HwPens.Pencil,
        CanvasTool.MARKER to com.yhx.notices.ui.icons.HwPens.Marker,
        CanvasTool.HIGHLIGHTER to com.yhx.notices.ui.icons.HwPens.Highlighter,
        CanvasTool.ERASER to com.yhx.notices.ui.icons.HwPens.Eraser,
    )
}

/** 弹框三档快捷笔号。 */
private val traySizes = listOf(4f, 8f, 14f)

/** 无界笔记顶部栏：标题行 + 工具行（对照华为平板真机布局）。 */
@Composable
private fun HwCanvasTopBar(
    title: String,
    onTitleChange: (String) -> Unit,
    onBack: () -> Unit,
    tool: CanvasTool,
    onTool: (CanvasTool) -> Unit,
    penColor: Color,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    palmBlock: Boolean,
    onTogglePalm: () -> Unit,
    background: String,
    onBackground: (String) -> Unit,
    minimapOn: Boolean,
    onToggleMinimap: () -> Unit,
    onInsertImage: () -> Unit,
    onInsertPdf: () -> Unit,
    onInsertSticker: () -> Unit,
    onInsertSpace: () -> Unit,
    onShareImage: () -> Unit,
    onExportGallery: () -> Unit,
    onExportPdf: () -> Unit,
) {
    val c = hwBarColors()
    Column(Modifier.background(c.titleBg).statusBarsPadding()) {
        // —— 第一行：返回 + 笔记徽标 + 可编辑标题 + 右侧圆钮组 ——
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HwCircleButton(HwIcons.Back, "返回", c, onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Icon(HwIcons.NoteBadge, null, tint = c.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = c.ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF007DFF)),
                decorationBox = { inner ->
                    if (title.isEmpty()) Text("无界笔记", color = c.inkDisabled, style = MaterialTheme.typography.titleMedium)
                    inner()
                },
                modifier = Modifier.weight(1f),
            )
            var insertOpen by remember { mutableStateOf(false) }
            Box {
                HwCircleButton(HwIcons.Add, "插入", c) { insertOpen = true }
                DropdownMenu(insertOpen, { insertOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("图片") },
                        leadingIcon = { Icon(HwIcons.Image, null, tint = c.ink, modifier = Modifier.size(20.dp)) },
                        onClick = { insertOpen = false; onInsertImage() },
                    )
                    DropdownMenuItem(
                        text = { Text("PDF") },
                        leadingIcon = { Icon(HwIcons.NoteBadge, null, tint = c.ink, modifier = Modifier.size(20.dp)) },
                        onClick = { insertOpen = false; onInsertPdf() },
                    )
                    DropdownMenuItem(
                        text = { Text("贴纸") },
                        leadingIcon = { Icon(HwIcons.Sticker, null, tint = c.ink, modifier = Modifier.size(20.dp)) },
                        onClick = { insertOpen = false; onInsertSticker() },
                    )
                    DropdownMenuItem(
                        text = { Text("插入纵向空白") },
                        leadingIcon = { Icon(HwIcons.InsertSpace, null, tint = c.ink, modifier = Modifier.size(20.dp)) },
                        onClick = { insertOpen = false; onInsertSpace() },
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            HwCircleButton(HwIcons.Panel, "缩略图", c, active = minimapOn, onClick = onToggleMinimap)
            Spacer(Modifier.width(10.dp))
            var moreOpen by remember { mutableStateOf(false) }
            Box {
                HwCircleButton(HwIcons.GridMenu, "更多", c) { moreOpen = true }
                DropdownMenu(moreOpen, { moreOpen = false }) {
                    DropdownMenuItem(text = { Text("分享为图片") }, onClick = { moreOpen = false; onShareImage() })
                    DropdownMenuItem(text = { Text("导出图片到相册") }, onClick = { moreOpen = false; onExportGallery() })
                    DropdownMenuItem(text = { Text("导出 PDF") }, onClick = { moreOpen = false; onExportPdf() })
                }
            }
        }
        // —— 第二行：撤销重做 ｜ 笔具 ｜ 功能 ｜ 线宽预设 + 快捷色 ——
        Column(Modifier.background(c.toolBg)) {
            Row(
                Modifier.fillMaxWidth().height(46.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HwToolIcon(HwIcons.Undo, "撤销", c, enabled = canUndo, onClick = onUndo)
                HwToolIcon(HwIcons.Redo, "重做", c, enabled = canRedo, onClick = onRedo)
                HwToolDivider(c)
                HwToolButton(HwIcons.Move, CanvasTool.MOVE, tool, c, onTool)
                HwToolButton(HwIcons.Lasso, CanvasTool.LASSO, tool, c, onTool)
                HwToolButton(HwIcons.TextBox, CanvasTool.TEXT, tool, c, onTool)
                HwToolDivider(c)
                // 笔具组（单色线性图标，选中淡蓝圆底；当前书写笔带颜色小点）
                HwPenToolButton(HwIcons.Fountain, CanvasTool.FOUNTAIN, tool, c, penColor, onTool)
                HwPenToolButton(HwIcons.Pen, CanvasTool.PEN, tool, c, penColor, onTool)
                HwPenToolButton(HwIcons.Pencil, CanvasTool.PENCIL, tool, c, penColor, onTool)
                HwPenToolButton(HwIcons.Marker, CanvasTool.MARKER, tool, c, penColor, onTool)
                HwPenToolButton(HwIcons.Highlighter, CanvasTool.HIGHLIGHTER, tool, c, penColor, onTool)
                HwToolButton(HwIcons.Eraser, CanvasTool.ERASER, tool, c, onTool)
                HwToolDivider(c)
                HwToolButton(HwIcons.ShapeRecog, CanvasTool.SHAPE, tool, c, onTool)
                HwToolIcon(HwIcons.Palm, "防误触", c, active = palmBlock, onClick = onTogglePalm)
                var paperOpen by remember { mutableStateOf(false) }
                Box {
                    HwToolIcon(HwIcons.Paper, "纸张样式", c) { paperOpen = true }
                    DropdownMenu(paperOpen, { paperOpen = false }) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            PaperOption("blank", "空白", background, c) { onBackground(it); paperOpen = false }
                            PaperOption("grid", "网格", background, c) { onBackground(it); paperOpen = false }
                            PaperOption("lines", "横线", background, c) { onBackground(it); paperOpen = false }
                            PaperOption("dots", "点阵", background, c) { onBackground(it); paperOpen = false }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        }
    }
}

/** 标题行圆形按钮（浅灰圆底 + 线性图标）。 */
@Composable
private fun HwCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    c: HwBarColors,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) c.selBg else c.circleBg)
            .androidx_clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = c.ink, modifier = Modifier.size(20.dp))
    }
}

/** 工具行按钮：选中时淡蓝圆底（华为式轻高亮）。 */
@Composable
private fun HwToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    t: CanvasTool,
    current: CanvasTool,
    c: HwBarColors,
    onTool: (CanvasTool) -> Unit,
) {
    val selected = current == t
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) c.selBg else Color.Transparent,
        androidx.compose.animation.core.tween(160), label = "toolBg",
    )
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(bg)
            .androidx_clickable { onTool(t) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, t.label, tint = c.ink, modifier = Modifier.size(22.dp))
    }
}

/** 工具行笔具按钮：与 HwToolButton 同款选中淡蓝圆底，选中时右下角带当前笔色小点。 */
@Composable
private fun HwPenToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    t: CanvasTool,
    current: CanvasTool,
    c: HwBarColors,
    penColor: Color,
    onTool: (CanvasTool) -> Unit,
) {
    val selected = current == t
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) c.selBg else Color.Transparent,
        androidx.compose.animation.core.tween(160), label = "penToolBg",
    )
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(bg)
            .androidx_clickable { onTool(t) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, t.label, tint = c.ink, modifier = Modifier.size(22.dp))
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(penColor)
                    .border(0.5.dp, Color(0x33000000), CircleShape),
            )
        }
    }
}

/** 工具行功能图标（无工具态；可禁用/可激活高亮）。 */
@Composable
private fun HwToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    c: HwBarColors,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) c.selBg else Color.Transparent)
            .androidx_clickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (enabled) c.ink else c.inkDisabled, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun HwToolDivider(c: HwBarColors) {
    Box(Modifier.padding(horizontal = 7.dp).width(1.dp).height(20.dp).background(c.hairline))
}

/* ====================== 拟物笔（弹框内复用） ====================== */

/** 弹框中的一支拟物笔：选中上浮、未选中插回。 */
@Composable
private fun PenSlot(
    art: androidx.compose.ui.graphics.vector.ImageVector,
    t: CanvasTool,
    current: CanvasTool,
    onTool: (CanvasTool) -> Unit,
) {
    val selected = current == t
    val lift by androidx.compose.animation.core.animateDpAsState(
        if (selected) 0.dp else 13.dp,
        androidx.compose.animation.core.tween(170),
        label = "penLift",
    )
    Icon(
        art, t.label, tint = Color.Unspecified,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(width = 29.dp, height = 66.dp)
            .offset(y = lift)
            .androidx_clickable { onTool(t) },
    )
}

/** 弹框笔号档位（圆点大小示意笔粗）。 */
@Composable
private fun SizeDot(s: Float, selected: Boolean, ink: Color, onWidth: (Float) -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .androidx_clickable { onWidth(s) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            drawCircle(ink, radius = (1.6f + s * 0.42f).dp.toPx())
            if (selected) {
                drawCircle(Color(0xFF007DFF), radius = 12.dp.toPx(), style = Stroke(1.6.dp.toPx()))
            }
        }
    }
}


/** 纸张样式选项（小预览块 + 标签）。 */
@Composable
private fun PaperOption(style: String, label: String, current: String, c: HwBarColors, onPick: (String) -> Unit) {
    val selected = style == current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 6.dp).androidx_clickable { onPick(style) },
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color(0xFF007DFF) else Color(0x1F000000),
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val lc = Color(0x2E000000)
                val sp = size.width / 4.5f
                when (style) {
                    "grid" -> {
                        var x = sp; while (x < size.width) { drawLine(lc, Offset(x, 0f), Offset(x, size.height), 1f); x += sp }
                        var y = sp; while (y < size.height) { drawLine(lc, Offset(0f, y), Offset(size.width, y), 1f); y += sp }
                    }
                    "lines" -> {
                        var y = sp; while (y < size.height) { drawLine(lc, Offset(3f, y), Offset(size.width - 3f, y), 1f); y += sp }
                    }
                    "dots" -> {
                        var y = sp
                        while (y < size.height) {
                            var x = sp
                            while (x < size.width) { drawCircle(lc, 1.6f, Offset(x, y)); x += sp }
                            y += sp
                        }
                    }
                }
            }
        }
        Text(label, fontSize = 11.sp, color = c.ink, modifier = Modifier.padding(top = 4.dp))
    }
}

/* ====================== 笔刷设置浮层（华为样式） ====================== */

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BrushPanel(
    tool: CanvasTool,
    onTool: (CanvasTool) -> Unit,
    color: Color,
    onColor: (Color) -> Unit,
    width: Float,
    onWidth: (Float) -> Unit,
    opacity: Float,
    onOpacity: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = hwBarColors()
    Surface(
        modifier = modifier.width(320.dp),
        color = if (isSystemInDarkTheme()) Color(0xFF2A2C2E) else Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14000000)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            // 标题（当前笔名）+ 关闭
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tool.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    HwIcons.Close, "关闭", tint = c.ink,
                    modifier = Modifier.size(30.dp).clip(CircleShape).androidx_clickable(onClose).padding(5.dp),
                )
            }
            // 拟物真笔一行（选中上浮，tint=Unspecified 保留多色插画）
            Row(
                Modifier.fillMaxWidth().height(70.dp).clipToBounds().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                trayPens.forEach { (t, art) -> PenSlot(art, t, tool, onTool) }
            }
            // 三档快捷笔号
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                traySizes.forEach { s -> SizeDot(s, s == width, c.ink, onWidth) }
            }
            // 粗细
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Text("粗细", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${width.roundToInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HwSlider(value = width, onValue = onWidth, range = 1f..30f)
            // 不透明度
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("不透明度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HwSlider(value = opacity, onValue = onOpacity, range = 0.1f..1f)
            // 颜色网格
            Text("颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp)) {
                gridColors.forEach { gc -> ColorSwatch(gc, gc == color, onColor) }
            }
        }
    }
}

/** 华为风格细滑条：3.5dp 圆角轨道 + 小白圆钮（不依赖 M3 Slider 的版本差异）。 */
@Composable
private fun HwSlider(value: Float, onValue: (Float) -> Unit, range: ClosedFloatingPointRange<Float>) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(range) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pad = 8.dp.toPx()
                    fun set(x: Float) {
                        val f = ((x - pad) / (size.width - pad * 2)).coerceIn(0f, 1f)
                        onValue(range.start + f * (range.endInclusive - range.start))
                    }
                    set(down.position.x); down.consume()
                    drag(down.id) { ch -> set(ch.position.x); ch.consume() }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 8.dp.toPx()
            val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            val cy = size.height / 2
            val trackH = 3.5.dp.toPx()
            val w = size.width - pad * 2
            drawRoundRect(
                Color(0x1F787880),
                topLeft = Offset(pad, cy - trackH / 2),
                size = androidx.compose.ui.geometry.Size(w, trackH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2),
            )
            drawRoundRect(
                Color(0xFF007DFF),
                topLeft = Offset(pad, cy - trackH / 2),
                size = androidx.compose.ui.geometry.Size(w * frac, trackH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2),
            )
            val tx = pad + w * frac
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(tx, cy))
            drawCircle(Color(0x29000000), radius = 8.dp.toPx(), center = Offset(tx, cy), style = Stroke(1.dp.toPx()))
        }
    }
}


@Composable
private fun ColorSwatch(c: Color, selected: Boolean, onColor: (Color) -> Unit) {
    Box(
        Modifier
            .padding(end = 7.dp)
            .size(if (selected) 28.dp else 24.dp)
            .clip(CircleShape)
            .background(c)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color(0x22000000),
                shape = CircleShape,
            )
            .androidx_clickable { onColor(c) },
    )
}

private fun Modifier.androidx_clickable(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }
