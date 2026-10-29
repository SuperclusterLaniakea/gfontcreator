package com.gfontcreator.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FontProjectDao {
    @Query("SELECT * FROM font_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<FontProject>>

    @Query("SELECT * FROM font_projects WHERE projectId = :id")
    suspend fun get(id: String): FontProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: FontProject)

    @Query("UPDATE font_projects SET status = :status, updatedAt = :now WHERE projectId = :id")
    suspend fun updateStatus(id: String, status: ProjectStatus, now: Long = System.currentTimeMillis())

    @Query("UPDATE font_projects SET generatedFontPath = :ttf, generatedGfontPath = :gfont, status = :status, updatedAt = :now WHERE projectId = :id")
    suspend fun setOutput(id: String, ttf: String?, gfont: String?, status: ProjectStatus, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM font_projects WHERE projectId = :id")
    suspend fun delete(id: String)
}

@Dao
interface CharStrokesDao {
    @Query("SELECT * FROM char_strokes WHERE projectId = :projectId ORDER BY codepoint")
    fun observeForProject(projectId: String): Flow<List<CharStrokes>>

    @Query("SELECT COUNT(*) FROM char_strokes WHERE projectId = :projectId AND inRefSet = 1")
    suspend fun countRefSetWritten(projectId: String): Int

    @Query("SELECT COUNT(*) FROM char_strokes WHERE projectId = :projectId")
    suspend fun countAllForProject(projectId: String): Int

    @Query("DELETE FROM char_strokes WHERE projectId = :projectId")
    suspend fun deleteAllForProject(projectId: String)

    @Query("SELECT * FROM char_strokes WHERE projectId = :projectId")
    suspend fun getAll(projectId: String): List<CharStrokes>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(strokes: CharStrokes)

    @Query("DELETE FROM char_strokes WHERE projectId = :projectId AND codepoint = :codepoint")
    suspend fun delete(projectId: String, codepoint: Int)
}

@Dao
interface CheckpointDao {
    @Query("SELECT * FROM checkpoints WHERE projectId = :projectId")
    suspend fun getAll(projectId: String): List<Checkpoint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: Checkpoint)

    @Query("DELETE FROM checkpoints WHERE projectId = :projectId")
    suspend fun clear(projectId: String)
}
