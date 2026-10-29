package com.gfontcreator.app.synthesis

import android.graphics.Bitmap
import com.gfontcreator.app.handwriting.StrokeProject

/**
 * 风格学习与字形合成接口（ref.md 4.4）
 *
 * 两阶段：
 *  阶段一：书写轨迹合成 —— 骨架变形网络将参考字体轨迹迁移到用户风格
 *  阶段二：字体风格渲染 —— GAN 从合成骨架恢复笔画轮廓细节
 *
 * 输入：用户书写的参考字集（775 字 OptSet 或快速模式 100 字）
 * 输出：目标字集（6900 字）的字形位图，供矢量化与 TTF 组装。
 */
interface StyleSynthesizer {

    /** 风格模型 */
    class StyleModel(
        /** 用户书写的参考字笔迹 */
        val referenceSamples: Map<Int, StrokeProject>,
        /** 提取的风格参数（仿射形变 / 笔宽分布 / 倾斜度等） */
        val styleParams: StyleParams,
    )

    /** 从参考字集学习风格 */
    suspend fun learnStyle(references: Map<Int, StrokeProject>): StyleModel

    /** 合成目标字的字形位图（返回 em 框渲染的墨迹位图，白底黑字） */
    suspend fun synthesize(model: StyleModel, codepoint: Int, emSize: Int = 1024): Bitmap?

    /** 是否已加载真实模型（未加载时走降级路径） */
    fun isModelReady(): Boolean
}

/** 提取的全局风格参数（降级方案：统计特征；模型方案：隐向量） */
data class StyleParams(
    /** 相对标准字心的平均偏移 (dx, dy)，归一化 em 单位 */
    val meanOffsetX: Float = 0f,
    val meanOffsetY: Float = 0f,
    /** 相对标准字体的缩放 */
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    /** 书写倾斜角（弧度） */
    val slant: Float = 0f,
    /** 平均笔宽（em 比例） */
    val strokeWidthRatio: Float = 0.045f,
)
