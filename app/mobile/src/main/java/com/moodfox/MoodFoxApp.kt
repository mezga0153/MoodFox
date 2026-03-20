package com.moodfox

import android.app.Application
import com.moodfox.data.local.DefaultCategories
import com.moodfox.data.local.db.CauseCategoryDao
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class MoodFoxApp : Application() {

    @Inject lateinit var causeCategoryDao: CauseCategoryDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale.US)
        seedDefaultCategories()
    }

    private fun seedDefaultCategories() {
        appScope.launch {
            if (causeCategoryDao.count() == 0) {
                causeCategoryDao.insertAll(DefaultCategories.all)
            }
        }
    }
}

