package com.moodfox.di

import android.content.Context
import androidx.room.Room
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MIGRATION_1_2
import com.moodfox.data.local.db.MIGRATION_2_3
import com.moodfox.data.local.db.MoodDatabase
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.WeatherSnapshotDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MoodDatabase {
        return Room.databaseBuilder(
            context,
            MoodDatabase::class.java,
            "moodfox.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides fun provideMoodEntryDao(db: MoodDatabase): MoodEntryDao = db.moodEntryDao()
    @Provides fun provideCauseCategoryDao(db: MoodDatabase): CauseCategoryDao = db.causeCategoryDao()
    @Provides fun provideWeatherSnapshotDao(db: MoodDatabase): WeatherSnapshotDao = db.weatherSnapshotDao()
}
