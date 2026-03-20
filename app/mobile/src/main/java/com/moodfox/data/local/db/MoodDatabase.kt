package com.moodfox.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MoodEntry::class, CauseCategory::class, WeatherSnapshot::class],
    version = 1,
    exportSchema = false,
)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun causeCategoryDao(): CauseCategoryDao
    abstract fun weatherSnapshotDao(): WeatherSnapshotDao
}
