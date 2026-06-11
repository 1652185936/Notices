package com.yhx.notices.ui.canvas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
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
import kotlin.math.roundToInt

enum class CanvasTool(val label: String) {
    MOVE("移动"), SELECT("选择"), PEN("钢笔"), PENCIL("铅笔"), HIGHLIGHTER("荧光笔"), ERASER("橡皮"), TEXT("文字")
}

/** 返回元素包围盒 [x, y, w, h]，笔迹不可选返回 null。 */
private fun elementBounds(el: com.yhx.notices.domain.canvas.CanvasElement): FloatArray? = when (el) {
    is ImageElement -> floatArrayOf(el.x, el.y, el.width, el.height)
    is TextElement -> floatArrayOf(el.x, el.y, el.text.length.coerceAtLeast(2) * el.fontSize * 0.6f, el.fontSize * 1.4f)
    else -> null
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
        bottomBar = {
            CanvasToolbar(
                tool = tool, onTool = { tool = it },
                color = color, onColor = { color = it },
                width = width, onWidth = { width = it },
                scalePercent = (scale * 100).roundToInt(),
                onZoomIn = { scale = (scale * 1.25f).coerceAtMost(10f) },
                onZoomOut = { scale = (scale / 1.25f).coerceAtLeast(0.1f) },
                onReset = { scale = 1f; offset = Offset.Zero },
                onImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
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
                            editingId = viewModel.addText(w.x, w.y)
                        }
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
                                    if (tool == CanvasTool.ERASER) {
                                        viewModel.eraseStrokes(flat, 20f / scale)
                                    } else {
                                        viewModel.addStroke(
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
    onColor: (Color) -> Unit,
    width: Float,
    onWidth: (Float) -> Unit,
    scalePercent: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onImage: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                CanvasTool.entries.forEach { t ->
                    FilterChip(
                        selected = tool == t,
                        onClick = { onTool(t) },
                        label = { Text(t.label) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                IconButton(onClick = onImage) { Icon(Icons.Default.Image, "插入图片") }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                palette.forEach { c ->
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(if (c == color) 28.dp else 24.dp)
                            .background(c, CircleShape)
                            .androidx_clickable { onColor(c) }
                    )
                }
                Box(Modifier.size(8.dp))
                widths.forEach { w ->
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .size(30.dp)
                            .background(
                                if (w == width) Color(0x22007DFF) else Color.Transparent, CircleShape
                            )
                            .androidx_clickable { onWidth(w) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size((w / 2f + 4f).dp).background(Color(0xFF182431), CircleShape))
                    }
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = onZoomOut) { Icon(Icons.Default.Remove, "缩小") }
                Text(
                    "$scalePercent%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.androidx_clickable(onReset).padding(horizontal = 4.dp),
                )
                IconButton(onClick = onZoomIn) { Icon(Icons.Default.Add, "放大") }
            }
        }
    }
}

private fun Modifier.androidx_clickable(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }
