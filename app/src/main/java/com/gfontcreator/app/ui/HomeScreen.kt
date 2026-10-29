package com.gfontcreator.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gfontcreator.app.data.FontProject
import com.gfontcreator.app.data.ProjectStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主页：项目总览 + 新建 / 继续 / 删除 / 生成导出
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    projects: List<FontProject>,
    writtenCounts: Map<String, Int>,
    onCreateProject: (String, Boolean, generateNow: Boolean) -> Unit,
    onOpenWriting: (String) -> Unit,
    onOpenExport: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var quick by remember { mutableStateOf(true) }
    var generateNow by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FontProject?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "字",
                                fontFamily = FontFamily.Serif,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("手写字体工坊", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "GFontCreator · v1.1.0",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("新建字体") },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HeroCard() }

            if (projects.isEmpty()) {
                item { EmptyState(onCreate = { showCreate = true }) }
            } else {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("我的字体项目", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${projects.size} 个",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(projects, key = { it.projectId }) { project ->
                    ProjectCard(
                        project = project,
                        written = writtenCounts[project.projectId] ?: 0,
                        onClick = {
                            if (project.status == ProjectStatus.COMPLETED) onOpenExport(project.projectId)
                            else onOpenWriting(project.projectId)
                        },
                        onWrite = { onOpenWriting(project.projectId) },
                        onExport = { onOpenExport(project.projectId) },
                        onDelete = { deleteTarget = project },
                    )
                }
                item {
                    Text(
                        "支持导出 TTF 通用字体与 WRobot 写字机单线字库（.gfont）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateProjectDialog(
            name = name,
            onNameChange = { name = it },
            quick = quick,
            onQuickChange = { quick = it },
            generateNow = generateNow,
            onGenerateNowChange = { generateNow = it },
            onConfirm = {
                onCreateProject(name, quick, generateNow)
                showCreate = false
                name = ""
                generateNow = false
            },
            onDismiss = { showCreate = false },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除项目") },
            text = { Text("确定删除「${target.name}」？其全部手写数据与生成文件将一并删除，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProject(target.projectId)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun HeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                "字",
                fontFamily = FontFamily.Serif,
                fontSize = 168.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 22.dp, y = (-14).dp),
            )
            Column(Modifier.padding(22.dp)) {
                Text(
                    "把笔迹，变成字体",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "手写采集 · 风格合成 · 一键导出完整字库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroTag("TTF 通用字体")
                    HeroTag("WRobot .gfont")
                }
            }
        }
    }
}

@Composable
private fun HeroTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
        shape = CircleShape,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "字",
                    fontFamily = FontFamily.Serif,
                    fontSize = 42.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("还没有字体项目", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "先写 100 个字，就能生成一版完整字库；\n没写过的字会按你的笔迹风格自动合成。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建第一个项目")
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: FontProject,
    written: Int,
    onClick: () -> Unit,
    onWrite: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = (written.toFloat() / project.refSetTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(progress, label = "projectProgress")
    val completed = project.status == ProjectStatus.COMPLETED

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        project.name.take(1).ifBlank { "字" },
                        fontFamily = FontFamily.Serif,
                        fontSize = 27.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        project.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "已写 $written / ${project.refSetTarget} 字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(project.status)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = if (completed) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "完成度 ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(project.updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onWrite, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (completed) "重新书写" else "继续书写")
                }
                OutlinedButton(onClick = onExport, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("生成导出")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "删除项目",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: ProjectStatus) {
    val (label, color) = when (status) {
        ProjectStatus.COLLECTING -> "采集中" to MaterialTheme.colorScheme.secondary
        ProjectStatus.DRAFT -> "草稿" to MaterialTheme.colorScheme.onSurfaceVariant
        ProjectStatus.MODELING -> "建模中" to MaterialTheme.colorScheme.tertiary
        ProjectStatus.GENERATING -> "生成中" to MaterialTheme.colorScheme.tertiary
        ProjectStatus.COMPLETED -> "已完成" to MaterialTheme.colorScheme.primary
        ProjectStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
    }
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun CreateProjectDialog(
    name: String,
    onNameChange: (String) -> Unit,
    quick: Boolean,
    onQuickChange: (Boolean) -> Unit,
    generateNow: Boolean,
    onGenerateNowChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建字体项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("字体名称") },
                    placeholder = { Text("我的手写字体") },
                    supportingText = { Text("留空则使用「我的手写字体」") },
                    singleLine = true,
                )
                Text("采集模式", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = quick,
                        onClick = { onQuickChange(true) },
                        label = { Text("快速 · 100 字") },
                        leadingIcon = if (quick) {
                            { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) }
                        } else {
                            { Icon(Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(16.dp)) }
                        },
                    )
                    FilterChip(
                        selected = !quick,
                        onClick = { onQuickChange(false) },
                        label = { Text("完整 · 775 字") },
                        leadingIcon = if (!quick) {
                            { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) }
                        } else {
                            { Icon(Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(16.dp)) }
                        },
                    )
                }
                Text(
                    if (quick) "先写 100 字生成初版，之后可随时继续补充。"
                    else "按参考字集写满 775 字，合成分子的风格更稳定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onGenerateNowChange(!generateNow) },
                ) {
                    Checkbox(checked = generateNow, onCheckedChange = onGenerateNowChange)
                    Text("创建后立即开始生成字体", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
