package com.moodfox.data.local

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogger @Inject constructor(
    private val context: Context,
) {
    companion object {
        private const val LOG_PREFIX = "app_activity-"
        private const val LOG_SUFFIX = ".log"
        private const val MAX_SIZE_BYTES = 512 * 1024 // 500 KB
        private const val RETENTION_DAYS = 7
        private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        @Volatile
        var instance: AppLogger? = null
            private set
    }

    init {
        instance = this
    }

    private val logDir: File by lazy { File(context.filesDir, "logs").also { it.mkdirs() } }
    private val logFilter = java.io.FileFilter { it.isFile && it.name.startsWith(LOG_PREFIX) && it.name.endsWith(LOG_SUFFIX) }
    private val lock = Any()

    fun log(tag: String, message: String) {
        val ts = LocalDateTime.now().format(timeFmt)
        val line = "$ts [$tag] $message\n"
        synchronized(lock) {
            try {
                pruneOldLogsLocked()
                val file = logFileFor(LocalDate.now())
                if (file.exists() && file.length() > MAX_SIZE_BYTES) {
                    val trimmed = file.readText().takeLast(MAX_SIZE_BYTES / 2)
                    file.writeText("--- log trimmed ---\n$trimmed")
                }
                file.appendText(line)
            } catch (_: Exception) {}
        }
    }

    fun read(file: File? = null): String {
        val target = file ?: latestLogFile()
        return try {
            if (target != null && target.exists()) target.readText() else "(empty log)"
        } catch (_: Exception) {
            "(error reading log)"
        }
    }

    fun clear() {
        synchronized(lock) {
            try { logDir.listFiles(logFilter)?.forEach { it.delete() } } catch (_: Exception) {}
        }
    }

    fun getLogFile(): File = logFileFor(LocalDate.now())

    fun listLogFiles(): List<LogFileInfo> = synchronized(lock) {
        pruneOldLogsLocked()
        logDir.listFiles(logFilter)
            ?.sortedByDescending { extractDate(it.name) ?: LocalDate.MIN }
            ?.map { LogFileInfo(it, extractDate(it.name), it.length()) }
            ?: emptyList()
    }

    fun latestLogFile(): File? = listLogFiles().firstOrNull()?.file

    fun user(message: String) = log("USER", message)
    fun nav(message: String)  = log("NAV", message)
    fun weather(message: String) = log("WEATHER", message)
    fun reminder(message: String) = log("REMINDER", message)
    fun error(tag: String, message: String, e: Throwable? = null) {
        val msg = if (e != null) "$message — ${e::class.simpleName}: ${e.message}" else message
        log("ERR/$tag", msg)
        if (e != null) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            log("ERR/$tag", "Stack:\n$sw")
        }
    }

    data class LogFileInfo(val file: File, val date: LocalDate?, val sizeBytes: Long)

    private fun logFileFor(date: LocalDate) = File(logDir, "$LOG_PREFIX${date}$LOG_SUFFIX")

    private fun extractDate(name: String): LocalDate? {
        val datePart = name.removePrefix(LOG_PREFIX).removeSuffix(LOG_SUFFIX)
        return try { LocalDate.parse(datePart) } catch (_: Exception) { null }
    }

    private fun pruneOldLogsLocked() {
        val files = logDir.listFiles(logFilter) ?: return
        val sorted = files.sortedByDescending { extractDate(it.name) ?: LocalDate.MIN }
        sorted.drop(RETENTION_DAYS).forEach { runCatching { it.delete() } }
    }
}
