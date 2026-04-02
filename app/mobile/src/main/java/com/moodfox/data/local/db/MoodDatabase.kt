package com.moodfox.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `moon_phase_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `phase` TEXT NOT NULL,
                `illumination` REAL NOT NULL,
                `age` REAL NOT NULL
            )
        """.trimIndent())
        db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `moonPhaseSnapshotId` INTEGER DEFAULT NULL")
    }
}

@Database(
    entities = [MoodEntry::class, CauseCategory::class, WeatherSnapshot::class, MoonPhaseSnapshot::class],
    version = 2,
    exportSchema = false,
)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun causeCategoryDao(): CauseCategoryDao
    abstract fun weatherSnapshotDao(): WeatherSnapshotDao
    abstract fun moonPhaseSnapshotDao(): MoonPhaseSnapshotDao
}
