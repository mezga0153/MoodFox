package com.moodfox.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,           // Instant.toEpochMilli()
    val moodValue: Int,            // -10..+10
    val causeIds: String = "[]",   // JSON array of CauseCategory IDs
    val note: String? = null,
    val weatherSnapshotId: Long? = null,
)

@Entity(tableName = "cause_categories")
data class CauseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val sortOrder: Int,
    val isDefault: Boolean,
    val isActive: Boolean = true,
)

@Entity(tableName = "weather_snapshots")
data class WeatherSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,           // Instant.toEpochMilli()
    val city: String,
    val temperatureC: Float,
    val condition: String,
    val isRaining: Boolean,
    val humidity: Float,
)
