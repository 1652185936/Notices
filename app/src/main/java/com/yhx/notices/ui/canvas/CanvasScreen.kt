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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.filled.Height
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yhx.notices.domain.canvas.ImageElement
import com.yhx.notices.domain.canvas.StrokeElement
import com.yhx.notices.domain.canvas.TextElement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

enum class CanvasTool(val label: String) {
    MOVE("移动"), SELECT("选择"), LASSO("套索"), PEN("钢笔"), PENCIL("铅笔"),
    HIGHLIGHTER("荧光笔"), SHAPE("形状"), ERASER("橡皮"), TEXT("文字")
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

/** 一笔成形：把手绘笔迹识别为直线/矩形/椭圆，返回规整后的点；无法识别返回 null。 */
private fun recognizeShape(pts: List<Float>): List<Float>? {
    if (pts.size < 10) return null
    val n = pts.size / 2
    val xs = FloatArray(n) { pts[it * 2] }
    val ys = FloatArray(n) { pts[it * 2 + 1] }
    val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
    val w = maxX - minX; val h = maxY - minY
    val span = maxOf(w, h)
    if (span < 24f) return null
    val x0 = xs[0]; val y0 = ys[0]; val x1 = xs[n - 1]; val y1 = ys[n - 1]

    var maxPerp = 0f
    for (i in 0 until n) maxPerp = maxOf(maxPerp, perpDist(xs[i], ys[i], x0, y0, x1, y1))
    val chord = hypot(x1 - x0, y1 - y0)
    if (chord > span * 0.5f && maxPerp < span * 0.10f) return lineSamples(x0, y0, x1, y1)

    if (hypot(x1 - x0, y1 - y0) > span * 0.30f) return null // 未闭合
    val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
    val rx = w / 2; val ry = h / 2
    var ellRes = 0f; var rectRes = 0f
    for (i in 0 until n) {
        val nx = (xs[i] - cx) / (rx + 1e-3f); val ny = (ys[i] - cy) / (ry + 1e-3f)
        ellRes += abs(hypot(nx, ny) - 1f)
        rectRes += distToRectEdge(xs[i], ys[i], minX, minY, maxX, maxY) / span
    }
    ellRes /= n; rectRes /= n
    return if (ellRes < rectRes) ellipseSamples(cx, cy, rx, ry) else rectSamples(minX, minY, maxX, maxY)
}

private fun perpDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay; val len = hypot(dx, dy)
    return if (len < 1e-3f) hypot(px - ax, py - ay) else abs((px - ax) * dy - (py - ay) * dx) / len
}

private fun distToRectEdge(px: Float, py: Float, minX: Float, minY: Float, maxX: Float, maxY: Float): Float =
    minOf(abs(px - minX), abs(px - maxX), abs(py - minY), abs(py - maxY))

private fun lineSamples(x0: Float, y0: Float, x1: Float, y1: Float): List<Float> {
    val out = ArrayList<Float>(); val steps = 16
    for (i in 0..steps) { val t = i / steps.toFloat(); out.add(x0 + (x1 - x0) * t); out.add(y0 + (y1 - y0) * t) }
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
private val widths = listOf(3f, 6f, 12f, 20f)
private val penTools = setOf(CanvasTool.PEN, CanvasTool.PENCIL, CanvasTool.HIGHLIGHTER)

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

    val livePoints = remember { mutableStateListOf<Offset>() }
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

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            // 插入到当前视口中心对应的世界坐标
            val world = screenToWorld(Offset(600f, 800f))
            viewModel.insertImage(it, world.x, world.y)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.onExit(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    BasicTextField(
                        value = viewModel.title,
                        onValueChange = viewModel::onTitleChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { inner ->
                            if (viewModel.title.isEmpty()) {
                                Text("无界笔记", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        },
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::cycleBackground) {
                        Icon(Icons.Default.GridOn, contentDescription = "纸张模板")
                    }
                    IconButton(onClick = viewModel::undo, enabled = viewModel.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    androidx.compose.material3.DropdownMenu(menuOpen, { menuOpen = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("分享为图片") },
                            onClick = {
                                menuOpen = false
                                viewModel.shareAsImage { uri ->
                                    uri?.let { com.yhx.notices.ui.editor.shareUri(ctx, it, "image/png") }
                                }
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("导出图片到相册") },
                            onClick = {
                                menuOpen = false
                                viewModel.exportImageToGallery { ok ->
                                    android.widget.Toast.makeText(
                                        ctx, if (ok) "已保存到相册" else "导出失败",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("导出 PDF") },
                            onClick = {
                                menuOpen = false
                                viewModel.exportPdf { uri ->
                                    uri?.let { com.yhx.notices.ui.editor.shareUri(ctx, it, "application/pdf") }
                                }
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        CanvasToolbar(
            tool = tool,
            onTool = { t ->
                if (t == tool && t in penTools) showBrushPanel = !showBrushPanel
                else { tool = t; showBrushPanel = t in penTools }
            },
            color = color,
            onOpenBrush = { showBrushPanel = true },
            scalePercent = (scale * 100).roundToInt(),
            onZoomIn = { scale = (scale * 1.25f).coerceAtMost(10f) },
            onZoomOut = { scale = (scale / 1.25f).coerceAtLeast(0.1f) },
            onReset = { scale = 1f; offset = Offset.Zero },
            onImage = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onInsertSpace = {
                val cy = screenToWorld(Offset(canvasSize.width / 2f, canvasSize.height / 2f)).y
                viewModel.insertVerticalSpace(cy, 400f)
            },
            onSticker = { showStickers = true },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .onSizeChanged { canvasSize = it }
                .pointerInput(tool) {
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
                        else -> detectDragGestures(
                            onDragStart = { p ->
                                livePoints.clear(); livePoints.add(screenToWorld(p))
                            },
                            onDrag = { change, _ ->
                                livePoints.add(screenToWorld(change.position)); change.consume()
                            },
                            onDragEnd = {
                                if (livePoints.size >= 1) {
                                    val flat = ArrayList<Float>(livePoints.size * 2)
                                    livePoints.forEach { flat.add(it.x); flat.add(it.y) }
                                    when (tool) {
                                        CanvasTool.ERASER -> viewModel.eraseStrokes(flat, 20f / scale)
                                        CanvasTool.SHAPE -> viewModel.addStroke(
                                            StrokeElement(
                                                tool = "pen",
                                                color = color.toArgb(),
                                                width = width,
                                                points = recognizeShape(flat) ?: flat,
                                            )
                                        )
                                        else -> viewModel.addStroke(
                                            StrokeElement(
                                                tool = tool.name.lowercase(),
                                                color = strokeColor(tool, color).toArgb(),
                                                width = strokeWidth(tool, width),
                                                points = flat,
                                            )
                                        )
                                    }
                                }
                                livePoints.clear()
                            },
                        )
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCanvasBackground(viewModel.background, offset, scale)
                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    viewModel.elements.forEach { el ->
                        when (el) {
                            is StrokeElement -> drawStrokeElement(el)
                            is ImageElement -> bitmaps[el.attachmentId]?.let { bmp ->
                                drawImage(
                                    image = bmp,
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(bmp.width, bmp.height),
                                    dstOffset = IntOffset(el.x.roundToInt(), el.y.roundToInt()),
                                    dstSize = IntSize(el.width.roundToInt(), el.height.roundToInt()),
                                )
                            }
                            is TextElement -> if (el.id != editingId && el.text.isNotEmpty()) {
                                val paint = android.graphics.Paint().apply {
                                    this.color = el.color
                                    textSize = el.fontSize
                                    isAntiAlias = true
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    el.text, el.x, el.y + el.fontSize, paint,
                                )
                            }
                        }
                    }
                    // 选中元素高亮框
                    selectedId?.let { sid ->
                        viewModel.elements.firstOrNull { it.id == sid }?.let { el ->
                            val b = elementBounds(el)
                            if (b != null) {
                                drawRect(
                                    color = Color(0xFF007DFF),
                                    topLeft = Offset(b[0], b[1]),
                                    size = androidx.compose.ui.geometry.Size(b[2], b[3]),
                                    style = Stroke(2f / scale),
                                )
                            }
                        }
                    }
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
                    // 实时预览笔迹
                    if (livePoints.size >= 2) {
                        val path = Path().apply {
                            moveTo(livePoints.first().x, livePoints.first().y)
                            for (i in 1 until livePoints.size) lineTo(livePoints[i].x, livePoints[i].y)
                        }
                        drawPath(
                            path, color = strokeColor(tool, color),
                            style = Stroke(strokeWidth(tool, width), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }

            if (selectedId != null && tool == CanvasTool.SELECT) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { selectedId?.let { viewModel.deleteElement(it) }; selectedId = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ) { Icon(Icons.Default.Delete, "删除选中") }
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

            // 缩略图导航（小地图）
            if (viewModel.elements.isNotEmpty() && canvasSize.width > 0) {
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

            if (showBrushPanel) {
                BrushPanel(
                    tool = tool,
                    onTool = { tool = it },
                    color = color,
                    onColor = { color = it },
                    width = width,
                    onWidth = { width = it },
                    onClose = { showBrushPanel = false },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
        }
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokeElement(el: StrokeElement) {
    if (el.points.size < 4) {
        if (el.points.size >= 2) {
            drawCircle(Color(el.color), radius = el.width / 2f, center = Offset(el.points[0], el.points[1]))
        }
        return
    }
    val path = Path().apply {
        moveTo(el.points[0], el.points[1])
        var i = 2
        while (i + 1 < el.points.size) {
            lineTo(el.points[i], el.points[i + 1]); i += 2
        }
    }
    drawPath(
        path, color = Color(el.color),
        style = Stroke(el.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun strokeColor(tool: CanvasTool, color: Color): Color = when (tool) {
    CanvasTool.HIGHLIGHTER -> color.copy(alpha = 0.35f)
    CanvasTool.ERASER -> Color.White
    else -> color
}

private fun strokeWidth(tool: CanvasTool, width: Float): Float = when (tool) {
    CanvasTool.HIGHLIGHTER -> width * 3f
    CanvasTool.ERASER -> width * 5f
    CanvasTool.PENCIL -> width * 0.7f
    else -> width
}

@Composable
private fun CanvasToolbar(
    tool: CanvasTool,
    onTool: (CanvasTool) -> Unit,
    color: Color,
    onOpenBrush: () -> Unit,
    scalePercent: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onImage: () -> Unit,
    onInsertSpace: () -> Unit,
    onSticker: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(tool, CanvasTool.MOVE, Icons.Default.PanTool, onTool)
            ToolButton(tool, CanvasTool.SELECT, Icons.Default.HighlightAlt, onTool)
            ToolButton(tool, CanvasTool.LASSO, Icons.Default.Gesture, onTool)
            ToolDivider()
            ToolButton(tool, CanvasTool.PEN, Icons.Default.Edit, onTool)
            ToolButton(tool, CanvasTool.PENCIL, Icons.Default.Create, onTool)
            ToolButton(tool, CanvasTool.HIGHLIGHTER, Icons.Default.Brush, onTool)
            ToolButton(tool, CanvasTool.SHAPE, Icons.Default.Category, onTool)
            ToolButton(tool, CanvasTool.ERASER, Icons.Default.CleaningServices, onTool)
            ToolButton(tool, CanvasTool.TEXT, Icons.Default.TextFields, onTool)
            ToolDivider()
            PlainTool(Icons.Default.Image, "图片", onImage)
            PlainTool(Icons.Default.EmojiEmotions, "贴纸", onSticker)
            PlainTool(Icons.Default.Height, "插入空白", onInsertSpace)
            ToolDivider()
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(color)
                    .border(1.dp, Color(0x33000000), CircleShape)
                    .androidx_clickable(onOpenBrush),
            )
            Box(Modifier.width(6.dp))
            IconButton(onClick = onZoomOut) { Icon(Icons.Default.Remove, "缩小") }
            Text(
                "$scalePercent%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).androidx_clickable(onReset).padding(horizontal = 4.dp, vertical = 4.dp),
            )
            IconButton(onClick = onZoomIn) { Icon(Icons.Default.Add, "放大") }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BrushPanel(
    tool: CanvasTool,
    onTool: (CanvasTool) -> Unit,
    color: Color,
    onColor: (Color) -> Unit,
    width: Float,
    onWidth: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(300.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 14.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14000000)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("笔刷", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = onClose) { Text("完成") }
            }
            Row(Modifier.padding(top = 4.dp)) {
                ToolButton(tool, CanvasTool.PEN, Icons.Default.Edit, onTool)
                ToolButton(tool, CanvasTool.PENCIL, Icons.Default.Create, onTool)
                ToolButton(tool, CanvasTool.HIGHLIGHTER, Icons.Default.Brush, onTool)
            }
            Text("粗细", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Row { widths.forEach { w -> WidthDot(w, w == width, onWidth) } }
            Text("颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 4.dp)) {
                gridColors.forEach { c -> ColorSwatch(c, c == color, onColor) }
            }
        }
    }
}

@Composable
private fun ToolButton(current: CanvasTool, t: CanvasTool, icon: androidx.compose.ui.graphics.vector.ImageVector, onTool: (CanvasTool) -> Unit) {
    val selected = current == t
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .androidx_clickable { onTool(t) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, t.label,
            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PlainTool(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 1.dp).size(40.dp).clip(CircleShape).androidx_clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ToolDivider() {
    Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(22.dp).background(Color(0x1F000000)))
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

@Composable
private fun WidthDot(w: Float, selected: Boolean, onWidth: (Float) -> Unit) {
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .androidx_clickable { onWidth(w) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size((w / 2f + 3f).dp).background(MaterialTheme.colorScheme.onSurface, CircleShape))
    }
}

private fun Modifier.androidx_clickable(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }
