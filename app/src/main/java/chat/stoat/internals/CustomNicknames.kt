package chat.stoat.internals

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import chat.stoat.api.StoatAPI
import chat.stoat.core.model.schemas.User

object CustomNicknames {
    private const val PREFS_NAME = "dismod_custom_nicknames"
    private const val PREFIX = "nick_"

    val dmNicknames = mutableStateMapOf<String, String>()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        sp.all.forEach { (key, value) ->
            if (key.startsWith(PREFIX) && value is String && value.isNotBlank()) {
                dmNicknames[key.removePrefix(PREFIX)] = value
            }
        }
    }

    private fun ensurePrefs(context: Context): SharedPreferences {
        return prefs ?: run {
            init(context)
            prefs ?: context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        }
    }

    fun getNickname(userId: String): String? {
        return dmNicknames[userId]?.takeIf { it.isNotBlank() }
    }

    fun setNickname(context: Context, userId: String, nickname: String?) {
        val sp = ensurePrefs(context)
        val clean = nickname?.trim()
        if (clean.isNullOrBlank()) {
            dmNicknames.remove(userId)
            sp.edit().remove("$PREFIX$userId").apply()
        } else {
            dmNicknames[userId] = clean
            sp.edit().putString("$PREFIX$userId", clean).apply()
        }
    }

    fun resolveName(userId: String, serverId: String? = null): String {
        if (serverId != null) {
            val serverNick = StoatAPI.members.getMember(serverId, userId)?.nickname
            if (!serverNick.isNullOrBlank()) {
                return serverNick
            }
        }
        val customNick = getNickname(userId)
        if (!customNick.isNullOrBlank()) {
            return customNick
        }
        val user = StoatAPI.userCache[userId]
        return user?.let { User.resolveDefaultName(it) } ?: userId
    }

    fun resolveName(user: User, serverId: String? = null): String {
        if (serverId != null && user.id != null) {
            val serverNick = StoatAPI.members.getMember(serverId, user.id!!)?.nickname
            if (!serverNick.isNullOrBlank()) {
                return serverNick
            }
        }
        val dmNick = user.id?.let { getNickname(it) }
        if (!dmNick.isNullOrBlank()) {
            return dmNick
        }
        return User.resolveDefaultName(user)
    }
}
