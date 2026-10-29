package com.gfontcreator.app.synthesis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.gfontcreator.app.handwriting.StrokeProject
import com.gfontcreator.app.handwriting.StrokeSmoother
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 降级合成器（模型未就绪时的可运行路径，ref.md 4.4 / 风险应对）：
 *
 * 阶段一（轨迹合成的降级）：从参考字集提取统计风格参数——
 *   相对系统字体的重心偏移、宽高缩放、倾斜角、笔宽比。
 * 阶段二（风格渲染的降级）：以系统字体（楷体优先）为骨架源，
 *   按风格参数做仿射变换渲染目标字位图。
 *
 * 接入真实模型（骨架变形 + GAN 渲染，20–50MB NCNN 模型）后，
 * 替换此实现即可，接口不变。
 */
class FallbackSynthesizer : StyleSynthesizer {

    override fun isModelReady(): Boolean = false

    override suspend fun learnStyle(
        references: Map<Int, StrokeProject>
    ): StyleSynthesizer.StyleModel = withContext(Dispatchers.Default) {
        val params = StyleParams()
        var slantSum = 0f; var slantN = 0
        var widthSum = 0f; var widthN = 0

        for ((_, project) in references) {
            for (stroke in project.strokes) {
                val flat = StrokeSmoother.flatten(stroke.points, steps = 2)
                if (flat.size >= 6) {
                    // 用笔画中段的走向估计倾斜
                    val mid = flat.size / 2
                    val a = flat[mid - 2]; val b = flat[mid + 1]
                    val dx = b.x - a.x; val dy = b.y - a.y
                    if (sqrt(dx * dx + dy * dy) > 10f) {
                        slantSum += atan2(dy, dx); slantN++
                    }
                }
                val speeds = flat.map { it.speed }.filter { it > 0.01f }
                if (speeds.isNotEmpty()) {
                    widthSum += speeds.average().toFloat(); widthN++
                }
            }
        }
        val avgSpeed = if (widthN > 0) widthSum / widthN else 0.5f
        val learned = params.copy(
            // 书写越快笔越细
            strokeWidthRatio = (0.055f - 0.02f * (avgSpeed / 2f)).coerceIn(0.03f, 0.06f),
        )
        StyleSynthesizer.StyleModel(references, learned)
    }

    override suspend fun synthesize(
        model: StyleSynthesizer.StyleModel,
        codepoint: Int,
        emSize: Int
    ): Bitmap? = withContext(Dispatchers.Default) {
        val ch = codepoint.toChar()

        // 用户写过该字 → 直接栅格化其笔迹
        model.referenceSamples[codepoint]?.let { project ->
            return@withContext rasterizeAtEm(project, emSize)
        }

        // 未写过 → 系统字体骨架 + 风格仿射
        val bmp = Bitmap.createBitmap(emSize, emSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = emSize * 0.84f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = pickChineseTypeface()
        }
        val p = model.styleParams
        val m = Matrix().apply {
            // 以字心为轴做倾斜/缩放（统计参数目前以默认值为主，接真实模型后填充）
            postSkew(-p.slant, 0f, emSize / 2f, emSize / 2f)
        }
        canvas.withMatrix(m) {
            drawText(
                ch.toString(),
                emSize / 2f,
                emSize / 2f - (paint.ascent() + paint.descent()) / 2f,
                paint,
            )
        }
        bmp
    }

    /** 将用户笔迹按 em 框重栅格化（供"已手写"字形复用矢量化管线） */
    private fun rasterizeAtEm(project: StrokeProject, emSize: Int): Bitmap? {
        if (project.strokes.isEmpty()) return null
        val all = project.strokes.flatMap { StrokeSmoother.flatten(it.points, steps = 2) }
        val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
        val w = (maxX - minX).coerceAtLeast(1f)
        val h = (maxY - minY).coerceAtLeast(1f)
        val scale = (emSize * 0.84f) / maxOf(w, h)
        val ox = (emSize - w * scale) / 2f
        val oy = (emSize - h * scale) / 2f

        val bmp = Bitmap.createBitmap(emSize, emSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = emSize * 0.04f
            isAntiAlias = true
        }
        for (stroke in project.strokes) {
            val flat = StrokeSmoother.flatten(stroke.points, steps = 3)
            if (flat.size < 2) continue
            val path = Path()
            path.moveTo((flat[0].x - minX) * scale + ox, (flat[0].y - minY) * scale + oy)
            for (pt in flat.drop(1)) {
                path.lineTo((pt.x - minX) * scale + ox, (pt.y - minY) * scale + oy)
            }
            canvas.drawPath(path, paint)
        }
        return bmp
    }

    private fun pickChineseTypeface(): Typeface = try {
        // 优先楷体（更接近手写骨架源），回退默认
        Typeface.create("Kaiti", Typeface.NORMAL).takeIf { !it.toString().contains("null") }
            ?: Typeface.DEFAULT
    } catch (_: Exception) {
        Typeface.DEFAULT
    }

    private inline fun Canvas.withMatrix(matrix: Matrix, block: Canvas.() -> Unit) {
        val save = save()
        concat(matrix)
        try { block() } finally { restoreToCount(save) }
    }
}

/** 合成器工厂：真实模型就绪后替换 */
object SynthesizerFactory {
    fun get(): StyleSynthesizer = FallbackSynthesizer()
}
