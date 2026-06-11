package com.yhx.notices.ui.notes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yhx.notices.data.local.dao.FolderWithCount
import com.yhx.notices.data.local.entity.FolderEntity
import com.yhx.notices.domain.model.ListLayout
import com.yhx.notices.domain.model.NoteListItem
import com.yhx.notices.domain.model.NoteSort
import com.yhx.notices.ui.icons.HwIcons
import kotlinx.coroutines.launch

private val folderColors = listOf(
    0xFF007DFF, 0xFFFA2A2D, 0xFFFF7500, 0xFF21A675, 0xFF4C2FBF, 0xFFFFBB00, 0xFF8E8E93,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotesListScreen(
    onOpenNote: (Long) -> Unit,
    onOpenCanvas: (Long) -> Unit = {},
    onSearch: () -> Unit = {},
    viewModel: NoteListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val inSelection = state.selection.isNotEmpty()

    var showCreateFolder by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<FolderEntity?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("记事本", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Folder, null) },
                    label = { Text("全部笔记") },
                    selected = state.currentFolderId == null,
                    onClick = { viewModel.selectFolder(null); scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                folderTree(state.folders).forEach { (f, depth) ->
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Folder, null) },
                        label = { Text("${f.folder.name}  (${f.noteCount})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = state.currentFolderId == f.folder.id,
                        badge = {
                            FolderRowMenu(
                                onRename = { renamingFolder = f.folder },
                                onRecolor = {
                                    val next = folderColors[(folderColors.indexOf(f.folder.color) + 1).mod(folderColors.size)]
                                    viewModel.recolorFolder(f.folder, next)
                                },
                                onDelete = { viewModel.deleteFolder(f.folder) },
                            )
                        },
                        onClick = { viewModel.selectFolder(f.folder.id); scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(start = (12 + depth * 16).dp, end = 12.dp),
                    )
                }
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CreateNewFolder, null) },
                    label = { Text("新建文件夹") },
                    selected = false,
                    onClick = { showCreateFolder = true },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (inSelection) {
                    // 多选操作栏
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = viewModel::clearSelection) {
                                Icon(Icons.Default.Close, contentDescription = "取消")
                            }
                            Text(
                                "已选 ${state.selection.size} 项",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = viewModel::pinSelected) { Icon(Icons.Default.PushPin, "置顶") }
                            IconButton(onClick = viewModel::favoriteSelected) { Icon(Icons.Default.Star, "收藏") }
                            IconButton(onClick = { showMoveDialog = true }) { Icon(Icons.Default.DriveFileMove, "移动") }
                            IconButton(onClick = {
                                viewModel.deleteSelected { count ->
                                    scope.launch {
                                        val r = snackbar.showSnackbar("已移入回收站 $count 项", "撤销")
                                        if (r == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                                    }
                                }
                            }) { Icon(Icons.Default.Delete, "删除") }
                        }
                    }
                } else {
                    HwHomeHeader(
                        title = folderName(state),
                        count = state.notes.size,
                        layout = state.layout,
                        sort = state.sort,
                        onMenu = { scope.launch { drawerState.open() } },
                        onSearch = onSearch,
                        onToggleLayout = viewModel::toggleLayout,
                        onSort = viewModel::setSort,
                        sortMenuOpen = showSortMenu,
                        onSortMenu = { showSortMenu = it },
                    )
                }
            },
            floatingActionButton = {
                if (!inSelection) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (fabExpanded) {
                            FabOption("无界笔记", HwIcons.Pen) {
                                fabExpanded = false
                                scope.launch { onOpenCanvas(viewModel.createCanvasNote()) }
                            }
                            FabOption("笔记", HwIcons.TextBox) {
                                fabExpanded = false
                                scope.launch { onOpenNote(viewModel.createNote()) }
                            }
                        }
                        // 主按钮：华为蓝圆形，展开时 + 旋转为 ×
                        val rotation by animateFloatAsState(if (fabExpanded) 45f else 0f, label = "fabRotate")
                        Surface(
                            onClick = { fabExpanded = !fabExpanded },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    HwIcons.Add, contentDescription = "新建", tint = Color.White,
                                    modifier = Modifier.size(26.dp).rotate(rotation),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            if (state.notes.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            HwIcons.NoteBadge, null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp),
                        )
                        Text(
                            "暂无笔记",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Text(
                            "点击 + 新建第一篇笔记",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else if (state.layout == ListLayout.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 172.dp),
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.notes, key = { it.id }) { note -> noteCardFor(note, state, viewModel, onOpenNote, onOpenCanvas) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.notes, key = { it.id }) { note -> noteCardFor(note, state, viewModel, onOpenNote, onOpenCanvas) }
                }
            }
        }
    }

    if (showCreateFolder) CreateFolderDialog(state, viewModel) { showCreateFolder = false }
    if (showMoveDialog) {
        MoveToFolderDialog(state.folders, onPick = { viewModel.moveSelectedToFolder(it); showMoveDialog = false }) { showMoveDialog = false }
    }
    renamingFolder?.let { f ->
        RenameFolderDialog(f, onRename = { viewModel.renameFolder(f, it); renamingFolder = null }) { renamingFolder = null }
    }
}

@Composable
private fun FolderRowMenu(onRename: () -> Unit, onRecolor: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "文件夹操作") }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("重命名") }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text("换颜色") }, onClick = { open = false; onRecolor() })
            DropdownMenuItem(text = { Text("删除文件夹") }, onClick = { open = false; onDelete() })
        }
    }
}

private fun folderName(state: NoteListUiState): String =
    state.folders.firstOrNull { it.folder.id == state.currentFolderId }?.folder?.name ?: "全部笔记"

private fun folderTree(folders: List<FolderWithCount>): List<Pair<FolderWithCount, Int>> {
    val byParent = folders.groupBy { it.folder.parentId }
    val result = ArrayList<Pair<FolderWithCount, Int>>()
    fun dfs(parentId: Long?, depth: Int) {
        byParent[parentId]?.sortedBy { it.folder.sortOrder }?.forEach {
            result.add(it to depth); dfs(it.folder.id, depth + 1)
        }
    }
    dfs(null, 0)
    return result
}

@Composable
private fun sortItem(label: String, value: NoteSort, current: NoteSort, onPick: (NoteSort) -> Unit) {
    DropdownMenuItem(
        text = { Text((if (value == current) "✓ " else "    ") + label) },
        onClick = { onPick(value) },
    )
}

@Composable
private fun noteCardFor(
    note: NoteListItem,
    state: NoteListUiState,
    vm: NoteListViewModel,
    onOpenNote: (Long) -> Unit,
    onOpenCanvas: (Long) -> Unit,
) {
    NoteCard(
        note = note,
        selected = note.id in state.selection,
        onClick = {
            when {
                state.selection.isNotEmpty() -> vm.toggleSelect(note.id)
                note.isCanvas -> onOpenCanvas(note.id)
                else -> onOpenNote(note.id)
            }
        },
        onLongClick = { vm.toggleSelect(note.id) },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteListItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.title.ifBlank { "无标题" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (note.isFavorite) {
                    Icon(
                        HwIcons.StarFilled, "收藏", tint = Color(0xFFFFBB00),
                        modifier = Modifier.padding(start = 4.dp).size(15.dp),
                    )
                }
            }
            if (note.firstImagePath != null) {
                AsyncImage(
                    model = java.io.File(
                        androidx.compose.ui.platform.LocalContext.current.filesDir, note.firstImagePath
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp)).background(Color(0x11000000)),
                )
            }
            when {
                note.isCanvas -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Icon(HwIcons.Pen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                    Text(
                        "无界笔记", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                note.isEncrypted -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Icon(HwIcons.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    Text(
                        "已加密", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                note.excerpt.isNotBlank() -> Text(
                    note.excerpt, fontSize = 13.sp, lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    formatNoteDate(note.updatedAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                if (note.isPinned) {
                    Icon(
                        Icons.Default.PushPin, "置顶",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

/** 笔记卡片日期：当年显示「M月d日」，跨年带年份。 */
private fun formatNoteDate(ts: Long): String {
    val now = java.util.Calendar.getInstance()
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val md = "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
    return if (cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)) md
    else "${cal.get(java.util.Calendar.YEAR)}年$md"
}

/** 首页头部：汉堡菜单 + 视图/排序，大标题 + 数量，搜索条（华为平板版式）。 */
@Composable
private fun HwHomeHeader(
    title: String,
    count: Int,
    layout: ListLayout,
    sort: NoteSort,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onToggleLayout: () -> Unit,
    onSort: (NoteSort) -> Unit,
    sortMenuOpen: Boolean,
    onSortMenu: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu) {
                Icon(HwIcons.Menu, "文件夹", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleLayout) {
                Icon(
                    if (layout == ListLayout.GRID) HwIcons.ViewList else HwIcons.ViewGrid,
                    "视图", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(21.dp),
                )
            }
            Box {
                IconButton(onClick = { onSortMenu(true) }) {
                    Icon(HwIcons.Sort, "排序", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(21.dp))
                }
                DropdownMenu(sortMenuOpen, { onSortMenu(false) }) {
                    sortItem("按修改时间", NoteSort.UPDATED, sort) { onSort(it); onSortMenu(false) }
                    sortItem("按创建时间", NoteSort.CREATED, sort) { onSort(it); onSortMenu(false) }
                    sortItem("按标题", NoteSort.TITLE, sort) { onSort(it); onSortMenu(false) }
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "$count 个笔记",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // 搜索条（点击进入搜索页）
        Surface(
            onClick = onSearch,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
                Icon(HwIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Text(
                    "搜索笔记", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** FAB 展开后的选项（白底圆角胶囊：图标 + 文字）。 */
@Composable
private fun FabOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CreateFolderDialog(state: NoteListUiState, viewModel: NoteListViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val currentFolder = state.folders.firstOrNull { it.folder.id == state.currentFolderId }?.folder
    var asChild by remember { mutableStateOf(currentFolder != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("文件夹名") })
                if (currentFolder != null) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(asChild, { asChild = it })
                        Text("作为「${currentFolder.name}」的子文件夹")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) viewModel.createFolder(name.trim(), if (asChild) currentFolder?.id else null)
                onDismiss()
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RenameFolderDialog(folder: FolderEntity, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(folder.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名文件夹") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("文件夹名") }) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onRename(name.trim()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MoveToFolderDialog(
    folders: List<FolderWithCount>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到") },
        text = {
            Column {
                Text("未分类", modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp))
                folderTree(folders).forEach { (f, depth) ->
                    Text(
                        f.folder.name,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(f.folder.id) }
                            .padding(start = (depth * 16).dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

