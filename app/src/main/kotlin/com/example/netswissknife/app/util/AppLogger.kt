package com.example.netswissknife.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application-wide debug logger. Writes entries to both Logcat and an in-app log file
 * stored in the app's private files directory (no storage permissions required).
 *
 * Call [init] once from [Application.onCreate] before any logging.
 * Read the accumulated log via [getLogContent]. Clear it with [clearLogs].
 */
object AppLogger {

    private const val LOGCAT_TAG = "NetSwissKnife"
    private const val LOG_FILE_NAME = "debug.log"
    private const val LOG_BACKUP_NAME = "debug.log.bak"

    /** Max size of the active log file before it is rotated to the backup file. */
    private const val MAX_LOG_BYTES = 512 * 1024 // 512 KB

    @Volatile
    private var logFile: File? = null

    private val dateFmt = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        i("AppLogger", "Logger initialized – log file: ${logFile?.absolutePath}")
    }

    // ── Logging API ───────────────────────────────────────────────────────────

    fun d(tag: String, message: String) = write("D", tag, message)

    fun i(tag: String, message: String) = write("I", tag, message)

    fun w(tag: String, message: String) = write("W", tag, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        write("E", tag, full)
    }

    // ── Content access ────────────────────────────────────────────────────────

    /**
     * Returns the full content of the log file, newest entries at the bottom.
     * If a backup file also exists (from rotation) it is prepended.
     */
    fun getLogContent(): String {
        val file = logFile ?: return "Logger not initialized – call AppLogger.init() first."
        if (!file.exists()) return "(No log entries yet)"
        return synchronized(this) {
            try {
                val backup = File(file.parent ?: return@synchronized "", LOG_BACKUP_NAME)
                val backupText = if (backup.exists()) backup.readText() + "\n--- LOG ROTATED ---\n\n" else ""
                backupText + file.readText()
            } catch (e: Exception) {
                "(Error reading log file: ${e.javaClass.simpleName}: ${e.message})"
            }
        }
    }

    /** Deletes the log file and its backup. */
    fun clearLogs() {
        val file = logFile ?: return
        synchronized(this) {
            file.delete()
            File(file.parent, LOG_BACKUP_NAME).delete()
        }
        i("AppLogger", "Logs cleared")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun write(level: String, tag: String, message: String) {
        // Always emit to Logcat so adb logcat works too
        when (level) {
            "D" -> Log.d(LOGCAT_TAG, "[$tag] $message")
            "I" -> Log.i(LOGCAT_TAG, "[$tag] $message")
            "W" -> Log.w(LOGCAT_TAG, "[$tag] $message")
            "E" -> Log.e(LOGCAT_TAG, "[$tag] $message")
            else -> Log.v(LOGCAT_TAG, "[$tag] $message")
        }

        // Also persist to file
        val file = logFile ?: return
        val entry = "${dateFmt.get()!!.format(Date())} [$level/$tag] $message\n"
        synchronized(this) {
            // Rotate when the file gets too large
            if (file.exists() && file.length() > MAX_LOG_BYTES) {
                val backup = File(file.parent, LOG_BACKUP_NAME)
                backup.delete()
                file.renameTo(backup)
            }
            try {
                file.appendText(entry)
            } catch (_: Exception) {
                // Swallow – logging must never crash the app
            }
        }
    }
}
