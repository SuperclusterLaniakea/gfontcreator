package com.gfontcreator.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 字体项目状态机 */
enum class ProjectStatus { DRAFT, COLLECTING, MODELING, GENERATING, COMPLETED, FAILED }

/** 一个字体项目（对应 ref.md 4.6 FontProject） */
@Entity(tableName = "font_projects")
data class FontProject(
    @PrimaryKey val projectId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ProjectStatus = ProjectStatus.COLLECTING,
    /** 参考字集目标数量：775（完整）/ 100（快速模式） */
    val refSetTarget: Int,
    val referenceStyle: String = "kaiti",
    val generatedFontPath: String? = null,
    val generatedGfontPath: String? = null,
)

/** 单个汉字的笔迹（贝塞尔控制点 JSON 序列化存储） */
@Entity(tableName = "char_strokes", primaryKeys = ["projectId", "codepoint"])
data class CharStrokes(
    val projectId: String,
    /** Unicode 码位 */
    val codepoint: Int,
    /** StrokeProject 的 JSON 序列化 */
    val strokesJson: String,
    val savedAt: Long,
    /** 质量评分（0-100），采集时自动评估 */
    val qualityScore: Int = 0,
    /** 该字是否在参考字集内 */
    val inRefSet: Boolean = false,
)

/** 生成检查点（断点续传） */
@Entity(tableName = "checkpoints", primaryKeys = ["projectId", "phase"])
data class Checkpoint(
    val projectId: String,
    /** 阶段名：modeled / synthesized / vectorized / ttf / gfont */
    val phase: String,
    /** 已处理到的字符索引（-1 表示完成） */
    val charIndex: Int,
    val savedAt: Long,
)
