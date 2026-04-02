package com.moodfox.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moodfox.domain.MoonPhaseCalculator

// v1 → v2: add moon_phase_snapshots table, moonPhaseSnapshotId FK on mood_entries,
// and backfill snapshots for all existing entries.
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
        // No DEFAULT NULL — avoids Room identity-hash mismatch from SQLite dflt_value storage.
        db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `moonPhaseSnapshotId` INTEGER")

        // Backfill moon phase snapshots for existing entries.
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
                    arrayOf<Any>(snap.timestamp, snap.phase, snap.illumination, snap.age),
                )
                db.execSQL(
                    "UPDATE `mood_entries` SET `moonPhaseSnapshotId` = last_insert_rowid() WHERE `id` = ?",
                    arrayOf<Any>(entryId),
                )
            }
        } finally {
            cursor.close()
        }
    }
}

// v2 → v3: add updatedAt and normalise mood_entries via full recreation so the schema
// exactly matches Room's model. Guards for all partial migration states (moon_phase_snapshots
// may or may not exist depending on upgrade path).
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Guard: ensure moon_phase_snapshots exists.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `moon_phase_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `phase` TEXT NOT NULL,
                `illumination` REAL NOT NULL,
                `age` REAL NOT NULL
            )
        """.trimIndent())
        // Guard: add missing columns (SQLite throws if already present — swallow).
        try { db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `moonPhaseSnapshotId` INTEGER") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `updatedAt` INTEGER") } catch (_: Exception) {}

        // Recreate mood_entries so the schema exactly matches Room's expected model,
        // eliminating any dflt_value artifacts left by earlier broken migrations.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `mood_entries_new` (
                `id`                  INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp`           INTEGER NOT NULL,
                `moodValue`           INTEGER NOT NULL,
                `causeIds`            TEXT NOT NULL,
                `note`                TEXT,
                `weatherSnapshotId`   INTEGER,
                `moonPhaseSnapshotId` INTEGER,
                `updatedAt`           INTEGER
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `mood_entries_new`
                SELECT `id`, `timestamp`, `moodValue`, `causeIds`, `note`,
                       `weatherSnapshotId`, `moonPhaseSnapshotId`, `updatedAt`
                FROM `mood_entries`
        """.trimIndent())
        db.execSQL("DROP TABLE `mood_entries`")
        db.execSQL("ALTER TABLE `mood_entries_new` RENAME TO `mood_entries`")
    }
}

// v3 → v4: some devices reached v3 via a divergent migration path (e.g. an intermediate
// APK that had a different v3 schema without moonPhaseSnapshotId / MoonPhaseSnapshot). This
// migration normalises every possible v3 state so Room's identity hash check passes on v4.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Guard: ensure moon_phase_snapshots exists.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `moon_phase_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `phase` TEXT NOT NULL,
                `illumination` REAL NOT NULL,
                `age` REAL NOT NULL
            )
        """.trimIndent())
        // Guard: ensure all optional mood_entries columns exist.
        try { db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `moonPhaseSnapshotId` INTEGER") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `updatedAt` INTEGER") } catch (_: Exception) {}

        // Full table recreation to produce the exact DDL Room expects for v4.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `mood_entries_new` (
                `id`                  INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp`           INTEGER NOT NULL,
                `moodValue`           INTEGER NOT NULL,
                `causeIds`            TEXT NOT NULL,
                `note`                TEXT,
                `weatherSnapshotId`   INTEGER,
                `moonPhaseSnapshotId` INTEGER,
                `updatedAt`           INTEGER
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `mood_entries_new`
                SELECT `id`, `timestamp`, `moodValue`, `causeIds`, `note`,
                       `weatherSnapshotId`, `moonPhaseSnapshotId`, `updatedAt`
                FROM `mood_entries`
        """.trimIndent())
        db.execSQL("DROP TABLE `mood_entries`")
        db.execSQL("ALTER TABLE `mood_entries_new` RENAME TO `mood_entries`")
    }
}

@Database(
    entities = [MoodEntry::class, CauseCategory::class, WeatherSnapshot::class, MoonPhaseSnapshot::class],
    version = 4,
    exportSchema = false,
)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun causeCategoryDao(): CauseCategoryDao
    abstract fun weatherSnapshotDao(): WeatherSnapshotDao
    abstract fun moonPhaseSnapshotDao(): MoonPhaseSnapshotDao
}
