package com.yhx.notices.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yhx.notices.ui.canvas.CanvasScreen
import com.yhx.notices.ui.editor.NoteEditorScreen
import com.yhx.notices.ui.icons.HwIcons
import com.yhx.notices.ui.notes.NotesListScreen
import com.yhx.notices.ui.search.SearchScreen
import com.yhx.notices.ui.settings.SettingsScreen
import com.yhx.notices.ui.todo.TodoScreen
import com.yhx.notices.ui.trash.TrashScreen

object Routes {
    const val NOTES = "notes"
    const val TODO = "todo"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val TRASH = "trash"
    const val EDITOR = "editor/{noteId}"
    const val CANVAS = "canvas/{noteId}"
    fun editor(noteId: Long) = "editor/$noteId"
    fun canvas(noteId: Long) = "canvas/$noteId"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs by lazy {
    listOf(
        BottomTab(Routes.NOTES, "笔记", HwIcons.NoteBadge),
        BottomTab(Routes.TODO, "待办", HwIcons.TodoCheck),
        BottomTab(Routes.SETTINGS, "我的", HwIcons.Person),
    )
}

@Composable
fun NoticesNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomTabs.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(22.dp)) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.NOTES,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.NOTES) {
                NotesListScreen(
                    onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                    onOpenCanvas = { id -> navController.navigate(Routes.canvas(id)) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.TODO) { TodoScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenTrash = { navController.navigate(Routes.TRASH) })
            }
            composable(Routes.TRASH) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            ) {
                NoteEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.CANVAS,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            ) {
                CanvasScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
