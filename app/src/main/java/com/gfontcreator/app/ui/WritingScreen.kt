package com.gfontcreator.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gfontcreator.app.handwriting.StrokeCanvas
import com.gfontcreator.app.handwriting.StrokeCanvasState
import com.gfontcreator.app.recognition.RecognizerFactory

/**
 * 书写页：参考字提示 + 手写画板 + 识别校验 + 字表导航
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingScreen(
    charset: List<Char>,
    refSetSize: Int,
    writtenCodepoints: Set<Int>,
    initialIndex: Int,
    onBack: () -> Unit,
    onSave: (codepoint: Int, project: com.gfontcreator.app.handwriting.StrokeProject, inRefSet: Boolean, onSaved: () -> Unit) -> Unit,
) {
    var index by remember { mutableIntStateOf(initialIndex.coerceIn(0, charset.size - 1)) }
    val ch = charset[index]
    val canvasState = remember { StrokeCanvasState() }
    val recognizer = remember { RecognizerFactory.get() }
    var feedback by remember { mutableStateOf<String?>(null) }
    var forceNext by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var showGrid by remember { mutableStateOf(false) }
    val inRefSet = index < refSetSize

    val refWritten = remember(writtenCodepoints, refSetSize) {
        charset.take(refSetSize).count { it.code in writtenCodepoints }
    }
    val progress = (refWritten.toFloat() / refSetSize.coerceAtLeast(1)).coerceIn(0f, 1f)

    fun goTo(target: Int) {
        index = target
        canvasState.clear()
        feedback = null
        forceNext = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("书写", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (inRefSet) "参考字 ${index + 1} / $refSetSize"
                            else "补充字 ${index + 1}（参考集外）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
            Modifier
                .padding(pad)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "采集进度",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "$refWritten / $refSetSize",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ch.toString(),
                            fontFamily = FontFamily.Serif,
                            fontSize = 32.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (inRefSet) "正在书写参考字" else "正在书写补充字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text("「$ch」", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "写完后点「保存并下一字」",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (ch.code in writtenCodepoints) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "已完成",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.large),
            ) {
                StrokeCanvas(
                    referenceChar = ch,
                    state = canvasState,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            feedback?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { showGrid = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.GridView, contentDescription = "字表清单")
                }
                FilledTonalIconButton(
                    onClick = {
                        val firstUndone = charset.indexOfFirst { it.code !in writtenCodepoints }
                        if (firstUndone >= 0) {
                            goTo(firstUndone)
                        } else {
                            feedback = "字表已全部完成，可以返回生成字体了"
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一个未写")
                }
                OutlinedTextField(
                    value = jumpText,
                    onValueChange = { jumpText = it.take(3) },
                    placeholder = { Text("字 / 序号", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = {
                        val t = jumpText.trim()
                        if (t.isEmpty()) return@FilledTonalButton
                        val target: Int? = t.toIntOrNull()?.let { n ->
                            if (n in 1..charset.size) n - 1 else null
                        } ?: charset.indexOf(t.first()).takeIf { it >= 0 }
                        if (target != null) {
                            goTo(target)
                        } else {
                            feedback = "字表中未找到「$t」"
                        }
                        jumpText = ""
                    },
                    shape = CircleShape,
                    modifier = Modifier.height(56.dp),
                ) { Text("跳转") }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { canvasState.clear(); feedback = null; forceNext = false },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("清空")
                }
                OutlinedButton(
                    onClick = {
                        if (canvasState.currentRaw.isNotEmpty()) {
                            canvasState.currentRaw.clear()
                        } else if (canvasState.committed.isNotEmpty()) {
                            canvasState.committed.removeAt(canvasState.committed.size - 1)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("撤销")
                }
                OutlinedButton(
                    onClick = {
                        if (index > 0) goTo(index - 1)
                    },
                    enabled = index > 0,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("上一字")
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val project = canvasState.toProject(ch.code)
                    if (project.strokes.isEmpty()) {
                        feedback = "请先在画板上书写"
                        return@Button
                    }
                    if (!forceNext) {
                        val ok = recognizer.verify(project, ch.code)
                        if (!ok) {
                            feedback = "识别与「$ch」差异较大，请重写；再点一次可强制收录"
                            forceNext = true
                            return@Button
                        }
                    }
                    onSave(ch.code, project, inRefSet) {
                        canvasState.clear()
                        feedback = null
                        forceNext = false
                        if (index + 1 < charset.size) {
                            index += 1
                        } else {
                            feedback = "已完成字表最后一个字，可以生成字体了"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存并下一字")
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (showGrid) {
        CharGridScreen(
            charset = charset,
            refSetSize = refSetSize,
            writtenCodepoints = writtenCodepoints,
            onPick = { i ->
                showGrid = false
                goTo(i)
            },
            onDismiss = { showGrid = false },
        )
    }
}
