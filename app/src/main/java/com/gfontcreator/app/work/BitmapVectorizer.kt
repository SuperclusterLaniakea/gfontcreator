package com.gfontcreator.app.work

import com.gfontcreator.app.vectorize.Contour
import com.gfontcreator.app.vectorize.GlyphOutline
import com.gfontcreator.app.vectorize.TTPoint
import com.gfontcreator.app.vectorize.Vectorizer
import java.util.ArrayDeque

/**
 * 位图 → 轮廓矢量化（复用 Vectorizer 的优化与归一化逻辑）。
 * 独立入口：输入二值位图（true=笔画），输出 em 框字形轮廓。
 */
object BitmapVectorizer {

    fun vectorize(binary: BooleanArray, size: Int): GlyphOutline {
        val denoised = denoise(binary, size, minArea = 24)
        val contours = traceContours(denoised, size)
        val optimized = contours.map { optimize(it) }
        return GlyphOutline(normalize(optimized, size))
    }

    private fun denoise(bin: BooleanArray, size: Int, minArea: Int): BooleanArray {
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
                if (x + 1 < size && bin[idx + 1] && !visited[idx + 1]) { visited[idx + 1] = true; queue.add(idx + 1) }
                if (x > 0 && bin[idx - 1] && !visited[idx - 1]) { visited[idx - 1] = true; queue.add(idx - 1) }
                if (y + 1 < size && bin[idx + size] && !visited[idx + size]) { visited[idx + size] = true; queue.add(idx + size) }
                if (y > 0 && bin[idx - size] && !visited[idx - size]) { visited[idx - size] = true; queue.add(idx - size) }
            }
            if (comp.size < minArea) for (i in comp) out[i] = false
        }
        return out
    }

    /**
     * 沿像素边界（lattice 网格）追踪闭合轮廓。
     *
     * 旧实现按「边界像素」漫游，可能反复进入已访问像素，单个字形就会膨胀到数十万点并 OOM；
     * 这里改为收集有向边界边（墨迹始终在行进方向右侧），每条边只走一次，
     * 外轮廓与内洞天然方向相反，满足 TrueType 非零环绕填充规则。
     */
    private fun traceContours(bin: BooleanArray, size: Int): List<List<Pair<Float, Float>>> {
        val verticesWide = size + 1
        val edges = ArrayList<Long>(4096)
        fun addEdge(x0: Int, y0: Int, x1: Int, y1: Int) {
            val from = y0 * verticesWide + x0
            val to = y1 * verticesWide + x1
            edges.add((from.toLong() shl 32) or to.toLong())
        }
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                if (!bin[row + x]) continue
                if (y == 0 || !bin[row - size + x]) addEdge(x, y, x + 1, y)
                if (x == size - 1 || !bin[row + x + 1]) addEdge(x + 1, y, x + 1, y + 1)
                if (y == size - 1 || !bin[row + size + x]) addEdge(x + 1, y + 1, x, y + 1)
                if (x == 0 || !bin[row + x - 1]) addEdge(x, y + 1, x, y)
            }
        }

        val outgoing = HashMap<Int, ArrayDeque<Int>>()
        for (i in edges.indices) {
            val from = (edges[i] ushr 32).toInt()
            outgoing.getOrPut(from) { ArrayDeque() }.add(i)
        }

        val used = BooleanArray(edges.size)
        val contours = ArrayList<List<Pair<Float, Float>>>()
        var budget = 120_000
        for (i in edges.indices) {
            if (used[i] || budget <= 0) continue
            val contour = ArrayList<Pair<Float, Float>>()
            val startVertex = (edges[i] ushr 32).toInt()
            var edge = i
            while (true) {
                used[edge] = true
                val packed = edges[edge]
                val from = (packed ushr 32).toInt()
                contour.add((from % verticesWide).toFloat() to (from / verticesWide).toFloat())
                budget--
                val to = packed.toInt()
                if (to == startVertex || budget <= 0) break
                val queue = outgoing[to] ?: break
                var next = -1
                while (queue.isNotEmpty()) {
                    val candidate = queue.removeFirst()
                    if (!used[candidate]) { next = candidate; break }
                }
                if (next < 0) break
                edge = next
            }
            if (contour.size >= 4) contours.add(contour)
        }
        return contours
    }

    /** 复用 Vectorizer 的 Douglas-Peucker + 贝塞尔优化（通过反射访问不便，此处独立实现） */
    private fun optimize(contour: List<Pair<Float, Float>>): List<TTPoint> {
        if (contour.size < 4) return contour.map { TTPoint(it.first, it.second, true) }
        var farIdx = 0; var farDist = -1f
        for (i in contour.indices) {
            val dx = contour[0].first - contour[i].first
            val dy = contour[0].second - contour[i].second
            val d = dx * dx + dy * dy
            if (d > farDist) { farDist = d; farIdx = i }
        }
        // 不要把起点重复追加为终点：DP 两端重合会退化为零长度线段，
        // 导致所有中间点被当作「共线」删掉（轮廓只剩 2 个点）。
        val closed = contour.drop(farIdx) + contour.take(farIdx)
        val simplified = douglasPeucker(closed, epsilon = 1.2f)
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
        val l1 = kotlin.math.sqrt(v1x * v1x + v1y * v1y).coerceAtLeast(1e-6f)
        val l2 = kotlin.math.sqrt(v2x * v2x + v2y * v2y).coerceAtLeast(1e-6f)
        val cos = ((v1x * v2x + v1y * v2y) / (l1 * l2)).coerceIn(-1f, 1f)
        return kotlin.math.acos(cos)
    }

    private fun pointLineDist(p: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val abx = b.first - a.first; val aby = b.second - a.second
        val apx = p.first - a.first; val apy = p.second - a.second
        val abLen = kotlin.math.sqrt(abx * abx + aby * aby).coerceAtLeast(1e-6f)
        return kotlin.math.abs(abx * apy - aby * apx) / abLen
    }

    private fun normalize(contours: List<List<TTPoint>>, canvasSize: Int): List<Contour> {
        if (contours.isEmpty()) return emptyList()
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (c in contours) for (p in c) {
            if (p.x < minX) minX = p.x; if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x; if (p.y > maxY) maxY = p.y
        }
        val w = (maxX - minX).coerceAtLeast(1f)
        val h = (maxY - minY).coerceAtLeast(1f)
        val margin = Vectorizer.EM_SIZE * 0.08f
        val scale = (Vectorizer.EM_SIZE - 2 * margin) / maxOf(w, h)
        val ox = (Vectorizer.EM_SIZE - w * scale) / 2f
        val oy = (Vectorizer.EM_SIZE - h * scale) / 2f
        return contours.map { pts ->
            Contour(pts.map { p ->
                TTPoint(
                    (p.x - minX) * scale + ox,
                    Vectorizer.EM_SIZE - ((p.y - minY) * scale + oy),
                    p.onCurve,
                )
            })
        }
    }
}
