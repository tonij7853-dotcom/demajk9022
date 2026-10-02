package chat.stoat.logging

import android.content.Context
import android.os.Build
import android.util.Log
import chat.stoat.BuildConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * High-performance, non-blocking structured JSONL logger for Dismod.
 * Writes diagnostic events to internal storage (filesDir/logs/app.log)
 * with automatic size-based log rotation, privacy scrubbing, crash capture,
 * and AI-ready export.
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // ~2 MB per file
    private const val MAX_BACKUP_FILES = 2 // app.log + app.log.1 + app.log.2 = 3 files total (~6 MB max)

    val sessionId = UUID.randomUUID().toString()
    private val backgroundWriter = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AppLogger-Writer").apply { isDaemon = true }
    }

    private var appContext: Context? = null
    private var logsDir: File? = null
    private var activeLogFile: File? = null
    private var externalLogsDir: File? = null
    private var activeExternalLogFile: File? = null
    private var isInitialized = false

    @Volatile
    var currentScreen: String = "launch"
        private set

    /**
     * CoroutineExceptionHandler for global structured logging of unhandled coroutines.
     */
    val globalCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        e(
            action = "coroutine_unhandled_exception",
            details = mapOf(
                "exception" to throwable.javaClass.name,
                "message" to (throwable.message ?: ""),
                "stack_trace" to throwable.stackTraceToString()
            ),
            throwable = throwable
        )
    }

    /**
     * Initializes the logger with the application context, creates the log directory,
     * installs uncaught exception crash handler, starts the embedded local debug server,
     * and records app launch.
     */
    fun init(context: Context) {
        if (isInitialized) return
        val app = context.applicationContext
        appContext = app
        logsDir = File(app.filesDir, "logs").apply { if (!exists()) mkdirs() }
        activeLogFile = File(logsDir, "app.log")

        try {
            val extDir = app.getExternalFilesDir("logs")
            if (extDir != null) {
                if (!extDir.exists()) extDir.mkdirs()
                externalLogsDir = extDir
                activeExternalLogFile = File(extDir, "app.log")
            }
        } catch (_: Exception) {}

        isInitialized = true

        installCrashHandler()

        // Start embedded direct log server (port 8088)
        DebugLogServer.start()

        i(
            action = "app_launch_started",
            details = mapOf(
                "app_version" to BuildConfig.VERSION_NAME,
                "version_code" to BuildConfig.VERSION_CODE,
                "android_sdk" to Build.VERSION.SDK_INT,
                "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "session_id" to sessionId,
                "debug_server_port" to DebugLogServer.activePort
            )
        )
    }

    fun setCurrentScreen(screen: String) {
        currentScreen = screen
    }

    fun d(action: String, details: Map<String, Any?> = emptyMap(), screen: String? = null) =
        log(LogLevel.DEBUG, action, details, screen)

    fun i(action: String, details: Map<String, Any?> = emptyMap(), screen: String? = null) =
        log(LogLevel.INFO, action, details, screen)

    fun w(
        action: String,
        details: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
        screen: String? = null
    ) = log(LogLevel.WARN, action, details, screen, throwable)

    fun e(
        action: String,
        details: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
        screen: String? = null
    ) = log(LogLevel.ERROR, action, details, screen, throwable)

    /**
     * Core logging function: sanitizes data, serializes to JSONL, and submits to background writer.
     */
    fun log(
        level: LogLevel,
        action: String,
        details: Map<String, Any?> = emptyMap(),
        screen: String? = null,
        throwable: Throwable? = null
    ) {
        val entryScreen = screen ?: currentScreen
        val nowIso = formatIso8601(System.currentTimeMillis())

        val sanitizedDetails = LogSanitizer.sanitizeMap(details).toMutableMap()
        if (throwable != null) {
            sanitizedDetails["error_class"] = throwable.javaClass.name
            sanitizedDetails["error_message"] = throwable.message ?: ""
            sanitizedDetails["stack_trace"] = throwable.stackTraceToString()
        }

        val json = JSONObject().apply {
            put("time", nowIso)
            put("level", level.name)
            put("action", action)
            put("screen", entryScreen)
            put("session_id", sessionId)
            put("details", JSONObject(sanitizedDetails))
        }

        val line = json.toString() + "\n"

        // Also echo to Android logcat for local debugging
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, "[$action] $line")
            LogLevel.INFO -> Log.i(TAG, "[$action] $line")
            LogLevel.WARN -> Log.w(TAG, "[$action] $line")
            LogLevel.ERROR -> Log.e(TAG, "[$action] $line")
        }

        if (!backgroundWriter.isShutdown) {
            backgroundWriter.submit {
                writeLineInternal(line)
            }
        }
    }

    @Synchronized
    private fun writeLineInternal(line: String) {
        val file = activeLogFile
        if (file != null) {
            try {
                rotateLogsIfNeeded(file, logsDir)
                FileOutputStream(file, true).use { fos ->
                    fos.write(line.toByteArray(Charsets.UTF_8))
                    fos.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing log line to internal disk", e)
            }
        }

        val extFile = activeExternalLogFile
        if (extFile != null) {
            try {
                rotateLogsIfNeeded(extFile, externalLogsDir)
                FileOutputStream(extFile, true).use { fos ->
                    fos.write(line.toByteArray(Charsets.UTF_8))
                    fos.flush()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Size-based log rotation: Keeps app.log, app.log.1, and app.log.2 (~2 MB each).
     */
    private fun rotateLogsIfNeeded(activeFile: File, dir: File?) {
        if (!activeFile.exists() || activeFile.length() < MAX_FILE_SIZE_BYTES) return

        val targetDir = dir ?: activeFile.parentFile ?: return
        try {
            // Delete oldest backup: app.log.2
            val oldest = File(targetDir, "app.log.$MAX_BACKUP_FILES")
            if (oldest.exists()) oldest.delete()

            // Shift older backups: app.log.1 -> app.log.2
            for (i in (MAX_BACKUP_FILES - 1) downTo 1) {
                val current = File(targetDir, "app.log.$i")
                val next = File(targetDir, "app.log.${i + 1}")
                if (current.exists()) current.renameTo(next)
            }

            // Rotate current app.log -> app.log.1
            val firstBackup = File(targetDir, "app.log.1")
            activeFile.renameTo(firstBackup)
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating log files in ${targetDir.absolutePath}", e)
        }
    }

    /**
     * Flushes writer queue synchronously. Call before app termination or crash handler exit.
     */
    fun flushSync() {
        val future = backgroundWriter.submit { /* barrier */ }
        try {
            future.get(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {}
    }

    /**
     * Intercepts uncaught exceptions on any thread, logs the full crash context,
     * flushes logs synchronously to storage, and lets the OS crash cleanly.
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                e(
                    action = "app_uncaught_crash",
                    details = mapOf(
                        "thread_name" to thread.name,
                        "thread_id" to thread.id,
                        "exception_class" to throwable.javaClass.name,
                        "message" to (throwable.message ?: ""),
                        "stack_trace" to throwable.stackTraceToString()
                    ),
                    throwable = throwable
                )
                flushSync()
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Creates an OkHttp Interceptor that automatically records network requests,
     * status codes, durations, and network exceptions while sanitizing auth tokens.
     */
    fun createOkHttpInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val rawUrl = request.url.toString()
            val sanitizedUrl = LogSanitizer.sanitizeUrl(rawUrl)
            val method = request.method
            val startMs = System.currentTimeMillis()

            try {
                val response: Response = chain.proceed(request)
                val durationMs = System.currentTimeMillis() - startMs
                val statusCode = response.code

                val details = mutableMapOf<String, Any?>(
                    "url" to sanitizedUrl,
                    "method" to method,
                    "status" to statusCode,
                    "duration_ms" to durationMs
                )

                if (statusCode >= 400) {
                    w(
                        action = "http_request_finished",
                        details = details
                    )
                } else {
                    d(
                        action = "http_request_finished",
                        details = details
                    )
                }

                response
            } catch (ioe: IOException) {
                val durationMs = System.currentTimeMillis() - startMs
                e(
                    action = "http_request_failed",
                    details = mapOf(
                        "url" to sanitizedUrl,
                        "method" to method,
                        "duration_ms" to durationMs,
                        "error" to (ioe.message ?: ioe.javaClass.simpleName)
                    ),
                    throwable = ioe
                )
                throw ioe
            }
        }
    }

    // ── Export & Diagnostics APIs ──────────────────────────────────────────

    /**
     * Reads all active and rotated log files in chronological order (oldest to newest lines).
     */
    fun readAllLogLines(): List<String> {
        val dir = logsDir ?: return emptyList()
        val files = mutableListOf<File>()
        for (i in MAX_BACKUP_FILES downTo 1) {
            val f = File(dir, "app.log.$i")
            if (f.exists()) files.add(f)
        }
        activeLogFile?.takeIf { it.exists() }?.let { files.add(it) }

        val lines = mutableListOf<String>()
        for (file in files) {
            try {
                file.forEachLine { line ->
                    if (line.isNotBlank()) lines.add(line)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading log file ${file.name}", e)
            }
        }
        return lines
    }

    /**
     * Returns logs formatted specifically for pasting directly to an AI assistant:
     * Header with metadata followed by JSONL entries, newest first.
     */
    fun getLogsFormattedForAi(maxCount: Int = 200, errorsOnly: Boolean = false): String {
        val all = readAllLogLines()
        val filtered = if (errorsOnly) {
            all.filter { it.contains("\"level\":\"ERROR\"") || it.contains("\"level\":\"WARN\"") }
        } else {
            all
        }

        // Newest first
        val reversed = filtered.asReversed().take(maxCount)
        val count = reversed.size
        val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        val header = "Dismod app logs, newest first, format: JSONL, $count entries, app version $version"
        val body = reversed.joinToString("\n")
        return "$header\n\n$body"
    }

    /**
     * Clears all log files.
     */
    fun clearLogs(): Boolean {
        return try {
            val dir = logsDir
            if (dir != null) {
                val files = dir.listFiles { _, name -> name.startsWith("app.log") } ?: emptyArray()
                files.forEach { it.delete() }
                activeLogFile = File(dir, "app.log")
            }
            val extDir = externalLogsDir
            if (extDir != null) {
                val extFiles = extDir.listFiles { _, name -> name.startsWith("app.log") } ?: emptyArray()
                extFiles.forEach { it.delete() }
                activeExternalLogFile = File(extDir, "app.log")
            }
            i("logs_cleared", mapOf("reason" to "User cleared logs from Debug screen"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed clearing logs", e)
            false
        }
    }

    /**
     * Gets the main log file to share via Android share sheet.
     */
    fun getLogFile(): File? {
        val file = activeLogFile ?: return null
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Combines all rotated logs into a single export file in cache for full sharing.
     */
    fun createExportFile(context: Context): File? {
        return try {
            val cacheLogsDir = File(context.cacheDir, "logs").apply { if (!exists()) mkdirs() }
            val exportFile = File(cacheLogsDir, "dismod_diagnostics_${System.currentTimeMillis()}.log")
            exportFile.outputStream().use { out ->
                val allLines = readAllLogLines()
                allLines.forEach { line ->
                    out.write((line + "\n").toByteArray(Charsets.UTF_8))
                }
                out.flush()
            }
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating log export file", e)
            null
        }
    }

    /**
     * Returns human-readable size of total logs stored on device.
     */
    fun getTotalLogSizeFormatted(): String {
        val dir = logsDir ?: return "0 KB"
        val totalBytes = dir.listFiles { _, name -> name.startsWith("app.log") }
            ?.sumOf { it.length() } ?: 0L
        return when {
            totalBytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", totalBytes / (1024f * 1024f))
            totalBytes >= 1024 -> String.format(Locale.US, "%.1f KB", totalBytes / 1024f)
            else -> "$totalBytes bytes"
        }
    }

    private fun formatIso8601(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }
}
