package com.gfontcreator.app.handwriting

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 笔迹数据模型（ref.md 4.1）
 * 每一笔保存为贝塞尔曲线控制点序列 + 速度剖面，用于还原笔锋效果。
 */
@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    /** 该点的书写速度（px/ms），用于笔锋宽度 */
    val speed: Float = 0f,
)

@Serializable
data class Stroke(
    val points: List<StrokePoint>,
)

/** 一个字的完整笔迹工程（对应 ref.md StrokeProject） */
@Serializable
data class StrokeProject(
    val codepoint: Int,
    val strokes: List<Stroke>,
    val savedAt: Long = System.currentTimeMillis(),
    /** 质量评分 0-100 */
    var qualityScore: Int = 0,
    /** 采集画布尺寸（px）；旧数据为 0，导出单线字库时按墨迹外接框归一化 */
    val canvasWidth: Float = 0f,
    val canvasHeight: Float = 0f,
) {
    fun toJson(): String = Json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(s: String): StrokeProject? = try {
            json.decodeFromString<StrokeProject>(s)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * 触摸点 → 贝塞尔控制点序列的平滑器。
 * 中点二次贝塞尔：以相邻原始点的中点为曲线端点，原始点为控制点。
 */
object StrokeSmoother {

    /** 将原始触摸点序列（含速度）转换为平滑后的采样点序列 */
    fun smooth(raw: List<StrokePoint>, minDist: Float = 2f): List<StrokePoint> {
        if (raw.size < 3) return raw.toList()

        val out = ArrayList<StrokePoint>(raw.size)
        out.add(raw.first())
        for (i in 1 until raw.size - 1) {
            val prev = raw[i - 1]
            val cur = raw[i]
            val next = raw[i + 1]
            val dist = dist(prev, cur)
            if (dist < minDist && i != raw.size - 2) continue
            // 在中点插入采样点
            val midX = (cur.x + next.x) / 2f
            val midY = (cur.y + next.y) / 2f
            val midSpeed = (cur.speed + next.speed) / 2f
            out.add(StrokePoint(cur.x, cur.y, cur.speed))
            out.add(StrokePoint(midX, midY, midSpeed))
        }
        out.add(raw.last())
        return out
    }

    /** 用二次贝塞尔把平滑点渲染为 Path 用的采样（细分 t 步长） */
    fun flatten(points: List<StrokePoint>, steps: Int = 6): List<StrokePoint> {
        if (points.size < 3) return points
        val out = ArrayList<StrokePoint>()
        var i = 0
        while (i + 2 < points.size) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val p2 = points[i + 2]
            for (s in 0..steps) {
                val t = s / steps.toFloat()
                val mt = 1 - t
                val x = mt * mt * p0.x + 2 * mt * t * p1.x + t * t * p2.x
                val y = mt * mt * p0.y + 2 * mt * t * p1.y + t * t * p2.y
                val sp = mt * mt * p0.speed + 2 * mt * t * p1.speed + t * t * p2.speed
                out.add(StrokePoint(x, y, sp))
            }
            i += 2
        }
        out.add(points.last())
        return out
    }

    fun dist(a: StrokePoint, b: StrokePoint): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

/** 笔迹质量评分：笔画完整性 + 布局合理性（简化启发式，ref.md 4.1） */
object StrokeQuality {
    fun score(project: StrokeProject): Int {
        if (project.strokes.isEmpty()) return 0
        var s = 40
        // 笔画数量在合理区间加分
        when (project.strokes.size) {
            in 1..2 -> s += 10
            in 3..15 -> s += 30
            else -> s += 10
        }
        // 覆盖 em 框范围（布局合理性）
        val all = project.strokes.flatMap { StrokeSmoother.flatten(it.points) }
        if (all.isNotEmpty()) {
            val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
            val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
            val w = maxX - minX; val h = maxY - minY
            if (w > 50 && h > 50) s += 20
            if (w > 120 && h > 120) s += 10
        }
        return s.coerceIn(0, 100)
    }
}
