package com.moodfox.data.local

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import com.moodfox.domain.WeatherScorer
import org.dhatim.fastexcel.Workbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
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
    private val fmt    = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())
    suspend fun exportXlsx(): Intent = withContext(Dispatchers.IO) {
        val entries    = moodEntryDao.getAllList()
        val categories = causeCategoryDao.getAllList().associateBy { it.id }
        val snapshots  = weatherSnapshotDao.getAllList().associateBy { it.id }

        val file = File(context.cacheDir, "moodfox_export.xlsx")
        file.outputStream().buffered().use { out ->
            Workbook(out, "MoodFox", "1.0").use { wb ->
                val ws = wb.newWorksheet("Mood Entries")
                listOf("ID", "Date", "Time", "Mood", "Causes", "Note", "Weather", "Temp (\u00b0C)", "Weather Score")
                    .forEachIndexed { col, name -> ws.value(0, col, name) }
                entries.forEachIndexed { i, e ->
                    val row = i + 1
                    val zdt = Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault())
                    val causeNames = try {
                        val arr = JSONArray(e.causeIds)
                        (0 until arr.length()).mapNotNull { categories[arr.getLong(it)]?.name }.joinToString("; ")
                    } catch (_: Exception) { "" }
                    val snap = e.weatherSnapshotId?.let { snapshots[it] }
                    ws.value(row, 0, e.id.toDouble())
                    ws.value(row, 1, zdt.toLocalDate())
                    ws.style(row, 1).format("yyyy-mm-dd").set()
                    ws.value(row, 2, zdt.toLocalTime().toString().take(5))
                    ws.value(row, 3, e.moodValue.toDouble())
                    ws.value(row, 4, causeNames)
                    ws.value(row, 5, e.note ?: "")
                    ws.value(row, 6, snap?.condition ?: "")
                    if (snap != null) ws.value(row, 7, snap.temperatureC.toInt().toDouble())
                    if (snap != null) ws.value(row, 8, WeatherScorer.score(snap).toDouble())
                }
            }
        }
        shareIntent(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    }

    suspend fun backupZip(): String = withContext(Dispatchers.IO) {
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
                    put("updatedAt",         e.updatedAt ?: JSONObject.NULL)
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

        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.now())
        val fileName = "MoodFox Backup $date.zip"

        val tmpZip = File(context.cacheDir, "moodfox_backup_tmp.zip")
        ZipOutputStream(tmpZip.outputStream().buffered()).use { zip ->
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = context.contentResolver.insert(collection, values)
                ?: throw IOException("Cannot create file in Downloads")
            context.contentResolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(tmpZip).use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(itemUri, values, null, null)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val dest = File(dir, fileName)
            FileInputStream(tmpZip).use { input -> FileOutputStream(dest).use { input.copyTo(it) } }
        }

        tmpZip.delete()
        fileName
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
                        updatedAt         = if (o.isNull("updatedAt") || !o.has("updatedAt")) null else o.getLong("updatedAt"),
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

