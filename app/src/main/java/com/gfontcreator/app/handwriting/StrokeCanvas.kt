package com.gfontcreator.app.handwriting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.gfontcreator.app.handwriting.Stroke as StrokeData
import kotlin.math.sqrt

/**
 * 手写画板（ref.md 4.1）
 * - 采集原始触摸坐标 + VelocityTracker 计算速度
 * - 中点二次贝塞尔平滑
 * - 背景绘制浅色参考字（米字格 + 模板字）
 */
class StrokeCanvasState {
    val currentRaw = mutableStateListOf<StrokePoint>()
    val committed = mutableStateListOf<StrokeData>()
    var canvasSize: IntSize = IntSize.Zero

    fun clear() {
        currentRaw.clear()
        committed.clear()
    }

    fun finishStroke() {
        if (currentRaw.size >= 2) {
            committed.add(StrokeData(StrokeSmoother.smooth(currentRaw.toList())))
        }
        currentRaw.clear()
    }

    fun toProject(codepoint: Int): StrokeProject =
        StrokeProject(
            codepoint = codepoint,
            strokes = committed.toList(),
            canvasWidth = canvasSize.width.toFloat(),
            canvasHeight = canvasSize.height.toFloat(),
        ).also { it.qualityScore = StrokeQuality.score(it) }
}

@Composable
fun StrokeCanvas(
    referenceChar: Char,
    state: StrokeCanvasState,
    modifier: Modifier = Modifier,
    showGuide: Boolean = true,
) {
    val velocityTracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    var lastTime = remember { 0L }
    val canvasBackground = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val inkColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
    val referenceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)

    Box(
        modifier = modifier
            .background(canvasBackground)
            .border(1.dp, borderColor)
            .onSizeChanged { state.canvasSize = it }
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        velocityTracker.resetTracking()
                        lastTime = System.currentTimeMillis()
                        state.currentRaw.clear()
                        state.currentRaw.add(StrokePoint(offset.x, offset.y))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val now = System.currentTimeMillis()
                        val dt = (now - lastTime).coerceAtLeast(1)
                        lastTime = now
                        val p = change.position
                        val prev = state.currentRaw.lastOrNull() ?: StrokePoint(p.x, p.y)
                        val speed = sqrt(
                            (p.x - prev.x).let { it * it } + (p.y - prev.y).let { it * it }
                        ) / dt
                        state.currentRaw.add(StrokePoint(p.x, p.y, speed))
                    },
                    onDragEnd = { state.finishStroke() },
                    onDragCancel = { state.finishStroke() },
                )
            }
        ) {
            val w = size.width
            val h = size.height

            if (showGuide) {
                // 米字格
                drawLine(guideColor, Offset(0f, h / 2), Offset(w, h / 2), 1f)
                drawLine(guideColor, Offset(w / 2, 0f), Offset(w / 2, h), 1f)
                drawLine(guideColor, Offset(0f, 0f), Offset(w, h), 1f)
                drawLine(guideColor, Offset(w, 0f), Offset(0f, h), 1f)
                // 浅色参考字：按短边收缩，宽画布上也不会溢出
                drawContext.canvas.nativeCanvas.let { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = referenceColor.toArgb()
                        textSize = minOf(w, h) * 0.78f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText(
                        referenceChar.toString(),
                        w / 2f,
                        h / 2f - (paint.ascent() + paint.descent()) / 2f,
                        paint,
                    )
                }
            }

            // 已落笔的笔画
            fun drawStroke(points: List<StrokePoint>) {
                if (points.size < 2) return
                val flat = StrokeSmoother.flatten(points)
                val path = Path().apply {
                    moveTo(flat.first().x, flat.first().y)
                    for (p in flat.drop(1)) lineTo(p.x, p.y)
                }
                // 笔锋：慢速处更粗（宽度随速度反比微调）
                drawPath(
                    path,
                    inkColor,
                    style = Stroke(
                        width = minOf(w, h) * 0.045f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            state.committed.forEach { drawStroke(it.points) }
            if (state.currentRaw.size >= 2) drawStroke(state.currentRaw.toList())
        }
    }
}
