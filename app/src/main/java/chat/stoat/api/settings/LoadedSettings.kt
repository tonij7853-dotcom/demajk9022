package chat.stoat.api.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import chat.stoat.StoatApplication
import chat.stoat.core.model.schemas.AndroidSpecificSettingsSpecialEmbedSettings
import chat.stoat.ui.theme.Theme
import chat.stoat.ui.theme.getDefaultFont
import chat.stoat.ui.theme.getDefaultTheme

enum class MessageReplyStyle {
    None,
    SwipeFromEnd,
    DoubleTap
}

enum class UserInterfaceFont {
    Default,
    GoogleSansFlex,
}

typealias SpecialEmbedSettings = AndroidSpecificSettingsSpecialEmbedSettings

object LoadedSettings {
    var theme by mutableStateOf(getDefaultTheme())
    var messageReplyStyle by mutableStateOf(MessageReplyStyle.SwipeFromEnd)
    var avatarRadius by mutableIntStateOf(50)
    var experimentsEnabled by mutableStateOf(false)
    var specialEmbedSettings by mutableStateOf(SpecialEmbedSettings())
    var poorlyFormedSettingsKeys by mutableStateOf(emptySet<String>())
    var font by mutableStateOf(getDefaultFont())

    private const val PREFS_NAME = "dismod_loaded_settings"

    fun initFromStorage(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString("theme", null)?.let {
            try {
                theme = if (it == "Revolt") Theme.Default else Theme.valueOf(it)
            } catch (_: Exception) {}
        }
        prefs.getString("font", null)?.let {
            try {
                font = UserInterfaceFont.valueOf(it)
            } catch (_: Exception) {}
        }
        if (prefs.contains("avatarRadius")) {
            avatarRadius = prefs.getInt("avatarRadius", 50)
        }
        prefs.getString("messageReplyStyle", null)?.let {
            try {
                messageReplyStyle = MessageReplyStyle.valueOf(it)
            } catch (_: Exception) {}
        }
    }

    fun saveToStorage(context: Context? = null) {
        val ctx = context ?: runCatching { StoatApplication.instance }.getOrNull() ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("theme", theme.name)
            .putString("font", font.name)
            .putInt("avatarRadius", avatarRadius)
            .putString("messageReplyStyle", messageReplyStyle.name)
            .apply()
    }

    fun hydrateWithSettings(settings: SyncedSettings) {
        this.theme = settings.android.theme?.let {
            if (it == "Revolt") Theme.Default else Theme.valueOf(it)
        } ?: getDefaultTheme()
        this.messageReplyStyle =
            settings.android.messageReplyStyle?.let {
                if (it == "None") MessageReplyStyle.SwipeFromEnd else {
                    try {
                        MessageReplyStyle.valueOf(it)
                    } catch (e: Exception) {
                        MessageReplyStyle.SwipeFromEnd
                    }
                }
            } ?: MessageReplyStyle.SwipeFromEnd
        this.avatarRadius = settings.android.avatarRadius ?: 50
        this.specialEmbedSettings = settings.android.specialEmbedSettings ?: SpecialEmbedSettings()
        this.font = settings.android.font?.let {
            try {
                UserInterfaceFont.valueOf(it)
            } catch (e: Exception) {
                null
            }
        } ?: getDefaultFont()
        saveToStorage()
    }

    fun reset() {
        theme = getDefaultTheme()
        messageReplyStyle = MessageReplyStyle.SwipeFromEnd
        avatarRadius = 50
        specialEmbedSettings = SpecialEmbedSettings()
        poorlyFormedSettingsKeys = emptySet()
        font = getDefaultFont()
        saveToStorage()
    }
}
