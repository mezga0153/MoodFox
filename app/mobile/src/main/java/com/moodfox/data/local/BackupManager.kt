package com.moodfox.data.local

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.moodfox.data.local.db.MoodEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())

    suspend fun exportCsv(entries: List<MoodEntry>): Intent = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "moodfox_export.csv")
        PrintWriter(file).use { pw ->
            pw.println("id,timestamp,datetime,moodValue,causeIds,note")
            entries.forEach { e ->
                val dt = fmt.format(Instant.ofEpochMilli(e.timestamp))
                pw.println("${e.id},${e.timestamp},\"$dt\",${e.moodValue},\"${e.causeIds}\",\"${e.note ?: ""}\"")
            }
        }
        shareIntent(file, "text/csv")
    }

    suspend fun exportJson(entries: List<MoodEntry>): Intent = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id",        e.id)
                put("timestamp", e.timestamp)
                put("datetime",  fmt.format(Instant.ofEpochMilli(e.timestamp)))
                put("moodValue", e.moodValue)
                put("causeIds",  JSONArray(e.causeIds))
                put("note",      e.note ?: JSONObject.NULL)
            })
        }
        val file = File(context.cacheDir, "moodfox_export.json")
        file.writeText(arr.toString(2))
        shareIntent(file, "application/json")
    }

    private fun shareIntent(file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
