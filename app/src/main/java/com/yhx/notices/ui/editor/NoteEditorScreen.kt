package com.yhx.notices.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
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
                    onChecklist = viewModel::toggleChecklistKind,
                    onDivider = { /* 预留：插入分割线 */ },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
            items(viewModel.blocks, key = { it.id }) { block ->
                BlockRenderer(block, viewModel)
            }
        }
    }
}

@Composable
private fun BlockRenderer(block: Block, vm: NoteEditorViewModel) {
    when (block) {
        is TextBlock -> EditableText(block, vm)
        is ChecklistBlock -> ChecklistRow(block, vm)
        is ImageBlock -> ImageBlockView(block, vm)
        is DividerBlock -> HorizontalDivider(Modifier.padding(vertical = 12.dp))
        is AudioBlock -> PlaceholderBlock("🎙 录音")
        is SketchBlock -> PlaceholderBlock("✏ 手写")
        is TableBlock -> TableView(block)
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
private fun ImageBlockView(block: ImageBlock, vm: NoteEditorViewModel) {
    var path by remember(block.attachmentId) { mutableStateOf<String?>(null) }
    LaunchedEffect(block.attachmentId) {
        path = vm.attachmentPath(block.attachmentId)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        AsyncImage(
            model = path,
            contentDescription = "图片",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color(0x11000000), RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
private fun PlaceholderBlock(label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color(0x11000000), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun TableView(block: TableBlock) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        block.rows.forEach { row ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    Text(
                        cell.ifEmpty { " " },
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            HorizontalDivider()
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

private fun numberedIndex(block: TextBlock, vm: NoteEditorViewModel): Int {
    var count = 1
    for (b in vm.blocks) {
        if (b.id == block.id) break
        if (b is TextBlock && b.kind == TextKind.NUMBERED) count++ else if (b is TextBlock && b.kind != TextKind.NUMBERED) count = 1
    }
    return count
}
