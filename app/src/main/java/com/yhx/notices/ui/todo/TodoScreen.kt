package com.yhx.notices.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhx.notices.data.local.entity.TodoEntity
import com.yhx.notices.domain.model.RepeatRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val expanded = remember { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    var editing by remember { mutableStateOf<TodoEntity?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("待办") }) }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text("添加待办…") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.add(input); input = "" }) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                val g = ui.groups
                group("已逾期", g.overdue, viewModel, ui, expanded) { editing = it }
                group("今天", g.today, viewModel, ui, expanded) { editing = it }
                group("未来", g.upcoming, viewModel, ui, expanded) { editing = it }
                group("无日期", g.noDate, viewModel, ui, expanded) { editing = it }
                group("已完成", g.done, viewModel, ui, expanded) { editing = it }
            }
        }
    }

    editing?.let { todo ->
        TodoEditDialog(
            todo = todo,
            onSave = { content, due, repeat -> viewModel.updateTodo(todo, content, due, repeat); editing = null },
            onDelete = { viewModel.delete(todo); editing = null },
            onDismiss = { editing = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(
    title: String,
    entries: List<TodoEntity>,
    vm: TodoViewModel,
    ui: TodoUiState,
    expanded: androidx.compose.runtime.snapshots.SnapshotStateMap<Long, Boolean>,
    onEdit: (TodoEntity) -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "h_$title") {
        Text(
            title, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }
    items(entries, key = { it.id }) { todo ->
        val children = ui.childrenByParent[todo.id].orEmpty()
        TodoRow(
            todo = todo, children = children,
            isExpanded = expanded[todo.id] == true,
            onToggleExpand = { expanded[todo.id] = !(expanded[todo.id] ?: false) },
            vm = vm, onEdit = { onEdit(todo) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoRow(
    todo: TodoEntity,
    children: List<TodoEntity>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    vm: TodoViewModel,
    onEdit: () -> Unit,
) {
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { vm.delete(todo); true } else false
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
        },
    ) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = todo.isDone, onCheckedChange = { vm.toggle(todo) })
                Column(Modifier.weight(1f)) {
                    Text(
                        todo.content, style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                        color = if (todo.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onBackground,
                    )
                    Row {
                        todo.dueAt?.let {
                            Text(
                                "⏰ " + SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(it)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (todo.repeatRule != RepeatRule.NONE) {
                            Text(
                                "  🔁 " + repeatLabel(todo.repeatRule),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (children.isNotEmpty()) {
                            Text(
                                "  ${children.count { it.isDone }}/${children.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "子任务")
                }
            }
            if (isExpanded) {
                children.forEach { sub ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 40.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = sub.isDone, onCheckedChange = { vm.toggle(sub) })
                        Text(
                            sub.content, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (sub.isDone) TextDecoration.LineThrough else null,
                            color = if (sub.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onBackground,
                        )
                        IconButton(onClick = { vm.delete(sub) }) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                var sub by remember { mutableStateOf("") }
                Row(
                    Modifier.fillMaxWidth().padding(start = 40.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = sub, onValueChange = { sub = it },
                        placeholder = { Text("添加子任务…") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.addSubtask(todo.id, sub); sub = "" }) {
                        Icon(Icons.Default.Add, "添加子任务")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TodoEditDialog(
    todo: TodoEntity,
    onSave: (String, Long?, RepeatRule) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var content by remember { mutableStateOf(todo.content) }
    var due by remember { mutableStateOf(todo.dueAt) }
    var repeat by remember { mutableStateOf(todo.repeatRule) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑待办") },
        text = {
            Column {
                OutlinedTextField(content, { content = it }, label = { Text("内容") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        due?.let { "时间：" + SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(it)) } ?: "无提醒时间",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pickDateTime(context) { due = it } }) { Text("设置") }
                    if (due != null) TextButton(onClick = { due = null }) { Text("清除") }
                }
                Text("重复", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                androidx.compose.foundation.layout.FlowRow {
                    RepeatRule.entries.forEach { r ->
                        FilterChip(
                            selected = repeat == r, onClick = { repeat = r },
                            label = { Text(repeatLabel(r)) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(content.trim().ifEmpty { todo.content }, due, repeat) }) { Text("保存") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

private fun repeatLabel(r: RepeatRule) = when (r) {
    RepeatRule.NONE -> "不重复"; RepeatRule.DAILY -> "每天"; RepeatRule.WEEKLY -> "每周"
    RepeatRule.MONTHLY -> "每月"; RepeatRule.YEARLY -> "每年"; RepeatRule.WEEKDAYS -> "工作日"
}

private fun pickDateTime(context: android.content.Context, onPicked: (Long) -> Unit) {
    val now = java.util.Calendar.getInstance()
    android.app.DatePickerDialog(
        context,
        { _, year, month, day ->
            android.app.TimePickerDialog(
                context,
                { _, hour, minute ->
                    val cal = java.util.Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0); set(java.util.Calendar.MILLISECOND, 0)
                    }
                    onPicked(cal.timeInMillis)
                },
                now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true,
            ).show()
        },
        now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH), now.get(java.util.Calendar.DAY_OF_MONTH),
    ).show()
}
