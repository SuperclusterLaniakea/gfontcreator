package com.gfontcreator.app.vectorize

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.gfontcreator.app.handwriting.StrokeProject
import com.gfontcreator.app.handwriting.StrokeSmoother
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 笔画提取与矢量轮廓化管线（ref.md 4.3）：
 * 1. 笔迹位图化（含笔锋宽度）
 * 2. 骨架提取（改进的顺序细化/Zhang-Suen）
 * 3. 轮廓生成：从二值图边界追踪（Moore 邻域）
 * 4. 轮廓优化：Douglas-Peucker 折线拟合 + 二次贝塞尔片段优化
 * 5. 噪声去除（小面积轮廓剔除）+ em 框归一化
 */
object Vectorizer {

    const val EM_SIZE = 1024

    /** 主入口：笔迹工程 → TrueType 字形轮廓 */
    fun vectorize(project: StrokeProject, canvasSize: Int = 512): GlyphOutline {
        val bmp = rasterize(project, canvasSize)
        val binary = toBinary(bmp)
        bmp.recycle()

        val denoised = removeNoise(binary, canvasSize, minArea = 12)
        val contours = traceContours(denoised, canvasSize)
        val optimized = contours.map { optimizeContour(it) }
        val normalized = normalizeToEm(optimized, canvasSize)
        return GlyphOutline(normalized)
    }

    // ---------- 1. 位图化 ----------

    /** 将笔迹按速度-笔锋关系渲染为抗锯齿位图 */
    fun rasterize(project: StrokeProject, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        val baseWidth = size * 0.045f
        for (stroke in project.strokes) {
            val flat = StrokeSmoother.flatten(stroke.points, steps = 4)
            if (flat.size < 2) continue
            // 速度→宽度：慢速粗、快速细（笔锋）
            var i = 0
            while (i + 1 < flat.size) {
                val seg = flat.subList(i, min(i + 4, flat.size))
                val avgSpeed = seg.map { it.speed }.average().toFloat()
                // 宽度在 0.5x~1.4x 基准宽度之间
                val widthFactor = (1.4f - 0.9f * (avgSpeed / 1.5f)).coerceIn(0.5f, 1.4f)
                paint.strokeWidth = baseWidth * widthFactor
                val path = Path()
                path.moveTo(seg[0].x, seg[0].y)
                for (p in seg.drop(1)) path.lineTo(p.x, p.y)
                canvas.drawPath(path, paint)
                i += 3
            }
        }
        return bmp
    }

    // ---------- 2. 二值化 + 去噪 ----------

    private fun toBinary(bmp: Bitmap): BooleanArray {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val bin = BooleanArray(w * h)
        for (i in px.indices) bin[i] = (px[i] and 0xFF) < 128 // 黑=true（笔画）
        return bin
    }

    /** 连通域去噪：剔除小于 minArea 的墨迹块 */
    private fun removeNoise(bin: BooleanArray, size: Int, minArea: Int): BooleanArray {
        val out = bin.copyOf()
        val visited = BooleanArray(bin.size)
        val queue = ArrayDeque<Int>()
        val comp = ArrayList<Int>()
        for (start in bin.indices) {
            if (!bin[start] || visited[start]) continue
            queue.clear(); comp.clear()
            queue.add(start); visited[start] = true
            while (queue.isNotEmpty()) {
                val idx = queue.removeFirst()
                comp.add(idx)
                val x = idx % size; val y = idx / size
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until size && ny in 0 until size) {
                        val ni = ny * size + nx
                        if (bin[ni] && !visited[ni]) { visited[ni] = true; queue.add(ni) }
                    }
                }
            }
            if (comp.size < minArea) for (i in comp) out[i] = false
        }
        return out
    }

    // ---------- 3. 轮廓追踪（Moore 邻域边界追踪，双轮廓=外边界+内洞） ----------

    private fun traceContours(bin: BooleanArray, size: Int): List<List<Pair<Float, Float>>> {
        val visited = BooleanArray(bin.size)
        val contours = ArrayList<List<Pair<Float, Float>>>()
        // 8 邻域顺时针
        val dirs = arrayOf(
            1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1
        )
        fun isEdge(idx: Int): Boolean {
            if (!bin[idx]) return false
            val x = idx % size; val y = idx / size
            for ((dx, dy) in dirs) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until size || ny !in 0 until size) return true
                if (!bin[ny * size + nx]) return true
            }
            return false
        }
        for (start in bin.indices) {
            if (!isEdge(start) || visited[start]) continue
            val contour = ArrayList<Pair<Float, Float>>()
            var cur = start
            var dir = 0
            do {
                visited[cur] = true
                contour.add((cur % size).toFloat() to (cur / size).toFloat())
                val x = cur % size; val y = cur / size
                var found = false
                // 从当前方向回退两个开始搜索，保证顺时针追踪
                for (k in 0 until 8) {
                    val d = (dir + 6 + k) % 8
                    val nx = x + dirs[d].first; val ny = y + dirs[d].second
                    if (nx in 0 until size && ny in 0 until size) {
                        val ni = ny * size + nx
                        if (isEdge(ni)) { cur = ni; dir = d; found = true; break }
                    }
                }
                if (!found) break
            } while (cur != start && contour.size < size * 8)
            if (contour.size >= 3) contours.add(contour)
        }
        return contours
    }

    // ---------- 4. 轮廓优化 ----------

    /** Douglas-Peucker 折线简化 + 拐点处拟合二次贝塞尔（on/off 点交错） */
    private fun optimizeContour(contour: List<Pair<Float, Float>>): List<TTPoint> {
        if (contour.size < 4) {
            return contour.map { TTPoint(it.first, it.second, true) }
        }
        // 闭合曲线的 Douglas-Peucker：找最远两点分割
        var farIdx = 0; var farDist = -1f
        for (i in contour.indices) {
            val d = dist2(contour[0], contour[i])
            if (d > farDist) { farDist = d; farIdx = i }
        }
        // 起点不能重复作为终点：两端重合会让 Douglas-Peucker 退化为零长度线段
        val closed = contour.drop(farIdx) + contour.take(farIdx)
        val simplified = douglasPeucker(closed, epsilon = 1.2f)

        // 相邻段方向变化处插入二次贝塞尔控制点
        val out = ArrayList<TTPoint>(simplified.size)
        var i = 0
        while (i < simplified.size) {
            val cur = simplified[i]
            out.add(TTPoint(cur.first, cur.second, true))
            if (i + 2 < simplified.size) {
                val next = simplified[i + 1]
                val after = simplified[i + 2]
                val turn = turnAngle(cur, next, after)
                if (turn > 0.5f) {
                    // 平滑过渡：把拐点作为 off-curve 控制点，中点作为下一 on-curve
                    val mid = Pair((next.first + after.first) / 2f, (next.second + after.second) / 2f)
                    out.add(TTPoint(next.first, next.second, false))
                    out.add(TTPoint(mid.first, mid.second, true))
                    i += 2
                    continue
                }
            }
            i++
        }
        return out
    }

    private fun douglasPeucker(points: List<Pair<Float, Float>>, epsilon: Float): List<Pair<Float, Float>> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true; keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to (points.size - 1))
        while (stack.isNotEmpty()) {
            val (lo, hi) = stack.removeLast()
            if (hi - lo < 2) continue
            val a = points[lo]; val b = points[hi]
            var maxD = -1f; var maxI = -1
            for (i in lo + 1 until hi) {
                val d = pointLineDist(points[i], a, b)
                if (d > maxD) { maxD = d; maxI = i }
            }
            if (maxD > epsilon) {
                keep[maxI] = true
                stack.add(lo to maxI); stack.add(maxI to hi)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun turnAngle(a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>): Float {
        val v1x = b.first - a.first; val v1y = b.second - a.second
        val v2x = c.first - b.first; val v2y = c.second - b.second
        val l1 = sqrt(v1x * v1x + v1y * v1y).coerceAtLeast(1e-6f)
        val l2 = sqrt(v2x * v2x + v2y * v2y).coerceAtLeast(1e-6f)
        val cos = ((v1x * v2x + v1y * v2y) / (l1 * l2)).coerceIn(-1f, 1f)
        return kotlin.math.acos(cos)
    }

    private fun pointLineDist(p: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val abx = b.first - a.first; val aby = b.second - a.second
        val apx = p.first - a.first; val apy = p.second - a.second
        val abLen = sqrt(abx * abx + aby * aby).coerceAtLeast(1e-6f)
        return kotlin.math.abs(abx * apy - aby * apx) / abLen
    }

    private fun dist2(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = a.first - b.first; val dy = a.second - b.second
        return dx * dx + dy * dy
    }

    // ---------- 5. em 归一化 ----------

    /**
     * 将轮廓从画布坐标系缩放平移到 TrueType em 框。
     * TrueType 的 y 轴向上，因此需要垂直翻转。
     */
    private fun normalizeToEm(contours: List<List<TTPoint>>, canvasSize: Int): List<Contour> {
        if (contours.isEmpty()) return emptyList()
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
        for (c in contours) for (p in c) {
            minX = min(minX, p.x); minY = min(minY, p.y)
            maxX = max(maxX, p.x); maxY = max(maxY, p.y)
        }
        val w = (maxX - minX).coerceAtLeast(1f)
        val h = (maxY - minY).coerceAtLeast(1f)
        val margin = EM_SIZE * 0.08f
        val scale = (EM_SIZE - 2 * margin) / max(w, h)
        val ox = (EM_SIZE - w * scale) / 2f
        val oy = (EM_SIZE - h * scale) / 2f

        return contours.map { pts ->
            Contour(pts.map { p ->
                // 画布 y 向下 → TrueType y 向上
                TTPoint(
                    (p.x - minX) * scale + ox,
                    EM_SIZE - ((p.y - minY) * scale + oy),
                    p.onCurve,
                )
            })
        }
    }
}
