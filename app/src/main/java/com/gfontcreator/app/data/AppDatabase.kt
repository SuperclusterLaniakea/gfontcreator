package com.gfontcreator.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FontProject::class, CharStrokes::class, Checkpoint::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fontProjectDao(): FontProjectDao
    abstract fun charStrokesDao(): CharStrokesDao
    abstract fun checkpointDao(): CheckpointDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gfontcreator.db",
                ).build().also { instance = it }
            }
    }
}
