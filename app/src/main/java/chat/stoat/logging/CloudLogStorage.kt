package chat.stoat.logging

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import chat.stoat.BuildConfig
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.microservices.autumn.uploadToAutumn
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages automated cloud storage and remote live ingestion for Dismod diagnostic logs.
 * Enables live logs to be stored anywhere in the cloud so developers/AI can access them
 * without needing USB debugging or being on the same local Wi-Fi.
 *
 * Supported storage backends:
 * 1. Dismod Native Autumn CDN (https://cdn.stoatusercontent.com/attachments/<id>/...)
 * 2. Anonymous public cloud paste service (https://dpaste.org) fallback
 * 3. Real-time live streaming to Discord Webhooks or custom HTTP endpoints
 */
object CloudLogStorage {
    private const val TAG = "CloudLogStorage"
    private const val PREFS_NAME = "dismod_cloud_logs"
    private const val KEY_WEBHOOK_URL = "webhook_url"
    private const val KEY_AUTO_UPLOAD_ERRORS = "auto_upload_errors"
    private const val KEY_LAST_CLOUD_URL = "last_cloud_url"
    private const val KEY_ENCRYPTION_ENABLED = "encryption_enabled"
    private const val KEY_ENCRYPTION_PASSWORD = "encryption_password"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null

    private val logBuffer = ConcurrentLinkedQueue<String>()
    private val isFlushing = AtomicBoolean(false)
    private val lastAutoUploadTime = AtomicLong(0)
    private const val AUTO_UPLOAD_DEBOUNCE_MS = 60_000L // At most once every 60s

    @Volatile
    var webhookUrl: String = ""
        private set

    @Volatile
    var autoUploadErrors: Boolean = true
        private set

    @Volatile
    var lastCloudLogUrl: String = ""
        private set

    @Volatile
    var isEncryptionEnabled: Boolean = true
        private set

    @Volatile
    var encryptionPassword: String = LogEncryptor.DEFAULT_PASSWORD
        private set

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        webhookUrl = sp.getString(KEY_WEBHOOK_URL, "") ?: ""
        autoUploadErrors = sp.getBoolean(KEY_AUTO_UPLOAD_ERRORS, true)
        lastCloudLogUrl = sp.getString(KEY_LAST_CLOUD_URL, "") ?: ""
        isEncryptionEnabled = sp.getBoolean(KEY_ENCRYPTION_ENABLED, true)
        encryptionPassword = sp.getString(KEY_ENCRYPTION_PASSWORD, LogEncryptor.DEFAULT_PASSWORD) ?: LogEncryptor.DEFAULT_PASSWORD
    }

    fun setWebhookUrl(url: String) {
        webhookUrl = url.trim()
        prefs?.edit()?.putString(KEY_WEBHOOK_URL, webhookUrl)?.apply()
    }

    fun setAutoUploadErrors(enabled: Boolean) {
        autoUploadErrors = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_UPLOAD_ERRORS, enabled)?.apply()
    }

    fun setLastCloudLogUrl(url: String) {
        lastCloudLogUrl = url
        prefs?.edit()?.putString(KEY_LAST_CLOUD_URL, url)?.apply()
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        isEncryptionEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_ENCRYPTION_ENABLED, enabled)?.apply()
    }

    fun setEncryptionPassword(password: String) {
        val effective = password.trim().ifEmpty { LogEncryptor.DEFAULT_PASSWORD }
        encryptionPassword = effective
        prefs?.edit()?.putString(KEY_ENCRYPTION_PASSWORD, effective)?.apply()
    }

    /**
     * Called whenever a new log entry is generated.
     */
    fun onLogEntry(line: String, level: LogLevel, action: String) {
        // Stream live if a webhook is configured
        if (webhookUrl.isNotBlank()) {
            logBuffer.offer(line)
            if (logBuffer.size >= 10 || level == LogLevel.ERROR) {
                flushWebhookBatch()
            }
        }

        // Automatically store dump to cloud on critical errors
        if (autoUploadErrors && level == LogLevel.ERROR && !action.startsWith("cloud_log_")) {
            val now = System.currentTimeMillis()
            if (now - lastAutoUploadTime.get() >= AUTO_UPLOAD_DEBOUNCE_MS) {
                lastAutoUploadTime.set(now)
                scope.launch {
                    try {
                        uploadLogsToCloud(errorsOnly = false, reason = "auto_error_$action")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed auto uploading logs to cloud", e)
                    }
                }
            }
        }
    }

    /**
     * Flushes buffered log entries to the configured webhook in background batches.
     */
    fun flushWebhookBatch() {
        if (webhookUrl.isBlank()) {
            logBuffer.clear()
            return
        }
        if (isFlushing.compareAndSet(false, true)) {
            scope.launch {
                try {
                    val batch = mutableListOf<String>()
                    while (batch.size < 25) {
                        val item = logBuffer.poll() ?: break
                        batch.add(item)
                    }
                    if (batch.isEmpty()) return@launch

                    sendBatchToWebhook(webhookUrl, batch)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed sending log batch to webhook: ${e.message}")
                } finally {
                    isFlushing.set(false)
                    if (logBuffer.isNotEmpty()) {
                        flushWebhookBatch()
                    }
                }
            }
        }
    }

    private fun sendBatchToWebhook(targetUrl: String, lines: List<String>) {
        val isDiscord = targetUrl.contains("discord.com/api/webhooks", ignoreCase = true)
        val body = if (isDiscord) {
            val textBuilder = StringBuilder("```json\n")
            for (l in lines) {
                if (textBuilder.length + l.length + 5 > 1900) {
                    textBuilder.append("... [batch truncated]")
                    break
                }
                textBuilder.append(l.trim()).append("\n")
            }
            textBuilder.append("```")
            JSONObject().apply {
                put("content", textBuilder.toString())
                put("username", "Dismod Live Logger")
            }.toString()
        } else {
            val jsonArray = JSONArray()
            lines.forEach { l ->
                try {
                    jsonArray.put(JSONObject(l))
                } catch (_: Exception) {
                    jsonArray.put(l)
                }
            }
            JSONObject().apply {
                put("app", "Dismod")
                put("version", BuildConfig.VERSION_NAME)
                put("count", lines.size)
                put("logs", jsonArray)
            }.toString()
        }

        val request = Request.Builder()
            .url(targetUrl)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", "Dismod-Logger/${BuildConfig.VERSION_NAME}")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 204) {
                Log.w(TAG, "Webhook responded with HTTP ${response.code}")
            }
        }
    }

    /**
     * Uploads recent diagnostic logs to cloud storage.
     * Tries Autumn CDN first; falls back to dpaste.org anonymous cloud storage.
     * Returns the publicly accessible URL.
     */
    suspend fun uploadLogsToCloud(
        errorsOnly: Boolean = false,
        maxCount: Int = 300,
        reason: String = "manual"
    ): Result<String> {
        return try {
            val rawContent = AppLogger.getLogsFormattedForAi(maxCount = maxCount, errorsOnly = errorsOnly)
            val content = if (isEncryptionEnabled && encryptionPassword.isNotBlank()) {
                LogEncryptor.encrypt(rawContent, encryptionPassword)
            } else {
                rawContent
            }
            var publicUrl: String? = null

            // 1. Try Autumn CDN first if authenticated
            if (StoatAPI.sessionToken.isNotBlank()) {
                try {
                    val tempFile = File.createTempFile("dismod_logs_", ".jsonl").apply {
                        writeText(content, Charsets.UTF_8)
                    }
                    val autumnId = uploadToAutumn(
                        file = tempFile,
                        name = "dismod_diagnostics_${System.currentTimeMillis()}.jsonl",
                        tag = "attachments",
                        contentType = ContentType.Text.Plain
                    )
                    tempFile.delete()
                    publicUrl = "${chat.stoat.core.model.data.STOAT_FILES}/attachments/$autumnId/dismod_diagnostics.jsonl"
                } catch (e: Exception) {
                    Log.w(TAG, "Autumn upload failed, falling back to public cloud storage: ${e.message}")
                }
            }

            // 2. Fallback to public anonymous cloud storage (dpaste.org)
            if (publicUrl == null) {
                val formBody = FormBody.Builder()
                    .add("content", content)
                    .add("title", "Dismod Diagnostics ($reason)")
                    .add("syntax", "json")
                    .add("expiry_days", "7")
                    .build()

                val request = Request.Builder()
                    .url("https://dpaste.org/api/")
                    .post(formBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body.string().trim()
                        if (body.isNotBlank()) {
                            publicUrl = body
                        }
                    }
                }
            }

            if (publicUrl != null) {
                setLastCloudLogUrl(publicUrl)
                AppLogger.i(
                    action = "cloud_log_upload_success",
                    details = mapOf(
                        "url" to publicUrl,
                        "reason" to reason,
                        "errors_only" to errorsOnly
                    )
                )
                Result.success(publicUrl)
            } else {
                throw IllegalStateException("All cloud storage services returned empty response")
            }
        } catch (e: Exception) {
            AppLogger.w(
                action = "cloud_log_upload_failed",
                details = mapOf(
                    "reason" to reason,
                    "error" to (e.message ?: "")
                ),
                throwable = e
            )
            Result.failure(e)
        }
    }
}
