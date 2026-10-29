package com.gfontcreator.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gfontcreator.app.data.AppDatabase
import com.gfontcreator.app.data.CharStrokes
import com.gfontcreator.app.data.FontProject
import com.gfontcreator.app.data.ProjectStatus
import com.gfontcreator.app.handwriting.StrokeProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class FontProjectViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val prefs = app.getSharedPreferences("gfont", Application.MODE_PRIVATE)

    val projects: Flow<List<FontProject>> = db.fontProjectDao().observeAll()

    /** 当前进行中的项目（最近创建且未完成） */
    fun activeProject(): Flow<FontProject?> =
        projects.map { list -> list.firstOrNull { it.status != ProjectStatus.COMPLETED } }

    fun createProject(name: String, quickMode: Boolean, generateNow: Boolean = false) = viewModelScope.launch {
        val project = FontProject(
            projectId = UUID.randomUUID().toString(),
            name = name.ifBlank { "我的手写字体" },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            refSetTarget = if (quickMode) 100 else 775,
        )
        db.fontProjectDao().upsert(project)
        prefs.edit().putString("activeProjectId", project.projectId).apply()
        if (generateNow) startGeneration(project.projectId)
    }

    /** 保存时可选：立即触发字体生成（ref.md 4.6 / 用户需求 3） */
    fun startGeneration(projectId: String) {
        val request = androidx.work.OneTimeWorkRequestBuilder<com.gfontcreator.app.work.FontGenerationWorker>()
            .setInputData(androidx.work.workDataOf(com.gfontcreator.app.work.FontGenerationWorker.KEY_PROJECT_ID to projectId))
            .addTag(projectId)
            .build()
        androidx.work.WorkManager.getInstance(getApplication()).enqueue(request)
    }

    fun deleteProject(projectId: String) = viewModelScope.launch {
        val project = db.fontProjectDao().get(projectId)
        db.charStrokesDao().deleteAllForProject(projectId)
        db.checkpointDao().clear(projectId)
        db.fontProjectDao().delete(projectId)
        val app = getApplication<Application>()
        // 删除公共存储中的产物（content URI 或文件路径）
        project?.let {
            com.gfontcreator.app.font.PublicFontStore.delete(app, it.generatedFontPath ?: "")
            com.gfontcreator.app.font.PublicFontStore.delete(app, it.generatedGfontPath ?: "")
        }
    }

    /** 各项目已写字数（主页列表展示用，挂起函数由 UI 层协程调用） */
    suspend fun writtenCounts(projectIds: List<String>): Map<String, Int> =
        projectIds.associateWith { db.charStrokesDao().countAllForProject(it) }

    suspend fun get(projectId: String): FontProject? = db.fontProjectDao().get(projectId)

    fun saveStrokes(projectId: String, sp: StrokeProject, inRefSet: Boolean) = viewModelScope.launch {
        db.charStrokesDao().upsert(
            CharStrokes(
                projectId = projectId,
                codepoint = sp.codepoint,
                strokesJson = sp.toJson(),
                savedAt = System.currentTimeMillis(),
                qualityScore = sp.qualityScore,
                inRefSet = inRefSet,
            )
        )
        db.fontProjectDao().get(projectId)?.let { db.fontProjectDao().upsert(it) } // touch updatedAt
    }

    suspend fun refSetWritten(projectId: String): Int =
        db.charStrokesDao().countRefSetWritten(projectId)
}
