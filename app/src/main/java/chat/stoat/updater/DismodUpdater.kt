package chat.stoat.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import chat.stoat.BuildConfig
import chat.stoat.core.model.data.DISMOD_UPDATE_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class DismodUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String? = null,
    val forceUpdate: Boolean = false
)

sealed interface DismodUpdateState {
    object Idle : DismodUpdateState
    object Checking : DismodUpdateState
    data class UpdateAvailable(val info: DismodUpdateInfo) : DismodUpdateState
    data class Downloading(val info: DismodUpdateInfo, val progress: Float) : DismodUpdateState
    data class ReadyToInstall(val info: DismodUpdateInfo, val apkFile: File) : DismodUpdateState
    data class Error(val message: String) : DismodUpdateState
}

object DismodUpdater {
    private const val TAG = "DismodUpdater"

    private val _state = MutableStateFlow<DismodUpdateState>(DismodUpdateState.Idle)
    val state = _state.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Checks if a new update is available.
     */
    fun checkForUpdates(
        context: Context,
        scope: CoroutineScope,
        customUrl: String? = null,
        notifyIfNoUpdate: Boolean = false
    ) {
        scope.launch {
            try {
                _state.value = DismodUpdateState.Checking
                val updateUrl = customUrl ?: DISMOD_UPDATE_URL
                Log.d(TAG, "Checking for updates at $updateUrl")

                val updateInfo = withContext(Dispatchers.IO) {
                    val finalUrl = if (updateUrl.contains("?")) "$updateUrl&t=${System.currentTimeMillis()}" else "$updateUrl?t=${System.currentTimeMillis()}"
                    val request = Request.Builder()
                        .url(finalUrl)
                        .header("User-Agent", "DismodAndroid/${BuildConfig.VERSION_NAME}")
                        .header("Cache-Control", "no-cache, no-store, must-revalidate")
                        .header("Pragma", "no-cache")
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Update check failed with code ${response.code}")
                            return@withContext null
                        }
                        val body = response.body.string()
                        jsonParser.decodeFromString<DismodUpdateInfo>(body)
                    }
                }

                val prefs = context.getSharedPreferences("dismod_updater", Context.MODE_PRIVATE)
                val dismissedVersion = prefs.getInt("dismissed_version_code", 0)

                val isNewer = updateInfo != null && updateInfo.versionCode > BuildConfig.VERSION_CODE

                if (updateInfo != null && isNewer) {
                    if (!notifyIfNoUpdate && !updateInfo.forceUpdate && updateInfo.versionCode <= dismissedVersion) {
                        Log.d(TAG, "Update ${updateInfo.versionCode} was dismissed previously, skipping auto prompt")
                        _state.value = DismodUpdateState.Idle
                    } else {
                        Log.i(TAG, "New update found: ${updateInfo.versionName} (${updateInfo.versionCode})")
                        _state.value = DismodUpdateState.UpdateAvailable(updateInfo)
                    }
                } else {
                    Log.d(TAG, "App is up to date (current: ${BuildConfig.VERSION_CODE})")
                    _state.value = DismodUpdateState.Idle
                    if (notifyIfNoUpdate) {
                        _state.value = DismodUpdateState.Error("Dismod is up to date! (v${BuildConfig.VERSION_NAME})")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                _state.value = DismodUpdateState.Idle
                if (notifyIfNoUpdate) {
                    _state.value = DismodUpdateState.Error("Could not check for updates: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * Downloads the APK file and triggers installation.
     */
    fun startUpdate(context: Context, scope: CoroutineScope, info: DismodUpdateInfo) {
        scope.launch {
            try {
                _state.value = DismodUpdateState.Downloading(info, 0f)

                val apkFile = withContext(Dispatchers.IO) {
                    val cacheFolder = context.externalCacheDir ?: context.cacheDir
                    val updatesDir = File(cacheFolder, "updates").apply {
                        mkdirs()
                        listFiles()?.forEach { file ->
                            if (file.name.endsWith(".apk")) file.delete()
                        }
                    }
                    val targetFile = File(updatesDir, "dismod-update-${info.versionCode}.apk")

                    val request = Request.Builder().url(info.apkUrl).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("Failed to download APK: HTTP ${response.code}")
                        }
                        val body = response.body
                        val totalBytes = body.contentLength()

                        body.byteStream().use { input ->
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(8 * 1024)
                                var read: Int
                                var downloaded = 0L

                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    if (totalBytes > 0) {
                                        val progress = (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                                        _state.value = DismodUpdateState.Downloading(info, progress)
                                    }
                                }
                                output.flush()
                            }
                        }
                    }
                    targetFile
                }

                _state.value = DismodUpdateState.ReadyToInstall(info, apkFile)
                markVersionHandled(context, info.versionCode)
                installApk(context, apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "Update download failed", e)
                _state.value = DismodUpdateState.Error("Download failed: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Installs the downloaded APK via Android Package Installer.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer", e)
            _state.value = DismodUpdateState.Error("Failed to launch installer: ${e.localizedMessage}")
        }
    }

    fun markVersionHandled(context: Context, versionCode: Int) {
        try {
            val prefs = context.getSharedPreferences("dismod_updater", Context.MODE_PRIVATE)
            prefs.edit().putInt("dismissed_version_code", versionCode).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark version handled", e)
        }
    }

    fun dismiss(context: Context? = null, versionCode: Int? = null) {
        if (context != null && versionCode != null) {
            markVersionHandled(context, versionCode)
        }
        _state.value = DismodUpdateState.Idle
    }
}
