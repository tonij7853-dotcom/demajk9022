package chat.stoat.screens.settings

import android.app.Application
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import chat.stoat.sheets.GifPickerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.microservices.autumn.uploadToAutumn
import chat.stoat.api.routes.user.fetchUserProfile
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.ui.text.font.FontWeight
import chat.stoat.api.routes.user.patchSelf
import chat.stoat.composables.generic.InlineMediaPicker
import chat.stoat.composables.profile.ProfileCosmeticsSettings
import chat.stoat.composables.screens.settings.RawUserOverview
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.Profile
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request

class ProfileSettingsScreenViewModel(val context: Application) :
    ViewModel() {
    var isLoading by mutableStateOf(true)
    var pfpModel by mutableStateOf<Any?>(null)
    var currentProfile by mutableStateOf<Profile?>(null)
    var pendingProfile by mutableStateOf<Profile?>(null)
    var backgroundModel by mutableStateOf<Any?>(null)
    var uploadProgress by mutableFloatStateOf(0f)
    var uploadError by mutableStateOf<String?>(null)
    var bioError by mutableStateOf<String?>(null)
    var currentPronouns by mutableStateOf<String?>(null)
    var pendingPronouns by mutableStateOf("")
    var pronounsError by mutableStateOf<String?>(null)

    var pendingPfpUri by mutableStateOf<Uri?>(null)
    var pendingBannerUri by mutableStateOf<Uri?>(null)
    var isSavingPfp by mutableStateOf(false)
    var isSavingBanner by mutableStateOf(false)

    init {
        StoatAPI.selfId?.let { self ->
            StoatAPI.userCache[self]?.let { user ->
                user.avatar?.id?.let {
                    pfpModel = "$STOAT_FILES/avatars/${it}"
                }
                currentPronouns = user.pronouns
                pendingPronouns = user.pronouns.orEmpty()
            }
            viewModelScope.launch {
                currentProfile = fetchUserProfile(self)
                currentProfile!!.background?.id?.let {
                    backgroundModel = "$STOAT_FILES/backgrounds/${it}"
                }

                pendingProfile = currentProfile!!.copy()

                isLoading = false
            }
        }

    }

    private fun prepareImageForUpload(uri: Uri, prefix: String): Pair<File, ContentType> {
        val inputStreamSupplier: () -> InputStream? = {
            if (uri.scheme == "http" || uri.scheme == "https") {
                val client = OkHttpClient()
                val resp = client.newCall(Request.Builder().url(uri.toString()).build()).execute()
                if (resp.isSuccessful) resp.body?.byteStream() else null
            } else {
                context.contentResolver.openInputStream(uri)
            }
        }

        // Check if it's an animated GIF by inspecting first 6 bytes
        val isGif = try {
            inputStreamSupplier()?.use { stream ->
                val header = ByteArray(6)
                val count = stream.read(header)
                count >= 6 && header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
                        header[2] == 'F'.code.toByte() && header[3] == '8'.code.toByte()
            } ?: false
        } catch (e: Exception) {
            false
        }

        if (isGif) {
            val filename = "${prefix}_${System.currentTimeMillis()}.gif"
            val mFile = File(context.cacheDir, filename)
            mFile.outputStream().use { output ->
                inputStreamSupplier()?.use { input ->
                    input.copyTo(output)
                }
            }
            return Pair(mFile, ContentType.Image.GIF)
        }

        // For all still photos (PNG, JPEG, WebP, HEIC, camera photo):
        // Decode without downscaling to get the exact original resolution in 32-bit ARGB_8888
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = try {
            inputStreamSupplier()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            null
        }

        if (bitmap == null) {
            // Fallback: copy raw bytes directly if BitmapFactory doesn't decode
            val filename = "${prefix}_${System.currentTimeMillis()}.png"
            val mFile = File(context.cacheDir, filename)
            mFile.outputStream().use { output ->
                inputStreamSupplier()?.use { input ->
                    input.copyTo(output)
                }
            }
            return Pair(mFile, ContentType.Image.PNG)
        }

        // Cap max dimension to 4096 to prevent exceeding Autumn's file size limit while guaranteeing ultra-crisp resolution
        val maxDim = 4096
        val processedBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            val targetW = (bitmap.width * ratio).toInt()
            val targetH = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        // Encode as 100% lossless PNG
        val filename = "${prefix}_${System.currentTimeMillis()}.png"
        val mFile = File(context.cacheDir, filename)
        mFile.outputStream().use { output ->
            processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.flush()
        }

        return Pair(mFile, ContentType.Image.PNG)
    }

    fun saveNewPfp() {
        uploadError = null

        val uri = pendingPfpUri ?: when (pfpModel) {
            is Uri -> pfpModel as Uri
            is String -> Uri.parse(pfpModel as String)
            else -> return
        }

        isSavingPfp = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (mFile, contentType) = prepareImageForUpload(uri, "avatar")
                val id = uploadToAutumn(
                    mFile,
                    mFile.name,
                    "avatars",
                    contentType,
                    onProgress = { soFar, outOf ->
                        uploadProgress = soFar.toFloat() / outOf.toFloat()
                    }
                )

                patchSelf(avatar = id)

                withContext(Dispatchers.Main) {
                    pfpModel = StoatAPI.userCache[StoatAPI.selfId]?.avatar?.id?.let {
                        "$STOAT_FILES/avatars/${it}"
                    }
                    pendingPfpUri = null
                    isSavingPfp = false
                    uploadProgress = 0f
                    Toast.makeText(context, "Profile picture saved in full quality!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uploadError = e.message
                    uploadProgress = 0f
                    isSavingPfp = false
                    Toast.makeText(context, "Failed to save avatar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveNewBackground() {
        uploadError = null

        val uri = pendingBannerUri ?: when (backgroundModel) {
            is Uri -> backgroundModel as Uri
            is String -> Uri.parse(backgroundModel as String)
            else -> return
        }

        isSavingBanner = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (mFile, contentType) = prepareImageForUpload(uri, "background")
                val id = uploadToAutumn(
                    mFile,
                    mFile.name,
                    "backgrounds",
                    contentType,
                    onProgress = { soFar, outOf ->
                        uploadProgress = soFar.toFloat() / outOf.toFloat()
                    }
                )

                patchSelf(background = id)

                val profile = StoatAPI.selfId?.let { fetchUserProfile(it) }
                withContext(Dispatchers.Main) {
                    if (profile != null) {
                        currentProfile = profile
                        pendingProfile = profile
                        backgroundModel = profile.background?.id?.let {
                            "$STOAT_FILES/backgrounds/${it}"
                        }
                    }
                    pendingBannerUri = null
                    isSavingBanner = false
                    uploadProgress = 0f
                    Toast.makeText(context, "Banner saved in full quality!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uploadError = e.message
                    uploadProgress = 0f
                    isSavingBanner = false
                    Toast.makeText(context, "Failed to save banner: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun removePfp() {
        pendingPfpUri = null
        viewModelScope.launch {
            patchSelf(remove = listOf("Avatar"))
            pfpModel = null
            Toast.makeText(context, "Profile picture removed", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeBackground() {
        pendingBannerUri = null
        viewModelScope.launch {
            patchSelf(remove = listOf("ProfileBackground"))
            backgroundModel = null
            Toast.makeText(context, "Banner removed", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveBio() {
        bioError = null
        viewModelScope.launch {
            try {
                patchSelf(bio = pendingProfile?.content)

                fetchUserProfile(StoatAPI.selfId!!).let {
                    currentProfile = it
                    pendingProfile = it
                }
            } catch (e: Exception) {
                bioError = e.message
            }
        }
    }

    fun savePronouns() {
        pronounsError = null
        val normalizedPronouns = pendingPronouns.trim()

        viewModelScope.launch {
            try {
                if (normalizedPronouns.isEmpty()) {
                    patchSelf(remove = listOf("Pronouns"))
                } else {
                    patchSelf(pronouns = normalizedPronouns)
                }

                currentPronouns = normalizedPronouns.ifEmpty { null }
                pendingPronouns = normalizedPronouns
            } catch (e: Exception) {
                pronounsError = e.message
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    navController: NavController,
    viewModel: ProfileSettingsScreenViewModel = koinViewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.settings_profile),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                actions = {
                    if (viewModel.pendingPfpUri != null || viewModel.pendingBannerUri != null) {
                        TextButton(
                            onClick = {
                                if (viewModel.pendingPfpUri != null && !viewModel.isSavingPfp) {
                                    viewModel.saveNewPfp()
                                }
                                if (viewModel.pendingBannerUri != null && !viewModel.isSavingBanner) {
                                    viewModel.saveNewBackground()
                                }
                            }
                        ) {
                            Text(
                                text = "Save All",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        },
    ) { pv ->
        Box(
            Modifier
                .padding(pv)
                .imePadding()
        ) {
            val scrollState = rememberScrollState()
            var showGifPickerForAvatar by remember { mutableStateOf(false) }
            var showGifPickerForBanner by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (viewModel.isLoading) {
                            Modifier
                        } else {
                            Modifier.verticalScroll(scrollState)
                        }
                    ),
                verticalArrangement = if (viewModel.isLoading) {
                    Arrangement.Center
                } else {
                    Arrangement.Top
                },
                horizontalAlignment = if (viewModel.isLoading) {
                    Alignment.CenterHorizontally
                } else {
                    Alignment.Start
                }
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                    )
                } else {
                    StoatAPI.userCache[StoatAPI.selfId]?.let {
                        RawUserOverview(
                            it,
                            viewModel.pendingProfile,
                            viewModel.pfpModel?.toString(),
                            viewModel.backgroundModel?.toString()
                        )
                    }

                    ProfileCosmeticsSettings(StoatAPI.selfId)

                    AnimatedVisibility(visible = viewModel.uploadProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { viewModel.uploadProgress },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
                        )
                    }

                    AnimatedVisibility(visible = viewModel.uploadError != null) {
                        Text(
                            text = viewModel.uploadError ?: "",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.settings_profile_profile_picture),
                                style = MaterialTheme.typography.labelLarge
                            )

                            Spacer(Modifier.height(10.dp))

                            InlineMediaPicker(
                                currentModel = viewModel.pfpModel,
                                circular = true,
                                useAvatarCircularity = true,
                                onPick = {
                                    viewModel.pendingPfpUri = it
                                    viewModel.pfpModel = it.toString()
                                },
                                canRemove = true,
                                onRemove = {
                                    viewModel.removePfp()
                                }
                            )

                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.saveNewPfp() },
                                    enabled = (viewModel.pendingPfpUri != null || viewModel.pfpModel != null) && !viewModel.isSavingPfp
                                ) {
                                    if (viewModel.isSavingPfp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Saving...")
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_check_24dp),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save Profile Pic")
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showGifPickerForAvatar = true }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_photo_library_24dp),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Choose GIF", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.settings_profile_custom_background),
                                style = MaterialTheme.typography.labelLarge,
                            )

                            Spacer(Modifier.height(10.dp))

                            InlineMediaPicker(
                                currentModel = viewModel.backgroundModel,
                                onPick = {
                                    viewModel.pendingBannerUri = it
                                    viewModel.backgroundModel = it.toString()
                                },
                                canRemove = true,
                                onRemove = {
                                    viewModel.removeBackground()
                                }
                            )

                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.saveNewBackground() },
                                    enabled = (viewModel.pendingBannerUri != null || viewModel.backgroundModel != null) && !viewModel.isSavingBanner
                                ) {
                                    if (viewModel.isSavingBanner) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Saving...")
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_check_24dp),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save Banner")
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showGifPickerForBanner = true }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_photo_library_24dp),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Choose GIF Banner", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 20.dp)
                    ) {
                        OutlinedTextField(
                            value = viewModel.pendingPronouns,
                            onValueChange = { value ->
                                if (value.length <= 24) {
                                    viewModel.pendingPronouns = value
                                }
                            },
                            label = {
                                Text(
                                    text = stringResource(id = R.string.settings_profile_pronouns),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            isError = viewModel.pronounsError != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        AnimatedVisibility(visible = viewModel.pronounsError != null) {
                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = viewModel.pronounsError ?: "",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                viewModel.savePronouns()
                            },
                            enabled = viewModel.pendingPronouns.trim().ifEmpty { null } !=
                                viewModel.currentPronouns,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_24dp),
                                contentDescription = null
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = stringResource(id = R.string.settings_profile_save),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = viewModel.pendingProfile?.content ?: "",
                            onValueChange = { value ->
                                viewModel.pendingProfile?.let {
                                    viewModel.pendingProfile = it.copy(content = value)
                                }
                            },
                            label = {
                                Text(
                                    text = stringResource(id = R.string.user_info_sheet_category_bio),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        AnimatedVisibility(visible = viewModel.bioError != null) {
                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = viewModel.bioError ?: "",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                viewModel.saveBio()
                            },
                            enabled = viewModel.pendingProfile?.content != viewModel.currentProfile?.content,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_24dp),
                                contentDescription = null
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = stringResource(id = R.string.settings_profile_save),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (showGifPickerForAvatar) {
                GifPickerSheet(
                    onDismissRequest = { showGifPickerForAvatar = false },
                    onGifSelected = { uri ->
                        showGifPickerForAvatar = false
                        viewModel.pfpModel = uri
                        viewModel.saveNewPfp()
                    }
                )
            }

            if (showGifPickerForBanner) {
                GifPickerSheet(
                    onDismissRequest = { showGifPickerForBanner = false },
                    onGifSelected = { uri ->
                        showGifPickerForBanner = false
                        viewModel.backgroundModel = uri
                        viewModel.saveNewBackground()
                    }
                )
            }
        }
    }
}
