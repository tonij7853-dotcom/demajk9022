package chat.stoat.api.unreads

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import chat.stoat.StoatApplication
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.MessageFlag
import chat.stoat.api.internals.ULID
import chat.stoat.api.internals.has
import chat.stoat.api.routes.channel.ackChannel
import chat.stoat.api.routes.server.ackServer
import chat.stoat.api.routes.sync.syncUnreads
import chat.stoat.c2dm.ActiveChannelTracker
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.ChannelUnread
import chat.stoat.core.model.schemas.Message
import chat.stoat.api.settings.NotificationSettingsProvider
import chat.stoat.internals.text.MessageProcessor

class Unreads {
    private val hasLoaded = mutableStateOf(false)
    val channels = mutableStateMapOf<String, ChannelUnread>()

    suspend fun sync() {
        channels.clear()
        channels.putAll(
            try {
                syncUnreads().associate {
                    it.id.channel to ChannelUnread(
                        id = it.id.channel,
                        last_id = it.last_id,
                        mentions = it.mentions ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                Log.e("Unreads", "Failed to sync unreads", e)
                emptyMap()
            }
        )
        hasLoaded.value = true
    }

    fun getForChannel(channelId: String, serverId: String?): ChannelUnread? {
        if (!hasLoaded.value) return null
        if (NotificationSettingsProvider.isChannelMuted(channelId, serverId)) return null
        return channels[channelId]
    }

    fun hasUnread(channelId: String, lastMessageId: String, serverId: String?): Boolean {
        if (!hasLoaded.value) return false
        if (NotificationSettingsProvider.isChannelMuted(channelId, serverId)) return false
        val unread = channels[channelId] ?: return false
        val lastAckId = unread.last_id
        if (lastAckId == null) {
            return (unread.mentions?.isNotEmpty() == true)
        }
        return lastAckId.compareTo(lastMessageId) < 0
    }

    fun getMentionCount(channelId: String): Int {
        if (channelId.isBlank()) return 0
        return channels[channelId]?.mentions?.size ?: 0
    }

    fun hasMentions(channelId: String): Boolean {
        return getMentionCount(channelId) > 0
    }

    fun getServerMentionCount(serverId: String): Int {
        if (serverId.isBlank()) return 0
        val server = StoatAPI.serverCache[serverId] ?: return 0
        var total = 0
        server.channels?.forEach { chId ->
            if (!NotificationSettingsProvider.isChannelMuted(chId, serverId)) {
                total += getMentionCount(chId)
            }
        }
        return total
    }

    fun serverHasUnread(serverId: String): Boolean {
        if (!hasLoaded.value) return false

        return StoatAPI.serverCache[serverId]?.channels?.any {
            val channel = StoatAPI.channelCache[it] ?: return@any false
            if (channel.channelType == ChannelType.VoiceChannel) return@any false
            if (NotificationSettingsProvider.isChannelMuted(it, serverId)) return@any false
            hasUnread(it, channel.lastMessageID ?: "", serverId) || hasMentions(it)
        } == true
    }

    fun getTotalCommunitiesMentionCount(): Int {
        var total = 0
        StoatAPI.serverCache.keys.forEach { srvId ->
            total += getServerMentionCount(srvId)
        }
        return total
    }

    fun getTotalConversationsMentionCount(): Int {
        var total = 0
        StoatAPI.channelCache.values.forEach { channel ->
            val chId = channel.id
            if (channel.server == null && chId != null) {
                val mentions = getMentionCount(chId)
                if (mentions > 0) {
                    total += mentions
                } else if (hasUnread(chId, channel.lastMessageID ?: "", null)) {
                    total += 1
                }
            }
        }
        return total
    }

    fun recordIncomingMessage(message: Message) {
        val selfId = StoatAPI.selfId ?: return
        if (message.author == selfId) return
        val channelId = message.channel ?: return
        val messageId = message.id ?: return

        val isActivelyViewing = ActiveChannelTracker.isAppInForeground && ActiveChannelTracker.activeChannelId == channelId
        if (isActivelyViewing) {
            return
        }

        val channel = StoatAPI.channelCache[channelId]
        val isDirectMessage = channel?.channelType == ChannelType.DirectMessage

        val isDirectMention = message.mentions?.contains(selfId) == true ||
                message.content?.contains("<@$selfId>") == true ||
                message.content?.contains("<@!$selfId>") == true

        val isEveryoneMention = (message.flags has MessageFlag.MentionsEveryone) ||
                (message.flags has MessageFlag.MentionsOnline) ||
                message.content?.contains("@everyone") == true ||
                message.content?.contains("@here") == true

        val isRoleMention = run {
            val serverId = channel?.server ?: return@run false
            val selfMember = StoatAPI.members.getMember(serverId, selfId) ?: return@run false
            val mentionedRoleIds = MessageProcessor.findMentionedRoleIDs(message.content)
            selfMember.roles?.any { it in mentionedRoleIds } == true
        }

        val isMentioned = isDirectMessage || isDirectMention || isEveryoneMention || isRoleMention

        val current = channels[channelId] ?: ChannelUnread(
            id = channelId,
            last_id = null,
            mentions = emptyList()
        )
        val currentMentions = current.mentions?.toMutableList() ?: mutableListOf()
        if (isMentioned && !currentMentions.contains(messageId)) {
            currentMentions.add(messageId)
        }

        channels[channelId] = current.copy(mentions = currentMentions)

        // Play in-app sound & haptic ping if app is in foreground
        if (isMentioned && ActiveChannelTracker.isAppInForeground) {
            playMentionPing()
        }
    }

    private fun playMentionPing() {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(StoatApplication.instance, notificationUri)
            ringtone?.play()
        } catch (_: Exception) {}
        try {
            val ctx = StoatApplication.instance
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(180)
            }
        } catch (_: Exception) {}
    }

    suspend fun markAsRead(channelId: String, messageId: String, sync: Boolean = true) {
        val current = channels[channelId]
        channels[channelId] = (current ?: ChannelUnread(id = channelId, last_id = messageId)).copy(
            last_id = messageId,
            mentions = emptyList()
        )
        if (sync) {
            try {
                ackChannel(channelId, messageId)
            } catch (e: Exception) {
                Log.e("Unreads", "Failed to ack channel $channelId", e)
            }
        }
    }

    fun processExternalAck(channelId: String, messageId: String) {
        val current = channels[channelId]
        channels[channelId] = (current ?: ChannelUnread(id = channelId, last_id = messageId)).copy(
            last_id = messageId,
            mentions = emptyList()
        )
    }

    suspend fun markServerAsRead(serverId: String, sync: Boolean = true) {
        val server = StoatAPI.serverCache[serverId] ?: return
        server.channels?.forEach { chId ->
            val current = channels[chId]
            channels[chId] = (current ?: ChannelUnread(id = chId, last_id = ULID.makeNext())).copy(
                last_id = ULID.makeNext(),
                mentions = emptyList()
            )
        }

        if (sync) {
            try {
                ackServer(serverId)
            } catch (e: Exception) {
                Log.e("Unreads", "Failed to ack server $serverId", e)
            }
        }
    }

    fun getAllUnreads(): List<ChannelUnread> {
        if (!hasLoaded.value) return emptyList()
        return channels.values.toList()
    }

    fun hasAnyUnreads(): Boolean {
        if (!hasLoaded.value) return false

        for ((channelId, unread) in StoatAPI.channelCache) {
            if (channelId !in channels) continue
            if (NotificationSettingsProvider.isChannelMuted(channelId, unread.server)) continue
            if (hasUnread(channelId, unread.lastMessageID ?: "", unread.server) || hasMentions(channelId)) {
                return true
            }
        }
        return false
    }

    fun countChannelsWithUnreads(): Int? {
        if (!hasLoaded.value) return null

        var count = 0
        for ((channelId, unread) in StoatAPI.channelCache) {
            if (channelId !in channels) continue
            if (NotificationSettingsProvider.isChannelMuted(channelId, unread.server)) continue
            if (hasUnread(channelId, unread.lastMessageID ?: "", unread.server) || hasMentions(channelId)) {
                count++
            }
        }
        return count
    }

    fun clear() {
        channels.clear()
        hasLoaded.value = false
    }
}
