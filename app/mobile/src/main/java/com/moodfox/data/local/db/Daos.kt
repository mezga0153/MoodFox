package com.moodfox.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodEntryDao {
    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC")
    fun getAll(): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entries WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC")
    fun getByDateRange(from: Long, to: Long): Flow<List<MoodEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MoodEntry): Long

    @Update
    suspend fun update(entry: MoodEntry)

    @Delete
    suspend fun delete(entry: MoodEntry)
}

@Dao
interface CauseCategoryDao {
    @Query("SELECT * FROM cause_categories ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<CauseCategory>>

    @Query("SELECT * FROM cause_categories WHERE isActive = 1 ORDER BY sortOrder ASC")
    fun getActive(): Flow<List<CauseCategory>>

    @Query("SELECT COUNT(*) FROM cause_categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CauseCategory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CauseCategory): Long

    @Update
    suspend fun update(category: CauseCategory)

    @Delete
    suspend fun delete(category: CauseCategory)

    @Transaction
    suspend fun updateSortOrders(updates: List<Pair<Long, Int>>) {
        updates.forEach { (id, order) ->
            updateSortOrder(id, order)
        }
    }

    @Query("UPDATE cause_categories SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}

@Dao
interface WeatherSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: WeatherSnapshot): Long

    @Query("SELECT * FROM weather_snapshots WHERE id = :id")
    suspend fun getById(id: Long): WeatherSnapshot?
}
