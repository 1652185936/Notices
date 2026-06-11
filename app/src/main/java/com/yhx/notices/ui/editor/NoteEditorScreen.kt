package com.yhx.notices.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yhx.notices.domain.richtext.AudioBlock
import com.yhx.notices.domain.richtext.Block
import com.yhx.notices.domain.richtext.ChecklistBlock
import com.yhx.notices.domain.richtext.DividerBlock
import com.yhx.notices.domain.richtext.ImageBlock
import com.yhx.notices.domain.richtext.SketchBlock
import com.yhx.notices.domain.richtext.Span
import com.yhx.notices.domain.richtext.SpanOps
import com.yhx.notices.domain.richtext.TableBlock
import com.yhx.notices.domain.richtext.TextBlock
import com.yhx.notices.domain.richtext.TextKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.insertImage(it) } }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startRecording() }

    val context = androidx.compose.ui.platform.LocalContext.current

    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { viewModel.insertImage(it) }
    }
    fun launchCamera() {
        val uri = createCameraUri(context)
        cameraUri = uri
        takePicture.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }
    var showSketch by remember { mutableStateOf(false) }
    var viewerPath by remember { mutableStateOf<String?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()

    // 语音转文字（系统 SpeechRecognizer）
    val recognizer = remember {
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(context))
            android.speech.SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }
    fun startVoice() {
        if (recognizer == null) {
            android.widget.Toast.makeText(context, "设备不支持语音识别", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: android.os.Bundle) {
                results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { viewModel.insertText(it) }
            }
            override fun onError(error: Int) {}
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        }
        android.widget.Toast.makeText(context, "请开始说话…", android.widget.Toast.LENGTH_SHORT).show()
        recognizer.startListening(intent)
    }
    val voicePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startVoice() }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { viewModel.onExit(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = viewModel.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                    }
                    IconButton(onClick = viewModel::redo, enabled = viewModel.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (viewModel.isPinned) "取消置顶" else "置顶") },
                            onClick = { viewModel.togglePin(); menuOpen = false },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (viewModel.isFavorite) "取消收藏" else "收藏") },
                            onClick = { viewModel.toggleFavorite(); menuOpen = false },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (viewModel.encrypted) "解除加密" else "加密") },
                            onClick = { viewModel.toggleEncryption(); menuOpen = false },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("分享为图片") },
                            onClick = {
                                menuOpen = false
                                viewModel.shareAsImage { uri -> uri?.let { shareUri(context, it, "image/png") } }
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("导出图片到相册") },
                            onClick = {
                                menuOpen = false
                                viewModel.exportImageToGallery { ok ->
                                    android.widget.Toast.makeText(
                                        context, if (ok) "已保存到相册" else "导出失败",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("导出 PDF") },
                            onClick = {
                                menuOpen = false
                                viewModel.exportPdf { uri -> uri?.let { shareUri(context, it, "application/pdf") } }
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("信纸样式") },
                            onClick = { viewModel.cycleSkin(); menuOpen = false },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = { menuOpen = false; viewModel.deleteNote(onBack) },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                FormatToolbar(viewModel)
                InsertBar(
                    onImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCamera = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) launchCamera()
                        else cameraPermission.launch(android.Manifest.permission.CAMERA)
                    },
                    onChecklist = viewModel::toggleChecklistKind,
                    onSketch = { showSketch = true },
                    onAudio = {
                        if (viewModel.isRecording) viewModel.stopRecordingAndInsert()
                        else audioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    onVoice = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) startVoice()
                        else voicePermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    onTable = viewModel::insertTable,
                    onDivider = viewModel::insertDivider,
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
        PaperBackground(viewModel.skin)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item(key = "title") {
                BasicTextField(
                    value = viewModel.title,
                    onValueChange = viewModel::onTitleChange,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (viewModel.title.isEmpty()) {
                            Text(
                                "标题",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
            item(key = "tags") {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    viewModel.tags.forEach { tag ->
                        androidx.compose.material3.AssistChip(
                            onClick = { viewModel.removeTag(tag.id) },
                            label = { Text("# ${tag.name}  ✕") },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    androidx.compose.material3.AssistChip(
                        onClick = { showTagDialog = true },
                        label = { Text("+ 标签") },
                    )
                }
            }
            items(viewModel.blocks, key = { it.id }) { block ->
                BlockRenderer(block, viewModel) { p -> viewerPath = p }
            }
        }
        }
    }

        if (showSketch) {
            SketchEditor(
                onCancel = { showSketch = false },
                onDone = { bmp -> viewModel.insertSketch(bmp); showSketch = false },
            )
        }

        if (viewModel.isRecording) {
            RecordingBar(
                onStop = { viewModel.stopRecordingAndInsert() },
                onCancel = { viewModel.cancelRecording() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (viewModel.isLocked) {
            LockScreen(onUnlock = viewModel::unlock, onBack = onBack)
        }

        if (showTagDialog) {
            TagDialog(
                allTags = allTags,
                selectedIds = viewModel.tags.map { it.id }.toSet(),
                onToggle = { id, selected -> if (selected) viewModel.removeTag(id) else viewModel.addTag(id) },
                onCreate = { viewModel.createAndAddTag(it) },
                onDismiss = { showTagDialog = false },
            )
        }

        viewerPath?.let { p ->
            ImageViewerOverlay(
                path = p,
                onClose = { viewerPath = null },
                onOcr = {
                    viewModel.ocr(p) { text ->
                        if (text.isBlank()) {
                            android.widget.Toast.makeText(context, "未识别到文字", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("ocr", text))
                            android.widget.Toast.makeText(context, "已识别并复制文字", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ImageViewerOverlay(path: String, onClose: () -> Unit, onOcr: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, offsetChange, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        pan += offsetChange
    }
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = path,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = pan.x, translationY = pan.y,
                    )
                    .transformable(state),
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
            androidx.compose.material3.TextButton(
                onClick = onOcr,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            ) {
                Text("提取文字", color = Color.White)
            }
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit, onBack: () -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.fragment.app.FragmentActivity
    fun auth() {
        activity?.let {
            com.yhx.notices.ui.security.BiometricAuth.authenticate(
                it, "笔记已加密", "请验证身份后查看",
                onSuccess = onUnlock, onError = {},
            )
        }
    }
    LaunchedEffect(Unit) { auth() }
    androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                "此笔记已加密",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            androidx.compose.material3.Button(
                onClick = { auth() },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("解锁查看") }
            androidx.compose.material3.TextButton(onClick = onBack) { Text("返回") }
        }
    }
}

@Composable
private fun RecordingBar(
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsed += 1
        }
    }
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 4.dp,
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape))
            Text(
                "  正在录音  %02d:%02d".format(elapsed / 60, elapsed % 60),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Box(Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onCancel) { Text("取消") }
            androidx.compose.material3.Button(onClick = onStop) { Text("完成") }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TagDialog(
    allTags: List<com.yhx.notices.data.local.entity.TagEntity>,
    selectedIds: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newTag by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签") },
        text = {
            Column {
                if (allTags.isEmpty()) {
                    Text("还没有标签，创建一个吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.foundation.layout.FlowRow {
                    allTags.forEach { tag ->
                        val sel = tag.id in selectedIds
                        androidx.compose.material3.FilterChip(
                            selected = sel,
                            onClick = { onToggle(tag.id, sel) },
                            label = { Text(tag.name) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                androidx.compose.foundation.layout.Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = newTag, onValueChange = { newTag = it },
                        singleLine = true, label = { Text("新建标签") },
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.TextButton(
                        onClick = { if (newTag.isNotBlank()) { onCreate(newTag.trim()); newTag = "" } },
                    ) { Text("创建") }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun PaperBackground(skin: String) {
    if (skin == "default") return
    val lineColor = Color(0x11000000)
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val spacing = 40.dp.toPx()
        when (skin) {
            "lines", "grid" -> {
                var y = spacing
                while (y < size.height) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f); y += spacing
                }
                if (skin == "grid") {
                    var x = spacing
                    while (x < size.width) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1f); x += spacing
                    }
                }
            }
            "dots" -> {
                var y = spacing
                while (y < size.height) {
                    var x = spacing
                    while (x < size.width) { drawCircle(lineColor, 2f, Offset(x, y)); x += spacing }
                    y += spacing
                }
            }
        }
    }
}

@Composable
private fun BlockRenderer(block: Block, vm: NoteEditorViewModel, onViewImage: (String) -> Unit) {
    when (block) {
        is TextBlock -> EditableText(block, vm)
        is ChecklistBlock -> ChecklistRow(block, vm)
        is ImageBlock -> AttachmentImageView(block.attachmentId, vm, onViewImage)
        is DividerBlock -> HorizontalDivider(Modifier.padding(vertical = 12.dp))
        is AudioBlock -> AudioBlockView(block, vm)
        is SketchBlock -> AttachmentImageView(block.attachmentId, vm, onViewImage)
        is TableBlock -> TableView(block, vm)
    }
}

@Composable
private fun EditableText(block: TextBlock, vm: NoteEditorViewModel) {
    val focusRequester = remember { FocusRequester() }
    var tfv by remember(block.id) {
        mutableStateOf(TextFieldValue(spansToAnnotated(block.spans)))
    }
    // 块 spans 外部变化时（样式/撤销/合块）重建 annotated，保留选区
    LaunchedEffect(block.spans) {
        val ann = spansToAnnotated(block.spans)
        if (ann.text != tfv.text || ann != tfv.annotatedString) {
            val len = ann.text.length
            tfv = tfv.copy(
                annotatedString = ann,
                selection = TextRange(tfv.selection.start.coerceIn(0, len)),
            )
        }
    }
    LaunchedEffect(vm.focusedBlockId) {
        if (vm.focusedBlockId == block.id) runCatching { focusRequester.requestFocus() }
    }

    val style = textKindStyle(block.kind)
    val prefix = listPrefix(block, vm)

    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (prefix != null) {
            Text(prefix, style = style, color = MaterialTheme.colorScheme.onBackground)
        }
        BasicTextField(
            value = tfv,
            onValueChange = { nv ->
                if (nv.text.contains('\n')) {
                    val nl = nv.text.indexOf('\n')
                    vm.onEnter(block.id, nl)
                    return@BasicTextField
                }
                vm.onTextChange(block.id, nv.text, nv.selection)
                val updated = vm.blocks.firstOrNull { it.id == block.id } as? TextBlock
                tfv = if (updated != null) {
                    TextFieldValue(spansToAnnotated(updated.spans), nv.selection)
                } else nv
            },
            textStyle = style.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) vm.onFocus(block.id, tfv.selection) }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown &&
                        e.key == Key.Backspace &&
                        tfv.selection.collapsed && tfv.selection.start == 0
                    ) {
                        vm.onBackspaceAtStart(block.id); true
                    } else false
                },
        )
    }
}

@Composable
private fun ChecklistRow(block: ChecklistBlock, vm: NoteEditorViewModel) {
    val focusRequester = remember { FocusRequester() }
    var tfv by remember(block.id) {
        mutableStateOf(TextFieldValue(spansToAnnotated(block.spans)))
    }
    LaunchedEffect(block.spans) {
        val ann = spansToAnnotated(block.spans)
        if (ann.text != tfv.text) tfv = tfv.copy(annotatedString = ann)
    }
    LaunchedEffect(vm.focusedBlockId) {
        if (vm.focusedBlockId == block.id) runCatching { focusRequester.requestFocus() }
    }
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = block.checked, onCheckedChange = { vm.toggleChecked(block.id) })
        val baseStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onBackground,
            textDecoration = if (block.checked) TextDecoration.LineThrough else null,
        )
        BasicTextField(
            value = tfv,
            onValueChange = { nv ->
                if (nv.text.contains('\n')) {
                    vm.onEnter(block.id, nv.text.indexOf('\n')); return@BasicTextField
                }
                vm.onTextChange(block.id, nv.text, nv.selection)
                val updated = vm.blocks.firstOrNull { it.id == block.id } as? ChecklistBlock
                tfv = if (updated != null) TextFieldValue(spansToAnnotated(updated.spans), nv.selection) else nv
            },
            textStyle = baseStyle,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) vm.onFocus(block.id, tfv.selection) }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace &&
                        tfv.selection.collapsed && tfv.selection.start == 0
                    ) { vm.onBackspaceAtStart(block.id); true } else false
                },
        )
    }
}

@Composable
private fun AttachmentImageView(attachmentId: Long, vm: NoteEditorViewModel, onView: (String) -> Unit) {
    var path by remember(attachmentId) { mutableStateOf<String?>(null) }
    LaunchedEffect(attachmentId) { path = vm.attachmentPath(attachmentId) }
    AsyncImage(
        model = path,
        contentDescription = "图片",
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x08000000))
            .clickable { path?.let(onView) },
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
    )
}

@Composable
private fun AudioBlockView(block: AudioBlock, vm: NoteEditorViewModel) {
    var info by remember(block.attachmentId) { mutableStateOf<AudioInfo?>(null) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(block.attachmentId) { info = vm.audioInfo(block.attachmentId) }

    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                playing = true
                vm.playAudio(block.attachmentId) { playing = false }
            }) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "录音  " + formatDuration(info?.durationMs ?: 0),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun TableView(block: TableBlock, vm: NoteEditorViewModel) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22000000)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            block.rows.forEachIndexed { r, row ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { c, cell ->
                        BasicTextField(
                            value = cell,
                            onValueChange = { vm.updateTableCell(block.id, r, c, it) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .padding(8.dp),
                        )
                        if (c < row.size - 1) {
                            Box(Modifier.width(1.dp).height(36.dp).background(Color(0x22000000)))
                        }
                    }
                }
                if (r < block.rows.size - 1) HorizontalDivider(thickness = 1.dp, color = Color(0x22000000))
            }
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().background(Color(0x06000000)),
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.TextButton(onClick = { vm.tableAddRow(block.id) }) { Text("+ 行") }
                androidx.compose.material3.TextButton(onClick = { vm.tableAddColumn(block.id) }) { Text("+ 列") }
            }
        }
    }
}

@Composable
private fun textKindStyle(kind: TextKind) = when (kind) {
    TextKind.H1 -> MaterialTheme.typography.headlineLarge
    TextKind.H2 -> MaterialTheme.typography.headlineMedium
    TextKind.H3 -> MaterialTheme.typography.headlineSmall
    TextKind.QUOTE -> MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    else -> MaterialTheme.typography.bodyLarge
}

@Composable
private fun listPrefix(block: TextBlock, vm: NoteEditorViewModel): String? = when (block.kind) {
    TextKind.BULLET -> "•  "
    TextKind.NUMBERED -> "${numberedIndex(block, vm)}.  "
    TextKind.QUOTE -> "丨  "
    else -> null
}

private fun createCameraUri(context: android.content.Context): android.net.Uri {
    val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
    val file = java.io.File(dir, "cam_${System.currentTimeMillis()}.jpg")
    return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

internal fun shareUri(context: android.content.Context, uri: android.net.Uri, mime: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = mime
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "分享"))
}

private fun numberedIndex(block: TextBlock, vm: NoteEditorViewModel): Int {
    var count = 1
    for (b in vm.blocks) {
        if (b.id == block.id) break
        if (b is TextBlock && b.kind == TextKind.NUMBERED) count++ else if (b is TextBlock && b.kind != TextKind.NUMBERED) count = 1
    }
    return count
}
