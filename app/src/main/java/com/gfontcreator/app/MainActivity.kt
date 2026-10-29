package com.gfontcreator.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gfontcreator.app.data.AppDatabase
import com.gfontcreator.app.ui.ExportScreen
import com.gfontcreator.app.ui.FontProjectViewModel
import com.gfontcreator.app.ui.HomeScreen
import com.gfontcreator.app.ui.WritingScreen
import com.gfontcreator.app.ui.theme.GFontTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val charset = assets.open("charset_6900.txt").bufferedReader().readText()
            .filter { !it.isWhitespace() }
            .map { it }

        setContent {
            GFontTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppContent(charset)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppContent(charset: List<Char>) {
        val vm: FontProjectViewModel = viewModel()
        val projects by vm.projects.collectAsState(initial = emptyList())
        val scope = rememberCoroutineScope()

        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        val db = remember { AppDatabase.get(this) }

        suspend fun writtenSet(projectId: String): Set<Int> =
            db.charStrokesDao().getAll(projectId).map { it.codepoint }.toSet()

        when (val s = screen) {
            is Screen.Home -> {
                // 状态不随 projects 重置（否则每次项目列表变化都会闪回 0），仅在列表变化时重新加载
                var writtenCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
                LaunchedEffect(projects) {
                    writtenCounts = vm.writtenCounts(projects.map { it.projectId })
                }
                HomeScreen(
                    projects = projects,
                    writtenCounts = writtenCounts,
                    onCreateProject = { name, quick, genNow -> vm.createProject(name, quick, genNow) },
                    onOpenWriting = { id -> screen = Screen.Writing(id, 0) },
                    onOpenExport = { id -> screen = Screen.Export(id) },
                    onDeleteProject = { id -> vm.deleteProject(id) },
                )
            }
            is Screen.Writing -> {
                val project = projects.firstOrNull { it.projectId == s.projectId }
                // 仅按 projectId 初始化/加载：projects 列表每次保存都会因 updatedAt 变化而重新发射，
                // 若把它放进 remember 的键，written 会在每次保存后被重置为空集，导致「已完成字数」闪回 0
                var written by remember(s.projectId) { mutableStateOf<Set<Int>>(emptySet()) }
                LaunchedEffect(s.projectId) {
                    written = writtenSet(s.projectId).toSet()
                }
                WritingScreen(
                    charset = charset,
                    refSetSize = project?.refSetTarget ?: 775,
                    writtenCodepoints = written,
                    initialIndex = s.startIndex,
                    onBack = { screen = Screen.Home },
                    onSave = { cp, sp, inRefSet, onSaved ->
                        scope.launch {
                            vm.saveStrokes(s.projectId, sp, inRefSet)
                            written = written + cp
                            onSaved()
                        }
                    },
                )
            }
            is Screen.Export -> {
                val project = projects.firstOrNull { it.projectId == s.projectId }
                // 同书写页：不要以 projects 为键，避免生成任务触发 projects 重发射时计数闪回 0
                var count by remember(s.projectId) { mutableStateOf(0) }
                LaunchedEffect(s.projectId) {
                    count = db.charStrokesDao().getAll(s.projectId).size
                }
                ExportScreen(
                    projectId = s.projectId,
                    projectName = project?.name ?: "未命名",
                    writtenCount = count,
                    existingTtfPath = project?.generatedFontPath,
                    existingGfontPath = project?.generatedGfontPath,
                    onBack = { screen = Screen.Home },
                )
            }
        }
    }

    private sealed class Screen {
        object Home : Screen()
        data class Writing(val projectId: String, val startIndex: Int) : Screen()
        data class Export(val projectId: String) : Screen()
    }
}
