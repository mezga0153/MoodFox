package com.moodfox.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moodfox.domain.MoonPhaseCalculator

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

        // Backfill: retroactively compute and link a moon phase snapshot for every
        // existing mood_entry so that pre-upgrade entries appear in analysis screens.
        val cursor = db.query("SELECT `id`, `timestamp` FROM `mood_entries`")
        try {
            val colId = cursor.getColumnIndexOrThrow("id")
            val colTs = cursor.getColumnIndexOrThrow("timestamp")
            while (cursor.moveToNext()) {
                val entryId = cursor.getLong(colId)
                val ts      = cursor.getLong(colTs)
                val snap    = MoonPhaseCalculator.compute(ts)
                db.execSQL(
                    "INSERT INTO `moon_phase_snapshots` (`timestamp`, `phase`, `illumination`, `age`) VALUES (?, ?, ?, ?)",
                    arrayOf(snap.timestamp, snap.phase, snap.illumination, snap.age),
                )
                db.execSQL(
                    "UPDATE `mood_entries` SET `moonPhaseSnapshotId` = last_insert_rowid() WHERE `id` = ?",
                    arrayOf(entryId),
                )
            }
        } finally {
            cursor.close()
        }
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
