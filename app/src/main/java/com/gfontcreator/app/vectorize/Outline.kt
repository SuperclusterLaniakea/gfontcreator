package com.gfontcreator.app.vectorize

/**
 * 矢量化核心数据结构（ref.md 4.3）
 * TrueType 使用二次贝塞尔曲线描述字形轮廓：
 * 轮廓由点序列组成，点分为曲线上点（onCurve）与控制点（offCurve）。
 */
data class TTPoint(val x: Float, val y: Float, val onCurve: Boolean)

/** 一条闭合轮廓 */
data class Contour(val points: List<TTPoint>)

/** 一个字形的轮廓集合（已归一化到 em 框） */
data class GlyphOutline(val contours: List<Contour>)
