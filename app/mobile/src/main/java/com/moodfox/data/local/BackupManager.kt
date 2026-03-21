package com.moodfox.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.moodfox.data.local.db.CauseCategory
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntry
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.WeatherSnapshot
import com.moodfox.data.local.db.WeatherSnapshotDao
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moodEntryDao: MoodEntryDao,
    private val causeCategoryDao: CauseCategoryDao,
    private val weatherSnapshotDao: WeatherSnapshotDao,
) {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())

    suspend fun exportCsv(): Intent = withContext(Dispatchers.IO) {
        val entries    = moodEntryDao.getAllList()
        val categories = causeCategoryDao.getAllList().associateBy { it.id }
        val snapshots  = weatherSnapshotDao.getAllList().associateBy { it.id }

        val file = File(context.cacheDir, "moodfox_export.csv")
        PrintWriter(file).use { pw ->
            pw.println("id,datetime,moodValue,causes,note,weatherCondition,temperatureC")
            entries.forEach { e ->
                val dt = fmt.format(Instant.ofEpochMilli(e.timestamp))
                val causeNames = try {
                    val arr = JSONArray(e.causeIds)
                    (0 until arr.length())
                        .mapNotNull { categories[arr.getLong(it)]?.let { c -> "${c.emoji} ${c.name}" } }
                        .joinToString("; ")
                } catch (_: Exception) { "" }
                val snap    = e.weatherSnapshotId?.let { snapshots[it] }
                val cond    = snap?.condition?.replace("\"", "\"\"") ?: ""
                val tempC   = snap?.temperatureC?.toInt()?.toString() ?: ""
                val note    = e.note?.replace("\"", "\"\"") ?: ""
                pw.println("${e.id},\"$dt\",${e.moodValue},\"$causeNames\",\"$note\",\"$cond\",$tempC")
            }
        }
        shareIntent(file, "text/csv")
    }

    suspend fun exportJson(): Intent = withContext(Dispatchers.IO) {
        val entries = moodEntryDao.getAllList()
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id",               e.id)
                put("timestamp",        e.timestamp)
                put("datetime",         fmt.format(Instant.ofEpochMilli(e.timestamp)))
                put("moodValue",        e.moodValue)
                put("causeIds",         JSONArray(e.causeIds))
                put("note",             e.note ?: JSONObject.NULL)
                put("weatherSnapshotId", e.weatherSnapshotId ?: JSONObject.NULL)
            })
        }
        val file = File(context.cacheDir, "moodfox_export.json")
        file.writeText(arr.toString(2))
        shareIntent(file, "application/json")
    }

    suspend fun backupZip(): Intent = withContext(Dispatchers.IO) {
        val entries    = moodEntryDao.getAllList()
        val categories = causeCategoryDao.getAllList()
        val snapshots  = weatherSnapshotDao.getAllList()

        val entriesJson = JSONArray().also { arr ->
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id",                e.id)
                    put("timestamp",         e.timestamp)
                    put("moodValue",         e.moodValue)
                    put("causeIds",          e.causeIds)
                    put("note",              e.note ?: JSONObject.NULL)
                    put("weatherSnapshotId", e.weatherSnapshotId ?: JSONObject.NULL)
                })
            }
        }.toString()

        val categoriesJson = JSONArray().also { arr ->
            categories.forEach { c ->
                arr.put(JSONObject().apply {
                    put("id",        c.id)
                    put("name",      c.name)
                    put("emoji",     c.emoji)
                    put("sortOrder", c.sortOrder)
                    put("isDefault", c.isDefault)
                    put("isActive",  c.isActive)
                })
            }
        }.toString()

        val snapshotsJson = JSONArray().also { arr ->
            snapshots.forEach { s ->
                arr.put(JSONObject().apply {
                    put("id",          s.id)
                    put("timestamp",   s.timestamp)
                    put("city",        s.city)
                    put("temperatureC", s.temperatureC.toDouble())
                    put("condition",   s.condition)
                    put("isRaining",   s.isRaining)
                    put("humidity",    s.humidity.toDouble())
                })
            }
        }.toString()

        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault()).format(Instant.now())
        val zipFile = File(context.cacheDir, "moodfox_backup_$stamp.zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            for ((name, content) in listOf(
                "mood_entries.json"      to entriesJson,
                "cause_categories.json"  to categoriesJson,
                "weather_snapshots.json" to snapshotsJson,
            )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        shareIntent(zipFile, "application/zip")
    }

    suspend fun restoreZip(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val contents = mutableMapOf<String, String>()
            context.contentResolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(ins.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        contents[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            // Snapshots first (referenced by entries via weatherSnapshotId)
            contents["weather_snapshots.json"]?.let { json ->
                weatherSnapshotDao.deleteAll()
                val arr  = JSONArray(json)
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    WeatherSnapshot(
                        id          = o.getLong("id"),
                        timestamp   = o.getLong("timestamp"),
                        city        = o.getString("city"),
                        temperatureC = o.getDouble("temperatureC").toFloat(),
                        condition   = o.getString("condition"),
                        isRaining   = o.getBoolean("isRaining"),
                        humidity    = o.getDouble("humidity").toFloat(),
                    )
                }
                weatherSnapshotDao.insertAll(list)
            }

            contents["cause_categories.json"]?.let { json ->
                causeCategoryDao.deleteAll()
                val arr  = JSONArray(json)
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    CauseCategory(
                        id        = o.getLong("id"),
                        name      = o.getString("name"),
                        emoji     = o.getString("emoji"),
                        sortOrder = o.getInt("sortOrder"),
                        isDefault = o.getBoolean("isDefault"),
                        isActive  = o.getBoolean("isActive"),
                    )
                }
                causeCategoryDao.insertAllReplace(list)
            }

            contents["mood_entries.json"]?.let { json ->
                moodEntryDao.deleteAll()
                val arr  = JSONArray(json)
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    MoodEntry(
                        id                = o.getLong("id"),
                        timestamp         = o.getLong("timestamp"),
                        moodValue         = o.getInt("moodValue"),
                        causeIds          = o.getString("causeIds"),
                        note              = if (o.isNull("note")) null else o.getString("note"),
                        weatherSnapshotId = if (o.isNull("weatherSnapshotId")) null else o.getLong("weatherSnapshotId"),
                    )
                }
                moodEntryDao.insertAll(list)
            }
            true
        } catch (_: Exception) {
            false
        }
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

