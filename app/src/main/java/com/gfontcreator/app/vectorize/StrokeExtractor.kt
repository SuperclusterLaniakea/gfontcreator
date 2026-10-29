package com.gfontcreator.app.vectorize

import java.util.ArrayDeque

/**
 * 位图 → 单线笔画（中心线）提取，供 WRobot `.gfont` 使用。
 *
 * 流程：
 * 1. 连通域分析：极小的墨点单独保留（点笔画不能被细化掉）
 * 2. Zhang-Suen 细化（只扫描边界候选点，避免全图反复扫描）
 * 3. 毛刺裁剪：去掉细化在笔画交叉处产生的短支路
 * 4. 骨架图追踪：把交叉点像素聚成节点、节点间的骨架链作为边，
 *    再按方向连续性把边串成完整笔画（交叉处直穿，不抬笔）
 * 5. Douglas-Peucker 简化
 *
 * 输入二值图约定：true = 墨迹，坐标原点左上、y 向下。
 */
object StrokeExtractor {

    data class Point(val x: Float, val y: Float)

    data class Options(
        val minComponentArea: Int = 12,
        val spurLength: Int = 8,
        val simplifyEpsilon: Float = 1.1f,
    )

    private val DIRS = arrayOf(
        0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1,
    )

    fun extract(
        binary: BooleanArray,
        width: Int,
        height: Int,
        options: Options = Options(),
    ): List<List<Point>> {
        if (width <= 0 || height <= 0 || binary.size < width * height) return emptyList()
        val dots = ArrayList<Pair<Int, Int>>()
        val ink = binary.copyOf()
        collectSmallComponents(ink, width, height, options.minComponentArea, dots)

        thin(ink, width, height)
        pruneSpurs(ink, width, height, options.spurLength)

        val pixelPaths = ArrayList<List<Int>>()
        traceGraph(ink, width, height, pixelPaths)

        val raw = ArrayList<List<Point>>()
        for (path in pixelPaths) {
            if (path.size < 2) continue
            raw.add(path.map { toPoint(it, width) })
        }
        // 交叉点会把一条连续笔画拆成多段；把方向连续、端点相邻的碎片接回去，
        // 同时丢掉细化产生的短毛刺（写字机每抬一次笔都有代价）。
        val out = mergeFragments(raw, joinDist = 4.5f, minLength = 3.0f)
            .map { trimReversalTail(simplify(it, options.simplifyEpsilon)) }
            .filter { it.size >= 2 && polylineLength(it) >= 3.0f }
            .toMutableList()
        for ((cx, cy) in dots) {
            out.add(
                listOf(
                    Point(cx - 1.5f, cy + 1.5f),
                    Point(cx + 1.5f, cy - 1.5f),
                )
            )
        }
        out.sortWith(compareBy({ stroke -> stroke.minOf { it.y } }, { stroke -> stroke.minOf { it.x } }))
        return out
    }

    // ------------------------------------------------------------------
    // 碎片合并
    // ------------------------------------------------------------------

    private fun mergeFragments(
        input: List<List<Point>>,
        joinDist: Float,
        minLength: Float,
    ): List<List<Point>> {
        val chains = input
            .filter { polylineLength(it) >= minLength }
            .map { it.toMutableList() }
            .toMutableList()

        var changed = true
        while (changed) {
            changed = false
            var bestScore = -1f
            var bestI = -1
            var bestJ = -1
            var bestChain: MutableList<Point>? = null
            for (i in chains.indices) {
                for (j in i + 1 until chains.size) {
                    val join = bestJoin(chains[i], chains[j], joinDist) ?: continue
                    if (join.second > bestScore) {
                        bestScore = join.second
                        bestI = i
                        bestJ = j
                        bestChain = join.first
                    }
                }
            }
            if (bestI >= 0) {
                chains[bestI] = bestChain!!
                chains.removeAt(bestJ)
                changed = true
            }
        }
        return chains
    }

    /** 在四种端点朝向中找方向最连续且距离最近的接法 */
    private fun bestJoin(
        a: List<Point>,
        b: List<Point>,
        maxDist: Float,
    ): Pair<MutableList<Point>, Float>? {
        if (a.isEmpty() || b.isEmpty()) return null
        var best: Pair<MutableList<Point>, Float>? = null

        fun consider(
            joinA: Point,
            joinB: Point,
            dirA: Point,
            dirB: Point,
            build: () -> MutableList<Point>,
        ) {
            val dx = joinB.x - joinA.x
            val dy = joinB.y - joinA.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist > maxDist) return
            val la = kotlin.math.sqrt(dirA.x * dirA.x + dirA.y * dirA.y)
            val lb = kotlin.math.sqrt(dirB.x * dirB.x + dirB.y * dirB.y)
            if (la < 1e-4f || lb < 1e-4f) return
            val dot = (dirA.x * dirB.x + dirA.y * dirB.y) / (la * lb)
            if (dot < 0.35f) return
            val score = dot - dist / (maxDist * 4f)
            if (best == null || score > best!!.second) best = build() to score
        }

        val aStart = startHeading(a)
        val aEnd = endHeading(a)
        val bStart = startHeading(b)
        val bEnd = endHeading(b)

        consider(a.last(), b.first(), aEnd, bStart) { (a + b).toMutableList() }
        consider(a.last(), b.last(), aEnd, Point(-bEnd.x, -bEnd.y)) {
            (a + b.asReversed()).toMutableList()
        }
        consider(a.first(), b.first(), aStart, bStart) {
            (a.asReversed() + b).toMutableList()
        }
        consider(a.first(), b.last(), aStart, Point(-bEnd.x, -bEnd.y)) {
            (a.asReversed() + b.asReversed()).toMutableList()
        }
        return best
    }

    private fun startHeading(pts: List<Point>): Point {
        if (pts.size < 2) return Point(0f, 0f)
        val a = pts[0]
        val b = pts[minOf(3, pts.size - 1)]
        return Point(b.x - a.x, b.y - a.y)
    }

    private fun endHeading(pts: List<Point>): Point {
        if (pts.size < 2) return Point(0f, 0f)
        val a = pts[maxOf(0, pts.size - 4)]
        val b = pts[pts.size - 1]
        return Point(b.x - a.x, b.y - a.y)
    }

    private fun polylineLength(pts: List<Point>): Float {
        var len = 0f
        for (i in 1 until pts.size) {
            val dx = pts[i].x - pts[i - 1].x
            val dy = pts[i].y - pts[i - 1].y
            len += kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return len
    }

    // ------------------------------------------------------------------
    // 连通域
    // ------------------------------------------------------------------

    private fun collectSmallComponents(
        ink: BooleanArray,
        w: Int,
        h: Int,
        minArea: Int,
        dots: MutableList<Pair<Int, Int>>,
    ) {
        val visited = BooleanArray(ink.size)
        val queue = ArrayDeque<Int>()
        val comp = ArrayList<Int>()
        for (start in ink.indices) {
            if (!ink[start] || visited[start]) continue
            queue.clear()
            comp.clear()
            queue.add(start)
            visited[start] = true
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                comp.add(i)
                val x = i % w
                val y = i / w
                for ((dx, dy) in DIRS) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val ni = ny * w + nx
                    if (ink[ni] && !visited[ni]) {
                        visited[ni] = true
                        queue.add(ni)
                    }
                }
            }
            if (comp.size < minArea) {
                var sx = 0
                var sy = 0
                for (i in comp) {
                    sx += i % w
                    sy += i / w
                    ink[i] = false
                }
                // 1–5 像素的孤立点基本是抗锯齿噪点，不作为笔画导出
                if (comp.size >= 6) {
                    dots.add((sx.toFloat() / comp.size).toInt() to (sy.toFloat() / comp.size).toInt())
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Zhang-Suen 细化（边界候选点队列）
    // ------------------------------------------------------------------

    private fun thin(ink: BooleanArray, w: Int, h: Int) {
        val candidates = ArrayList<Int>()
        val inCand = BooleanArray(ink.size)
        for (i in ink.indices) {
            if (ink[i] && hasWhiteNeighbor(ink, w, h, i)) {
                candidates.add(i)
                inCand[i] = true
            }
        }

        var changed = true
        while (changed) {
            changed = false
            for (phase in 0..1) {
                val remove = ArrayList<Int>()
                for (i in candidates) {
                    if (ink[i] && shouldRemove(ink, w, h, i, phase)) remove.add(i)
                }
                if (remove.isEmpty()) continue
                changed = true
                for (i in remove) ink[i] = false

                val next = ArrayList<Int>(remove.size * 4)
                val seen = BooleanArray(ink.size)
                for (i in remove) {
                    val x = i % w
                    val y = i / w
                    for ((dx, dy) in DIRS) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val ni = ny * w + nx
                        if (ink[ni] && !seen[ni]) {
                            seen[ni] = true
                            next.add(ni)
                        }
                    }
                }
                for (i in candidates) {
                    if (ink[i] && !seen[i] && hasWhiteNeighbor(ink, w, h, i)) {
                        seen[i] = true
                        next.add(i)
                    }
                }
                candidates.clear()
                candidates.addAll(next)
            }
        }
    }

    private fun hasWhiteNeighbor(ink: BooleanArray, w: Int, h: Int, i: Int): Boolean {
        val x = i % w
        val y = i / w
        for ((dx, dy) in DIRS) {
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h) return true
            if (!ink[ny * w + nx]) return true
        }
        return false
    }

    private fun shouldRemove(ink: BooleanArray, w: Int, h: Int, i: Int, phase: Int): Boolean {
        val x = i % w
        val y = i / w
        fun p(dx: Int, dy: Int): Boolean {
            val nx = x + dx
            val ny = y + dy
            return nx in 0 until w && ny in 0 until h && ink[ny * w + nx]
        }
        val p2 = p(0, -1)
        val p3 = p(1, -1)
        val p4 = p(1, 0)
        val p5 = p(1, 1)
        val p6 = p(0, 1)
        val p7 = p(-1, 1)
        val p8 = p(-1, 0)
        val p9 = p(-1, -1)
        val b = listOf(p2, p3, p4, p5, p6, p7, p8, p9).count { it }
        if (b !in 2..6) return false
        val ring = booleanArrayOf(p2, p3, p4, p5, p6, p7, p8, p9)
        var a = 0
        for (k in 0 until 8) {
            if (!ring[k] && ring[(k + 1) % 8]) a++
        }
        if (a != 1) return false
        return if (phase == 0) {
            !(p2 && p4 && p6) && !(p4 && p6 && p8)
        } else {
            !(p2 && p4 && p8) && !(p2 && p6 && p8)
        }
    }

    // ------------------------------------------------------------------
    // 骨架图
    // ------------------------------------------------------------------

    private fun neighbors(ink: BooleanArray, w: Int, h: Int, i: Int): IntArray {
        val x = i % w
        val y = i / w
        var n = 0
        val buf = IntArray(8)
        for ((dx, dy) in DIRS) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until w && ny in 0 until h) {
                val ni = ny * w + nx
                if (ink[ni]) buf[n++] = ni
            }
        }
        return buf.copyOf(n)
    }

    /**
     * 骨架像素的邻居数。注意不能用 Rutovitz 交叉数判定节点：
     * 1px 骨架在斜向转折处会出现「两个相邻邻居」（交叉数为 1），
     * 那只是路径上的拐点，误判为端点会把一笔切成很多段。
     */
    private fun degree(ink: BooleanArray, w: Int, h: Int, i: Int): Int =
        neighbors(ink, w, h, i).size

    private fun pruneSpurs(ink: BooleanArray, w: Int, h: Int, maxLen: Int) {
        repeat(2) {
            val remove = HashSet<Int>()
            for (i in ink.indices) {
                if (!ink[i] || degree(ink, w, h, i) != 1) continue
                val path = ArrayList<Int>()
                path.add(i)
                var prev = -1
                var cur = i
                var hitJunction = false
                while (path.size <= maxLen) {
                    var next = -1
                    for (n in neighbors(ink, w, h, cur)) {
                        if (n != prev) {
                            next = n
                            break
                        }
                    }
                    if (next < 0) break
                    path.add(next)
                    if (degree(ink, w, h, next) >= 3) {
                        hitJunction = true
                        break
                    }
                    prev = cur
                    cur = next
                }
                if (hitJunction && path.size <= maxLen) {
                    // 保留交叉点本身
                    for (k in 0 until path.size - 1) remove.add(path[k])
                }
            }
            if (remove.isEmpty()) return
            for (i in remove) ink[i] = false
        }
    }

    /**
     * 把细化骨架转成笔画。
     *
     * 先按度数把像素分成「节点」（端点/交叉点，度数 != 2）和「路径点」（度数 == 2），
     * 相邻节点像素聚成一个节点簇——这样细化留下的 2x2 拐角块会被收缩成一个点，
     * 不会在交叉处产生大量 2–3 像素的碎片。节点簇之间的路径链构成图的边，
     * 最后从端点出发按方向最连续的原则把边串起来，路过交叉点时直穿。
     */
    private fun traceGraph(ink: BooleanArray, w: Int, h: Int, out: MutableList<List<Int>>) {
        val n = w * h
        val deg = IntArray(n) { -1 }
        val nodePixels = ArrayList<Int>()
        for (i in ink.indices) {
            if (!ink[i]) continue
            deg[i] = neighbors(ink, w, h, i).size
            if (deg[i] != 2) nodePixels.add(i)
        }
        if (nodePixels.isEmpty()) {
            traceLoop(ink, w, h, out)
            return
        }

        // ---- 节点簇 ----
        val nodeId = IntArray(n) { -1 }
        var clusterTotal = 0
        for (start in nodePixels) {
            if (nodeId[start] >= 0) continue
            val id = clusterTotal++
            nodeId[start] = id
            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val p = stack.removeLast()
                for (q in neighbors(ink, w, h, p)) {
                    if (deg[q] != 2 && nodeId[q] < 0) {
                        nodeId[q] = id
                        stack.addLast(q)
                    }
                }
            }
        }

        // ---- 边：节点簇之间的路径链 ----
        val edgePixels = ArrayList<IntArray>()
        val usedPathPixel = BooleanArray(n)
        for (p in nodePixels) {
            for (q in neighbors(ink, w, h, p)) {
                if (nodeId[q] == nodeId[p]) continue
                if (deg[q] != 2 || usedPathPixel[q]) continue
                val pts = ArrayList<Int>()
                pts.add(p)
                var prev = p
                var cur = q
                pts.add(cur)
                usedPathPixel[cur] = true
                while (deg[cur] == 2) {
                    var next = -1
                    for (nn in neighbors(ink, w, h, cur)) {
                        if (nn != prev) {
                            next = nn
                            break
                        }
                    }
                    if (next < 0) break
                    prev = cur
                    cur = next
                    pts.add(cur)
                    if (deg[cur] == 2) usedPathPixel[cur] = true
                }
                edgePixels.add(pts.toIntArray())
            }
        }

        val edgeA = IntArray(edgePixels.size) { nodeId[edgePixels[it].first()] }
        val edgeB = IntArray(edgePixels.size) { nodeId[edgePixels[it].last()] }
        val incident = Array(clusterTotal) { ArrayList<Int>() }
        for (e in edgePixels.indices) {
            incident[edgeA[e]].add(e)
            if (edgeB[e] != edgeA[e]) incident[edgeB[e]].add(e)
        }
        val used = BooleanArray(edgePixels.size)

        fun pointsFrom(e: Int, node: Int): IntArray =
            if (edgeA[e] == node) edgePixels[e] else edgePixels[e].reversedArray()

        fun otherNode(e: Int, node: Int): Int =
            if (edgeA[e] == node) edgeB[e] else edgeA[e]

        fun outgoingHeading(pts: IntArray): FloatArray {
            val a = pts.first()
            val b = pts[minOf(pts.size - 1, 4)]
            return heading(a, b, w)
        }

        fun incomingHeading(pts: IntArray): FloatArray {
            val a = pts[maxOf(0, pts.size - 5)]
            val b = pts.last()
            return heading(a, b, w)
        }

        fun assemble(startNode: Int, startEdge: Int): List<Int> {
            used[startEdge] = true
            val path = ArrayList<Int>()
            val firstPts = pointsFrom(startEdge, startNode)
            path.addAll(firstPts.toList())
            var node = otherNode(startEdge, startNode)
            var incoming = incomingHeading(firstPts)
            while (node >= 0) {
                val options = incident[node].filter { !used[it] }
                if (options.isEmpty()) break
                var best = options[0]
                var bestScore = -2f
                for (e in options) {
                    val score = cosine(incoming, outgoingHeading(pointsFrom(e, node)))
                    if (score > bestScore) {
                        bestScore = score
                        best = e
                    }
                }
                used[best] = true
                val pts = pointsFrom(best, node)
                if (path.last() == pts.first()) {
                    path.addAll(pts.drop(1))
                } else {
                    path.addAll(pts.toList())
                }
                node = otherNode(best, node)
                incoming = incomingHeading(pts)
            }
            return path
        }

        // 1. 从端点（只有一条边的节点）出发，一路按方向直穿交叉点
        for (node in incident.indices) {
            if (incident[node].size != 1) continue
            val e = incident[node][0]
            if (!used[e]) out.add(assemble(node, e))
        }
        // 2. 补齐剩下的边（环、以及没有端点的笔画）
        for (e in edgePixels.indices) {
            if (!used[e]) out.add(assemble(edgeA[e], e))
        }
    }

    /** 整幅骨架都是 2 度像素时是纯闭合环，直接绕一圈 */
    private fun traceLoop(ink: BooleanArray, w: Int, h: Int, out: MutableList<List<Int>>) {
        val start = ink.indexOfFirst { it }
        if (start < 0) return
        val path = ArrayList<Int>()
        var prev = -1
        var cur = start
        while (path.size <= ink.size) {
            path.add(cur)
            val next = neighbors(ink, w, h, cur).firstOrNull { it != prev } ?: break
            prev = cur
            cur = next
            if (cur == start) break
        }
        if (path.size >= 2) out.add(path)
    }

    private fun heading(a: Int, b: Int, w: Int): FloatArray {
        val dx = (b % w - a % w).toFloat()
        val dy = (b / w - a / w).toFloat()
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-6f)
        return floatArrayOf(dx / len, dy / len)
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float = a[0] * b[0] + a[1] * b[1]

    // ------------------------------------------------------------------
    // 简化
    // ------------------------------------------------------------------

    private fun toPoint(i: Int, w: Int): Point =
        Point((i % w).toFloat() + 0.5f, (i / w).toFloat() + 0.5f)

    /**
     * 细化在笔画平头末端容易分叉成「Y」形，追踪后会留下原路折返的小尾巴。
     * 若最后一小段相对前一段几乎原路返回，就裁掉它，让笔画停在真正的端点。
     */
    private fun trimReversalTail(points: List<Point>): List<Point> {
        if (points.size < 3) return points
        val a = points[points.size - 3]
        val b = points[points.size - 2]
        val c = points[points.size - 1]
        val v1x = b.x - a.x
        val v1y = b.y - a.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val l1 = kotlin.math.sqrt(v1x * v1x + v1y * v1y)
        val l2 = kotlin.math.sqrt(v2x * v2x + v2y * v2y)
        if (l1 < 1e-4f || l2 < 1e-4f) return points
        val cos = (v1x * v2x + v1y * v2y) / (l1 * l2)
        if (cos > -0.9f) return points
        // 只裁很短的回折，避免削掉真正的尖钩
        if (l2 > polylineLength(points) * 0.2f) return points
        return points.dropLast(1)
    }

    private fun simplify(points: List<Point>, epsilon: Float): List<Point> {        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (lo, hi) = stack.removeLast()
            if (hi - lo < 2) continue
            val a = points[lo]
            val b = points[hi]
            var maxD = -1f
            var maxI = -1
            for (i in lo + 1 until hi) {
                val d = pointLineDist(points[i], a, b)
                if (d > maxD) {
                    maxD = d
                    maxI = i
                }
            }
            if (maxD > epsilon) {
                keep[maxI] = true
                stack.add(lo to maxI)
                stack.add(maxI to hi)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun pointLineDist(p: Point, a: Point, b: Point): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val apx = p.x - a.x
        val apy = p.y - a.y
        val len = kotlin.math.sqrt(abx * abx + aby * aby).coerceAtLeast(1e-6f)
        return kotlin.math.abs(abx * apy - aby * apx) / len
    }
}
