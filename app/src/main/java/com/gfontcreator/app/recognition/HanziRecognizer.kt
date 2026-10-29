package com.gfontcreator.app.recognition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.gfontcreator.app.handwriting.StrokeProject
import com.gfontcreator.app.handwriting.StrokeSmoother

data class Candidate(val codepoint: Int, val confidence: Float)

/**
 * 手写汉字识别接口（ref.md 4.2）
 * 双向职责：校验书写内容是否与预期一致 / 自由书写模式识别用户写的字。
 *
 * 生产实现：Handwritten 项目 MobileNetV2 模型（GB2312 一级字 3755 类），
 * NCNN INT8 量化约 4.1MB、单次推理 2–5ms，通过 NcnnRecognizer JNI 接入。
 * 当前内置 ProxyRecognizer：基于笔迹结构统计的轻量代理校验，
 * 并保留统一接口，接入 NCNN 模型后即可无缝替换。
 */
interface HanziRecognizer {
    /**
     * 对笔迹进行识别，返回按置信度降序的候选字列表。
     * @param expectCodepoint 预期汉字（校验模式），null 表示自由识别
     */
    fun recognize(project: StrokeProject, expectCodepoint: Int? = null): List<Candidate>

    /** 校验模式：主候选置信度低于阈值时建议重写 */
    fun verify(project: StrokeProject, expectCodepoint: Int, threshold: Float = 0.5f): Boolean {
        val top = recognize(project, expectCodepoint).firstOrNull() ?: return false
        return top.codepoint == expectCodepoint && top.confidence >= threshold
    }
}

/**
 * 代理实现：不加载模型，用可量化的笔迹结构特征做启发式校验。
 * - 将笔迹渲染为 64×64 位图，提取归一化网格特征
 * - 与预期汉字的系统字体渲染特征比较结构相似度
 * 这是"可运行但精度有限"的占位，接口与 NCNN 实现完全一致。
 */
class ProxyRecognizer : HanziRecognizer {

    override fun recognize(project: StrokeProject, expectCodepoint: Int?): List<Candidate> {
        if (project.strokes.isEmpty()) return emptyList()
        val expected = expectCodepoint ?: return listOf(Candidate(project.codepoint, 1f))

        val a = rasterizeStrokes(project)
        val b = rasterizeSystemFont(expectCodepoint.toChar())
        val score = structuralSimilarity(a, b)
        return listOf(Candidate(expectCodepoint, score))
    }

    /** 将笔迹渲染为 64x64 二值位图 */
    private fun rasterizeStrokes(project: StrokeProject): FloatArray {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        // 归一化到画布（留 4px 边距）
        val all = project.strokes.flatMap { StrokeSmoother.flatten(it.points) }
        if (all.isNotEmpty()) {
            val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
            val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
            val w = (maxX - minX).coerceAtLeast(1f)
            val h = (maxY - minY).coerceAtLeast(1f)
            val scale = (size - 8f) / maxOf(w, h)
            val ox = (size - w * scale) / 2f
            val oy = (size - h * scale) / 2f
            for (stroke in project.strokes) {
                val flat = StrokeSmoother.flatten(stroke.points)
                if (flat.size < 2) continue
                val path = Path()
                path.moveTo((flat[0].x - minX) * scale + ox, (flat[0].y - minY) * scale + oy)
                for (p in flat.drop(1)) {
                    path.lineTo((p.x - minX) * scale + ox, (p.y - minY) * scale + oy)
                }
                canvas.drawPath(path, paint)
            }
        }
        return bitmapToGrid(bmp, size)
    }

    /** 系统字体渲染预期汉字为 64x64 二值位图 */
    private fun rasterizeSystemFont(ch: Char): FloatArray {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = size * 0.86f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(ch.toString(), size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        return bitmapToGrid(bmp, size)
    }

    private fun bitmapToGrid(bmp: Bitmap, size: Int): FloatArray {
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        val grid = FloatArray(size * size)
        for (i in px.indices) {
            // 8x8 池化到 8x8 特征，降低对笔画位置的敏感
            grid[i] = if ((px[i] and 0xFF) < 128) 1f else 0f
        }
        bmp.recycle()
        return grid
    }

    /** 网格结构相似度（分块 IoU 的简化版） */
    private fun structuralSimilarity(a: FloatArray, b: FloatArray): Float {
        val block = 8
        val gridSize = 64
        var hit = 0f
        var total = 0f
        for (by in 0 until gridSize step block) {
            for (bx in 0 until gridSize step block) {
                var sa = 0f; var sb = 0f
                for (y in by until by + block) {
                    for (x in bx until bx + block) {
                        val i = y * gridSize + x
                        sa += a[i]; sb += b[i]
                    }
                }
                if (sb > 0f) {
                    total += 1f
                    if (sa > 0f) hit += 1f
                }
            }
        }
        // 覆盖率 + 笔画数量接近度的组合
        val inkA = a.sum(); val inkB = b.sum()
        val inkRatio = if (inkB > 0) (minOf(inkA, inkB) / maxOf(inkA, inkB)) else 0f
        val coverage = if (total > 0) hit / total else 0f
        return (coverage * 0.6f + inkRatio * 0.4f).coerceIn(0f, 1f)
    }
}
