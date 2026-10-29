package com.gfontcreator.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字表清单：大字网格 + 完成状态 + 筛选 / 定位
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharGridScreen(
    charset: List<Char>,
    refSetSize: Int,
    writtenCodepoints: Set<Int>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 undone, 2 done

    val shown: List<IndexedValue<Char>> = when (filter) {
        1 -> charset.withIndex().filter { it.value.code !in writtenCodepoints }
        2 -> charset.withIndex().filter { it.value.code in writtenCodepoints }
        else -> charset.withIndex().toList()
    }
    val refWritten = remember(writtenCodepoints, refSetSize) {
        charset.take(refSetSize).count { it.code in writtenCodepoints }
    }
    val progress = (refWritten.toFloat() / refSetSize.coerceAtLeast(1)).coerceIn(0f, 1f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("字表清单", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "已写 $refWritten / $refSetSize · ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("全部") })
                FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("未写") })
                FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("已写") })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val firstUndone = charset.indexOfFirst { it.code !in writtenCodepoints }
                    if (firstUndone >= 0) onPick(firstUndone)
                }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("第一个未写")
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(shown, key = { it.value.code }) { entry ->
                    val i = entry.index
                    val ch = entry.value
                    val done = ch.code in writtenCodepoints
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (done) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                0.5.dp,
                                if (done) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onPick(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ch.toString(),
                            fontFamily = FontFamily.Serif,
                            fontSize = 22.sp,
                            color = if (done) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (done) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
