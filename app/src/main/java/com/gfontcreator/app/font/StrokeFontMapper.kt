package com.gfontcreator.app.font

import com.gfontcreator.app.handwriting.StrokeProject
import com.gfontcreator.app.handwriting.StrokeSmoother
import com.gfontcreator.app.vectorize.StrokeExtractor
import kotlin.math.max
import kotlin.math.min

/**
 * 把手写笔迹 / 骨架像素映射到 `.gfont` 的字体单位坐标（y 向下）。
 *
 * - 手写字：按保存时的画布尺寸把整个画布映射到 em 内容框，保留用户书写的大小；
 *   旧数据没有画布尺寸时按墨迹外接框归一化。
 * - 合成字：位图代表整个 em 框，直接按比例映射，保留不同字符的相对大小
 *   （标点、字母不会被强行撑满字格）。
 *
 * WRobot 渲染时会用每个字的墨迹框再居中一次，因此这里只需保证缩放一致。
 */
object StrokeFontMapper {

    fun fromWrittenProject(
        project: StrokeProject,
        emUnits: Int = GFontBuilder.EM_UNITS,
    ): List<List<GFontBuilder.Point>> {
        val flattened = project.strokes.map { StrokeSmoother.flatten(it.points, steps = 3) }
            .filter { it.size >= 2 }
        if (flattened.isEmpty()) return emptyList()

        val content = emUnits * GFontBuilder.CONTENT_RATIO
        val cw = project.canvasWidth
        val ch = project.canvasHeight
        val useCanvas = cw > 1f && ch > 1f

        var scale: Float
        var cx: Float
        var cy: Float
        if (useCanvas) {
            scale = min(content / cw, content / ch)
            cx = cw / 2f
            cy = ch / 2f
        } else {
            val all = flattened.flatten()
            val minX = all.minOf { it.x }
            val maxX = all.maxOf { it.x }
            val minY = all.minOf { it.y }
            val maxY = all.maxOf { it.y }
            val w = (maxX - minX).coerceAtLeast(1f)
            val h = (maxY - minY).coerceAtLeast(1f)
            scale = content / max(w, h)
            cx = (minX + maxX) / 2f
            cy = (minY + maxY) / 2f
        }

        return flattened.map { stroke ->
            stroke.map { GFontBuilder.Point((it.x - cx) * scale, (it.y - cy) * scale) }
        }
    }

    fun fromSkeleton(
        strokes: List<List<StrokeExtractor.Point>>,
        bitmapWidth: Int,
        bitmapHeight: Int,
        emUnits: Int = GFontBuilder.EM_UNITS,
    ): List<List<GFontBuilder.Point>> {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return emptyList()
        val scale = emUnits.toFloat() / max(bitmapWidth, bitmapHeight)
        val cx = bitmapWidth / 2f
        val cy = bitmapHeight / 2f
        return strokes.map { stroke ->
            stroke.map { GFontBuilder.Point((it.x - cx) * scale, (it.y - cy) * scale) }
        }
    }

    /** 位图（白底黑字）→ 单线笔画；factor 为降采样倍数，用于控制细化耗时 */
    fun skeletonFromBitmap(
        pixels: IntArray,
        width: Int,
        height: Int,
        factor: Int = 4,
    ): List<List<GFontBuilder.Point>> {
        val f = factor.coerceAtLeast(1)
        val w = (width / f).coerceAtLeast(1)
        val h = (height / f).coerceAtLeast(1)
        val bin = BooleanArray(w * h)
        // 阈值取 1/3 而不是 1/2：细笔画（撇/捺尾、点）在 4× 降采样时
        // 若按多数像素判定会断裂，导致骨架被切成互不相连的碎段。
        val need = (f * f + 2) / 3
        for (by in 0 until h) {
            for (bx in 0 until w) {
                var dark = 0
                for (dy in 0 until f) {
                    val y = by * f + dy
                    if (y >= height) break
                    val base = y * width
                    for (dx in 0 until f) {
                        val x = bx * f + dx
                        if (x >= width) break
                        if ((pixels[base + x] and 0xFF) < 128) dark++
                    }
                }
                bin[by * w + bx] = dark >= need
            }
        }
        val strokes = StrokeExtractor.extract(bin, w, h)
        return fromSkeleton(strokes, w, h)
    }
}
