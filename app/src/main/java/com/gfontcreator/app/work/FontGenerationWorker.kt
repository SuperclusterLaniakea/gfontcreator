package com.gfontcreator.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gfontcreator.app.data.AppDatabase
import com.gfontcreator.app.data.Checkpoint
import com.gfontcreator.app.data.FontProject
import com.gfontcreator.app.data.ProjectStatus
import com.gfontcreator.app.font.GFontBuilder
import com.gfontcreator.app.font.StrokeFontMapper
import com.gfontcreator.app.font.TtfBuilder
import com.gfontcreator.app.synthesis.SynthesizerFactory
import com.gfontcreator.app.vectorize.Vectorizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 字体生成管线 Worker（ref.md 4.6 / 7.1 分阶段异步执行）
 *
 * 流程：
 *   学习风格 → 逐字合成（每 200 字存检查点） → 矢量化（TTF） →
 *   中心线提取（GFont/写字机器人） → TTF 组装 → WRobot 单线字库封装
 */
class FontGenerationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_PROJECT_ID = "projectId"
        const val KEY_PROGRESS = "progress"
        const val KEY_PERCENT = "percent"
        const val KEY_TTF_PATH = "ttfPath"
        const val KEY_GFONT_PATH = "gfontPath"

        /** 每处理多少个字保存一次中间检查点 */
        private const val CHECKPOINT_INTERVAL = 200

        /** 生成用栅格尺寸：512 已能还原笔锋，轮廓/细化耗时约为 1024 的四分之一，整库可在几分钟内完成 */
        private const val SYNTH_EM = 512
    }

    private val db = AppDatabase.get(applicationContext)
    private val charset by lazy {
        applicationContext.assets.open("charset_6900.txt").bufferedReader().readText()
            .filter { !it.isWhitespace() }
            .map { it.code }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val projectId = inputData.getString(KEY_PROJECT_ID)
            ?: return@withContext Result.failure()
        val project = db.fontProjectDao().get(projectId)
            ?: return@withContext Result.failure()

        try {
            db.fontProjectDao().updateStatus(projectId, ProjectStatus.GENERATING)

            // ---- 1. 载入已手写的笔迹 ----
            val strokeRecords = db.charStrokesDao().getAll(projectId)
            val references = strokeRecords.mapNotNull { rec ->
                com.gfontcreator.app.handwriting.StrokeProject.fromJson(rec.strokesJson)
                    ?.let { rec.codepoint to it }
            }.toMap()
            if (references.isEmpty()) {
                db.fontProjectDao().updateStatus(projectId, ProjectStatus.FAILED)
                return@withContext Result.failure(workDataOf(KEY_PROGRESS to "无手写样本"))
            }

            // ---- 2. 风格学习（检查点：modeled） ----
            val synthesizer = SynthesizerFactory.get()
            val style = synthesizer.learnStyle(references)
            db.checkpointDao().upsert(Checkpoint(projectId, "modeled", -1, now()))
            setProgress(
                workDataOf(
                    KEY_PROGRESS to "风格学习完成（参考字 ${references.size}）",
                    KEY_PERCENT to 1,
                )
            )

            // ---- 3. 逐字合成 + 矢量化 + 中心线提取（检查点每 200 字） ----
            val glyphOutlines = HashMap<Int, com.gfontcreator.app.vectorize.GlyphOutline>(charset.size)
            val gfontGlyphs = ArrayList<GFontBuilder.Glyph>(charset.size + 1)
            val writtenCodepoints = references.keys.toSet()

            for ((index, cp) in charset.withIndex()) {
                val bitmap = synthesizer.synthesize(style, cp, SYNTH_EM)
                if (bitmap != null) {
                    val w = bitmap.width
                    val h = bitmap.height
                    val pixels = IntArray(w * h)
                    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                    val binary = BooleanArray(w * h)
                    for (i in pixels.indices) binary[i] = (pixels[i] and 0xFF) < 128

                    glyphOutlines[cp] = BitmapVectorizer.vectorize(binary, w)

                    // GFont 需要单线笔画：手写字直接用原始笔迹；合成字从位图骨架提取
                    val written = references[cp]
                    val strokes = if (written != null) {
                        StrokeFontMapper.fromWrittenProject(written)
                    } else {
                        StrokeFontMapper.skeletonFromBitmap(pixels, w, h, factor = 2)
                    }
                    if (strokes.isNotEmpty()) {
                        gfontGlyphs.add(GFontBuilder.Glyph(cp, strokes))
                    }
                    bitmap.recycle()
                }
                if (index % 50 == 0) {
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS to "合成进度 ${index + 1}/${charset.size}",
                            KEY_PERCENT to ((index + 1) * 92 / charset.size),
                        )
                    )
                }
                if (index % CHECKPOINT_INTERVAL == CHECKPOINT_INTERVAL - 1) {
                    db.checkpointDao().upsert(Checkpoint(projectId, "synthesized", index, now()))
                }
            }
            db.checkpointDao().upsert(Checkpoint(projectId, "synthesized", -1, now()))

            // 先封装较小的 .gfont 并落盘，再组装 TTF，避免两套点对象同时参与大块分配
            setProgress(workDataOf(KEY_PROGRESS to "封装 WRobot 单线字库…", KEY_PERCENT to 94))
            val gfont = GFontBuilder.build(
                gfontGlyphs,
                GFontBuilder.Metadata(name = project.name),
            )
            val base = sanitizeFileName(project.name)
            val gfontLoc = com.gfontcreator.app.font.PublicFontStore.write(applicationContext, "$base.gfont", gfont)
            gfontGlyphs.clear()

            setProgress(workDataOf(KEY_PROGRESS to "组装 TTF 字体…", KEY_PERCENT to 98))
            val glyphData = glyphOutlines.map { (cp, outline) ->
                cp to TtfBuilder.GlyphData(outline)
            } + (0x20 to TtfBuilder.GlyphData(null, advanceWidth = TtfBuilder.UNITS_PER_EM / 2))
            val meta = TtfBuilder.Metadata(familyName = project.name)
            val ttf = TtfBuilder.build(glyphData, meta)
            glyphOutlines.clear()

            // ---- 6. 写入公共存储（Documents/GFontCreator）----
            val ttfLoc = com.gfontcreator.app.font.PublicFontStore.write(applicationContext, "$base.ttf", ttf)

            db.fontProjectDao().setOutput(projectId, ttfLoc, gfontLoc, ProjectStatus.COMPLETED)
            db.checkpointDao().clear(projectId)
            setProgress(workDataOf(KEY_PROGRESS to "完成（已写入 Documents/GFontCreator）", KEY_PERCENT to 100))
            Result.success(workDataOf(KEY_TTF_PATH to ttfLoc, KEY_GFONT_PATH to gfontLoc))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // OutOfMemoryError 等 Error 也要把项目置为失败，否则列表会一直停留在「生成中」
            db.fontProjectDao().updateStatus(projectId, ProjectStatus.FAILED)
            Result.failure(workDataOf(KEY_PROGRESS to (e.message ?: "生成失败")))
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim()
        return (cleaned.ifBlank { "我的手写字体" }).take(40)
    }

    private fun now(): Long = System.currentTimeMillis()
}
