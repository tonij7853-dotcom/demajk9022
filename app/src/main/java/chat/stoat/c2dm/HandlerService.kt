package chat.stoat.c2dm

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import chat.stoat.BuildConfig
import chat.stoat.R
import chat.stoat.activities.MainActivity
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.ULID
import chat.stoat.api.routes.channel.fetchSingleChannel
import chat.stoat.api.settings.NotificationSettingsProvider
import chat.stoat.api.settings.SyncedSettings
import chat.stoat.c2dm.ChannelRegistrator.Companion.CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES
import chat.stoat.c2dm.ChannelRegistrator.Companion.CHANNEL_ID_GROUP_SOCIAL_FRIENDREQUESTS
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.persistence.Database
import chat.stoat.persistence.KVStorage
import chat.stoat.persistence.SqlStorage
import com.bumptech.glide.Glide
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import kotlin.math.abs

private val LETTER_ICON_COLORS = intArrayOf(
    0xFF1565C0.toInt(),
    0xFF2E7D32.toInt(),
    0xFF6A1B9A.toInt(),
    0xFFC62828.toInt(),
    0xFF00838F.toInt(),
    0xFFE65100.toInt(),
    0xFF4527A0.toInt(),
    0xFF283593.toInt(),
)

private fun generateLetterBitmap(name: String, sizePx: Int = 256): Bitmap {
    val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = LETTER_ICON_COLORS[abs(name.hashCode()) % LETTER_ICON_COLORS.size]

    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = color
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

    paint.color = Color.WHITE
    paint.textSize = sizePx * 0.45f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    val bounds = Rect()
    paint.getTextBounds(letter, 0, 1, bounds)
    canvas.drawText(letter, sizePx / 2f, sizePx / 2f + bounds.height() / 2f - bounds.bottom, paint)

    return bitmap
}

object NotificationID {
    const val NEW_MESSAGE = 0
    const val FRIEND_REQUEST = 1001
}

class HandlerService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        logcat(LogPriority.INFO) { "HandlerService: onNewToken received" }
        DismodPushManager.handleNewToken(token, this)
    }

    override fun onMessageReceived(fcmMessage: RemoteMessage) {
        val data = fcmMessage.data
        logcat(LogPriority.INFO) { "HandlerService: onMessageReceived payload: $data" }

        val type = data["type"] ?: ""
        if (type == "push.friend_request" || type == "friend_request" || type == "relationship" || data.containsKey("friend_request")) {
            handleFriendRequestPush(data, fcmMessage)
            return
        }

        handleChatMessagePush(data, fcmMessage)
    }

    private fun handleFriendRequestPush(data: Map<String, String>, fcmMessage: RemoteMessage) {
        val username = data["username"]
            ?: data["author_name"]
            ?: data["name"]
            ?: fcmMessage.notification?.title
            ?: getString(R.string.unknown)
        val userId = data["user_id"] ?: data["author"] ?: data["id"] ?: "unknown"

        val kv = KVStorage(this)
        val isEnabled = runBlocking { kv.getBoolean("notifications_enabled") } ?: true
        if (!isEnabled) return

        ChannelRegistrator(this).register()
        val notificationManager = NotificationManagerCompat.from(this)
        if (!notificationManager.areNotificationsEnabled()) return

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            userId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_GROUP_SOCIAL_FRIENDREQUESTS)
            .setSmallIcon(R.drawable.ic_stoat_24dp)
            .setContentTitle("Friend Request")
            .setContentText("$username sent you a friend request")
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify("friend_$userId", NotificationID.FRIEND_REQUEST, builder.build())
        }
    }

    private fun handleChatMessagePush(data: Map<String, String>, fcmMessage: RemoteMessage) {
        val channelId = data["channel"] ?: data["channel_id"] ?: data["tag"] ?: run {
            logcat(LogPriority.ERROR) { "No channel in push payload, abort" }
            return
        }

        val authorId = data["author"] ?: data["author_id"] ?: data["sender"].orEmpty()
        val body = data["body"] ?: data["content"] ?: data["message_content"] ?: fcmMessage.notification?.body ?: run {
            logcat(LogPriority.ERROR) { "No message body in push payload, abort" }
            return
        }

        val kv = KVStorage(this)
        val selfId = runBlocking { kv.get("selfId") }.orEmpty()

        // 1. Suppress if message sent by self
        if (selfId.isNotEmpty() && authorId == selfId) {
            logcat(LogPriority.DEBUG) { "Message sent by self, suppressing push notification" }
            return
        }

        // 2. Suppress if user is currently inside this exact channel in foreground
        if (ActiveChannelTracker.isAppInForeground && ActiveChannelTracker.activeChannelId == channelId) {
            logcat(LogPriority.DEBUG) { "App in foreground viewing channel $channelId, suppressing push" }
            return
        }

        // 3. Check if user turned off notifications globally
        val isNotificationsEnabled = runBlocking { kv.getBoolean("notifications_enabled") } ?: true
        if (!isNotificationsEnabled) {
            logcat(LogPriority.DEBUG) { "Notifications globally disabled, suppressing push" }
            return
        }

        val isMention = data["mention"] == "true" ||
                data["mentioned"] == "true" ||
                data["is_mention"] == "true" ||
                (selfId.isNotEmpty() && body.contains("<@$selfId>"))

        // 4. Check DND status (Presence = Busy)
        val selfPresence = runBlocking { kv.get("selfPresence") }
        if (selfPresence == "Busy" && !isMention) {
            logcat(LogPriority.DEBUG) { "User in DND and not mentioned, suppressing push" }
            return
        }

        val authorName = data["author_name"]
            ?: data["author_username"]
            ?: data["name"]
            ?: fcmMessage.notification?.title
            ?: getString(R.string.unknown)

        val image = data["image"] ?: data["avatar"] ?: data["icon"].orEmpty()
        val serverId = data["server"] ?: data["server_id"]
        val messageId = data["message"] ?: data["message_id"] ?: data["id"] ?: ULID.makeNext()
        val messageTimestamp = runCatching { ULID.asTimestamp(messageId) }.getOrNull() ?: System.currentTimeMillis()

        // 5. Check channel/server mute preferences
        SyncedSettings.initFromStorage(this)
        val db = Database(SqlStorage.driver)
        val dbChannel = runCatching { db.channelQueries.findById(channelId).executeAsOneOrNull() }.getOrNull()
        val resolvedServerId = serverId ?: dbChannel?.server

        val isMuted = NotificationSettingsProvider.isChannelMuted(channelId, resolvedServerId)
        if (isMuted && !isMention) {
            logcat(LogPriority.INFO) { "Channel $channelId is muted and not mentioned, suppressing push" }
            return
        }

        fun serverPrefix(sid: String?): String? {
            if (sid == null) return null
            return runCatching { db.serverQueries.findById(sid).executeAsOneOrNull()?.name }.getOrNull()
        }

        fun formatChannelName(type: String, name: String?, sid: String?): String {
            val base = when (type) {
                "DirectMessage" -> return authorName
                "TextChannel" -> "#${name ?: "channel"}"
                else -> name ?: return authorName
            }
            val prefix = serverPrefix(sid) ?: return base
            return "$prefix · $base"
        }

        val channelName = dbChannel?.let {
            formatChannelName(it.channelType, it.name, it.server)
        } ?: runBlocking {
            runCatching { fetchSingleChannel(channelId) }.getOrNull()?.let {
                formatChannelName(
                    it.channelType?.value ?: "",
                    it.name,
                    it.server
                )
            } ?: authorName
        }

        fun loadBitmap(url: String): Bitmap? = runCatching {
            Glide.with(this)
                .asBitmap()
                .load(url)
                .circleCrop()
                .timeout(800)
                .submit()
                .get(800, java.util.concurrent.TimeUnit.MILLISECONDS)
        }.getOrNull()

        val selfName = runBlocking { kv.get("selfName") }.orEmpty().ifEmpty { "Me" }
        val selfBitmap: Bitmap = generateLetterBitmap(selfName)

        val self = Person.Builder()
            .setBot(false)
            .setKey(selfId.ifEmpty { "self" })
            .setIcon(IconCompat.createWithBitmap(selfBitmap))
            .setName(selfName)
            .build()

        val authorBitmap = if (image.isNotEmpty()) {
            loadBitmap(image) ?: generateLetterBitmap(authorName)
        } else {
            generateLetterBitmap(authorName)
        }

        val conversationBitmap: Bitmap = when (dbChannel?.channelType) {
            "TextChannel", "VoiceChannel" -> {
                val server = resolvedServerId?.let { runCatching { db.serverQueries.findById(it).executeAsOneOrNull() }.getOrNull() }
                val iconUrl = server?.iconId?.let { "$STOAT_FILES/icons/$it" }
                (iconUrl?.let { loadBitmap(it) })
                    ?: generateLetterBitmap(server?.name ?: channelName)
            }
            "Group" -> {
                val iconUrl = dbChannel.iconId?.let { "$STOAT_FILES/icons/$it" }
                (iconUrl?.let { loadBitmap(it) })
                    ?: generateLetterBitmap(dbChannel.name ?: channelName)
            }
            else -> authorBitmap
        }

        val author = Person.Builder()
            .setBot(false)
            .setKey(authorId.ifEmpty { "author" })
            .setIcon(IconCompat.createWithBitmap(authorBitmap))
            .setName(authorName)
            .build()

        val shortcutId = "${BuildConfig.APPLICATION_ID}.channel.$channelId"

        val conversationIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("channelId", channelId)
            putExtra("messageId", messageId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val shortcut = ShortcutInfoCompat.Builder(this, shortcutId)
            .setShortLabel(channelName)
            .setLongLabel(channelName)
            .setIcon(IconCompat.createWithBitmap(conversationBitmap))
            .setIntent(conversationIntent)
            .setLongLived(true)
            .setPerson(author)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)

        val remoteInput = RemoteInput.Builder("content").run {
            setLabel(getString(R.string.message_context_sheet_actions_reply))
            build()
        }

        val replyIntent = Intent(this, ReplyReceiver::class.java).apply {
            putExtra("channelId", channelId)
        }

        val replyAction: NotificationCompat.Action =
            NotificationCompat.Action.Builder(
                R.drawable.ic_reply_24dp,
                getString(R.string.message_context_sheet_actions_reply),
                PendingIntent.getBroadcast(
                    this,
                    channelId.hashCode(),
                    replyIntent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
                .addRemoteInput(remoteInput)
                .build()

        val markAsReadIntent = Intent(this, MarkAsReadReceiver::class.java).apply {
            putExtra("channelId", channelId)
            putExtra("messageId", messageId)
        }

        val markAsReadAction: NotificationCompat.Action =
            NotificationCompat.Action.Builder(
                R.drawable.ic_mark_chat_read_24dp,
                getString(R.string.channel_context_sheet_actions_mark_read),
                PendingIntent.getBroadcast(
                    this,
                    channelId.hashCode() xor 1,
                    markAsReadIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
                .build()

        val contentIntent = PendingIntent.getActivity(
            this,
            channelId.hashCode(),
            conversationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationManager = NotificationManagerCompat.from(this)
        val existingStyle = notificationManager
            .activeNotifications
            .firstOrNull { it.tag == channelId && it.id == NotificationID.NEW_MESSAGE }
            ?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }

        val messagingStyle = (existingStyle ?: NotificationCompat.MessagingStyle(self))
            .setGroupConversation(dbChannel?.channelType != "DirectMessage")
            .setConversationTitle(channelName)
            .addMessage(body, messageTimestamp, author)

        // Ensure notification channels are registered
        ChannelRegistrator(this).register()

        // Discord-style notification group key: collapses notifications per channel or per server
        val groupKey = if (resolvedServerId != null) "dismod_server_$resolvedServerId" else "dismod_channel_$channelId"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES)
            .setSmallIcon(R.drawable.ic_stoat_24dp)
            .setContentTitle(if (dbChannel?.channelType == "DirectMessage") authorName else channelName)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setStyle(messagingStyle)
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addAction(replyAction)
            .addAction(markAsReadAction)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        // Android 11+ bubbles
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setShortcutId(shortcutId)
            builder.setLocusId(LocusIdCompat(shortcutId))

            val bubbleIntent = PendingIntent.getActivity(
                this,
                channelId.hashCode(),
                conversationIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val bubbleMetadata = NotificationCompat.BubbleMetadata.Builder(
                bubbleIntent,
                IconCompat.createWithBitmap(conversationBitmap)
            )
                .setDesiredHeight(600)
                .setAutoExpandBubble(false)
                .setSuppressNotification(false)
                .build()

            builder.setBubbleMetadata(bubbleMetadata)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Post conversation notification
        notificationManager.notify(channelId, NotificationID.NEW_MESSAGE, builder.build())

        // Post group summary notification so multiple conversations collapse like Discord
        val summaryTitle = serverPrefix(resolvedServerId) ?: channelName
        val summaryId = (resolvedServerId ?: channelId).hashCode()
        val summaryNotification = NotificationCompat.Builder(this, CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES)
            .setSmallIcon(R.drawable.ic_stoat_24dp)
            .setContentTitle(summaryTitle)
            .setContentText(getString(R.string.app_name))
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(summaryTitle))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        notificationManager.notify("summary_$groupKey", summaryId, summaryNotification)
    }
}
