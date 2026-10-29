package com.gfontcreator.app.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gfontcreator.app.font.PublicFontStore
import com.gfontcreator.app.work.FontGenerationWorker
import java.time.Duration

/**
 * 生成与导出页：一键生成 TTF 通用字体 + WRobot 单线字库（.gfont）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: String,
    projectName: String,
    writtenCount: Int,
    existingTtfPath: String?,
    existingGfontPath: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }
    var running by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var progressPercent by remember { mutableStateOf(0f) }
    var ttfPath by remember { mutableStateOf(existingTtfPath) }
    var gfontPath by remember { mutableStateOf(existingGfontPath) }

    val workInfos by workManager.getWorkInfosByTagFlow(projectId)
        .collectAsState(initial = emptyList())

    LaunchedEffect(workInfos) {
        val active = workInfos.firstOrNull {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }
        if (active != null) {
            running = true
            progressText = active.progress.getString(FontGenerationWorker.KEY_PROGRESS)
                ?: "已加入生成队列…"
            progressPercent = active.progress.getInt(FontGenerationWorker.KEY_PERCENT, 0) / 100f
            return@LaunchedEffect
        }
        if (workInfos.isEmpty()) return@LaunchedEffect

        val success = workInfos.lastOrNull { it.state == WorkInfo.State.SUCCEEDED }
        if (success != null) {
            success.outputData.getString(FontGenerationWorker.KEY_TTF_PATH)?.let { ttfPath = it }
            success.outputData.getString(FontGenerationWorker.KEY_GFONT_PATH)?.let { gfontPath = it }
            running = false
            progressPercent = 1f
            progressText = "生成完成"
            return@LaunchedEffect
        }
        val failed = workInfos.lastOrNull { it.state == WorkInfo.State.FAILED }
        if (failed != null) {
            running = false
            progressText = failed.outputData.getString(FontGenerationWorker.KEY_PROGRESS)
                ?: "生成失败，请重试"
        }
    }

    val hasOutput = !ttfPath.isNullOrBlank() || !gfontPath.isNullOrBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("生成与导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    projectName,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "已手写 $writtenCount 个字 · 未写的字按你的笔迹风格合成完整字库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Brush,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    running -> "正在生成字体"
                                    hasOutput -> "字库已就绪"
                                    else -> "准备生成"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "TTF 通用字体 + WRobot 单线字库",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (hasOutput && !running) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (running) {
                        LinearProgressIndicator(
                            progress = { progressPercent.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        )
                        Text(
                            progressText.ifBlank { "正在合成字库，请稍候…" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            progressText.ifBlank {
                                "将生成覆盖 GB2312 常用字的完整字库，通常需要几分钟。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (progressText.contains("失败")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = {
                            val request = OneTimeWorkRequestBuilder<FontGenerationWorker>()
                                .setInputData(
                                    workDataOf(FontGenerationWorker.KEY_PROJECT_ID to projectId)
                                )
                                .addTag(projectId)
                                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(10))
                                .build()
                            workManager.enqueue(request)
                            running = true
                            progressPercent = 0f
                            progressText = "已加入生成队列…"
                        },
                        enabled = !running && writtenCount > 0,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(
                            if (hasOutput) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (hasOutput) "重新生成字体" else "开始生成字体")
                    }
                    if (writtenCount == 0) {
                        Text(
                            "至少手写 1 个字才能生成；建议先写 100 字。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (hasOutput) {
                Text("导出文件", style = MaterialTheme.typography.titleMedium)
                ttfPath?.let { path ->
                    OutputCard(
                        path = path,
                        icon = Icons.Rounded.TextFormat,
                        title = "通用字体 · TTF",
                        description = "可安装到系统，或用于支持 TTF 的软件",
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
                gfontPath?.let { path ->
                    OutputCard(
                        path = path,
                        icon = Icons.Rounded.Draw,
                        title = "写字机字库 · GFont",
                        description = "单线笔画，专为 WRobot 写字机器人准备",
                        accent = MaterialTheme.colorScheme.primary,
                        badge = "WRobot 兼容",
                    )
                }
                Text(
                    "文件保存在 Documents/GFontCreator，分享后可传到电脑使用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("在 WRobot 中使用", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "1. 把 .gfont 文件传到电脑，放进 WRobot 的字库目录\n" +
                            "2. 在 WRobot 中选择「${projectName}」字库\n" +
                            "3. 排版后即可按单线笔迹书写",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "v3 单线字库格式，已通过 WRobot 解析与渲染验证",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputCard(
    path: String,
    icon: ImageVector,
    title: String,
    description: String,
    accent: Color,
    badge: String? = null,
) {
    val context = LocalContext.current
    val fileName = remember(path) { PublicFontStore.displayName(context, path) }
    val sizeKb = remember(path) { PublicFontStore.displaySizeKb(context, path) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        badge?.let {
                            Spacer(Modifier.width(8.dp))
                            Surface(color = accent.copy(alpha = 0.12f), shape = CircleShape) {
                                Text(
                                    it,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        fileName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    Text(
                        when {
                            sizeKb >= 1024 -> String.format("%.1f MB", sizeKb / 1024f)
                            sizeKb > 0 -> "$sizeKb KB"
                            else -> "文件大小计算中"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                FilledTonalButton(
                    onClick = {
                        val intent = PublicFontStore.shareIntent(context, path)
                        context.startActivity(Intent.createChooser(intent, "分享字体文件"))
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("分享")
                }
            }
        }
    }
}
