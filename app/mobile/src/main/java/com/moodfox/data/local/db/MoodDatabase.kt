package com.moodfox.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// v1 → v2: add updatedAt column.
// NOTE: do NOT use "DEFAULT NULL" — SQLite stores it in dflt_value as the string "NULL",
// which causes Room's identity-hash check to fail against the entity model that has no default.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `updatedAt` INTEGER")
    }
}

// v2 → v3: rescue devices that were deployed with the broken MIGRATION_1_2 that used
// "DEFAULT NULL". SQLite stored dflt_value='NULL' for that column, making Room's hash
// check fail. We normalise the table so the column definition is exactly what Room expects.
// We also guard against devices at v2 that never had the column at all (e.g. fresh installs
// that were created at v2 before updatedAt was in the entity schema).
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Ensure the column exists regardless of which v2 variant the device has.
        // SQLite will throw if the column already exists — that's fine, we swallow it.
        try {
            db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `updatedAt` INTEGER")
        } catch (_: Exception) { /* already present — continue to recreation */ }

        // Recreate the table to normalise dflt_value (removes the stored "NULL" string
        // that the first broken migration left behind, which caused the hash mismatch).
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `mood_entries_new` (
                `id`                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp`         INTEGER NOT NULL,
                `moodValue`         INTEGER NOT NULL,
                `causeIds`          TEXT NOT NULL,
                `note`              TEXT,
                `weatherSnapshotId` INTEGER,
                `updatedAt`         INTEGER
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `mood_entries_new`
                SELECT `id`, `timestamp`, `moodValue`, `causeIds`, `note`,
                       `weatherSnapshotId`, `updatedAt`
                FROM `mood_entries`
        """.trimIndent())
        db.execSQL("DROP TABLE `mood_entries`")
        db.execSQL("ALTER TABLE `mood_entries_new` RENAME TO `mood_entries`")
    }
}

@Database(
    entities = [MoodEntry::class, CauseCategory::class, WeatherSnapshot::class],
    version = 3,
    exportSchema = false,
)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun causeCategoryDao(): CauseCategoryDao
    abstract fun weatherSnapshotDao(): WeatherSnapshotDao
}
