package chat.stoat.c2dm

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import chat.stoat.R
import chat.stoat.activities.MainActivity
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.MessageFlag
import chat.stoat.api.internals.has
import chat.stoat.api.realtime.frames.receivable.MessageFrame
import chat.stoat.api.realtime.frames.receivable.UserRelationshipFrame
import chat.stoat.c2dm.ChannelRegistrator.Companion.CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES
import chat.stoat.c2dm.ChannelRegistrator.Companion.CHANNEL_ID_GROUP_SOCIAL_FRIENDREQUESTS
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.User
import chat.stoat.persistence.KVStorage
import kotlinx.coroutines.runBlocking

object ActiveChannelTracker {
    var activeChannelId: String? = null
    var isAppInForeground: Boolean = false
}

object DismodNotificationPoster {
    private const val TAG = "DismodNotification"

    @Volatile
    var areNotificationsEnabled: Boolean? = null

    fun postMessageNotification(context: Context, messageFrame: MessageFrame) {
        try {
            val channelId = messageFrame.channel ?: return
            val authorId = messageFrame.author ?: return

            // Do not notify for user's own messages
            if (authorId == StoatAPI.selfId) return

            // Only suppress notification if user is in foreground AND actively viewing THIS exact channel
            if (ActiveChannelTracker.isAppInForeground && ActiveChannelTracker.activeChannelId == channelId) {
                return
            }

            // Ensure notification channels are registered (cached internally)
            ChannelRegistrator(context).register()

            // Check if notifications are enabled
            val notificationManager = NotificationManagerCompat.from(context)
            if (!notificationManager.areNotificationsEnabled()) return

            val isEnabled = areNotificationsEnabled ?: run {
                val kv = KVStorage(context)
                (runBlocking { kv.getBoolean("notifications_enabled") } ?: true).also {
                    areNotificationsEnabled = it
                }
            }
            if (!isEnabled) return

            // Resolve channel title
            val channel = StoatAPI.channelCache[channelId]
            val isDm = channel?.channelType == ChannelType.DirectMessage
            val isGroup = channel?.channelType == ChannelType.Group

            // Resolve author display name
            val author = StoatAPI.userCache[authorId]
            val authorName = messageFrame.masquerade?.name
                ?: author?.let { chat.stoat.internals.CustomNicknames.resolveName(it, channel?.server) }
                ?: chat.stoat.internals.CustomNicknames.getNickname(authorId)
                ?: context.getString(R.string.unknown)

            val selfId = StoatAPI.selfId
            val isMentioned = if (selfId == null) false else {
                val directMention = messageFrame.mentions?.contains(selfId) == true ||
                        messageFrame.content?.contains("<@$selfId>") == true ||
                        messageFrame.content?.contains("<@!$selfId>") == true
                val everyoneMention = (messageFrame.flags != null && (
                        (messageFrame.flags has MessageFlag.MentionsEveryone) ||
                        (messageFrame.flags has MessageFlag.MentionsOnline))) ||
                        messageFrame.content?.contains("@everyone") == true ||
                        messageFrame.content?.contains("@here") == true
                val roleMention = run {
                    val serverId = channel?.server ?: return@run false
                    val selfMember = StoatAPI.members.getMember(serverId, selfId) ?: return@run false
                    val mentionedRoleIds = chat.stoat.internals.text.MessageProcessor.findMentionedRoleIDs(messageFrame.content)
                    selfMember.roles?.any { it in mentionedRoleIds } == true
                }
                directMention || everyoneMention || roleMention
            }

            val notificationTitle = when {
                isDm -> authorName
                isGroup -> if (isMentioned) "Mentioned by $authorName in ${channel?.name ?: "Group"}" else "$authorName in ${channel?.name ?: "Group"}"
                channel?.server != null -> {
                    val server = StoatAPI.serverCache[channel.server]
                    val serverName = server?.name
                    val chName = channel.name?.let { "#$it" } ?: "#channel"
                    val location = if (serverName != null) "$serverName · $chName" else chName
                    if (isMentioned) "Mentioned by $authorName ($location)" else "$authorName ($location)"
                }
                else -> authorName
            }

            // Text-only body formatting (never load or display GIFs / media bitmaps)
            val rawContent = messageFrame.content?.trim().orEmpty()
            val body = if (rawContent.isNotEmpty()) {
                if (!messageFrame.attachments.isNullOrEmpty()) {
                    val count = messageFrame.attachments!!.size
                    "$rawContent [${if (count > 1) "$count attachments" else "Attachment"}]"
                } else {
                    rawContent
                }
            } else if (!messageFrame.attachments.isNullOrEmpty()) {
                val first = messageFrame.attachments!!.first()
                val filename = first.filename?.lowercase().orEmpty()
                val type = first.metadata?.type
                when {
                    filename.endsWith(".gif") || first.contentType?.contains("gif", ignoreCase = true) == true -> "[GIF]"
                    type == "Image" -> "[Image]"
                    type == "Video" -> "[Video]"
                    type == "Audio" -> "[Audio]"
                    else -> "[Attachment: ${first.filename ?: "File"}]"
                }
            } else {
                "[Message]"
            }

            val conversationIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("channelId", channelId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val contentIntent = PendingIntent.getActivity(
                context,
                channelId.hashCode(),
                conversationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val replyIntent = Intent(context, ReplyReceiver::class.java).apply {
                putExtra("channelId", channelId)
            }

            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_reply_24dp,
                context.getString(R.string.message_context_sheet_actions_reply),
                PendingIntent.getBroadcast(
                    context,
                    channelId.hashCode(),
                    replyIntent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            ).addRemoteInput(
                RemoteInput.Builder("content")
                    .setLabel(context.getString(R.string.message_context_sheet_actions_reply))
                    .build()
            ).build()

            val markAsReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
                putExtra("channelId", channelId)
                putExtra("messageId", messageFrame.id)
            }

            val markAsReadAction = NotificationCompat.Action.Builder(
                R.drawable.ic_mark_chat_read_24dp,
                context.getString(R.string.channel_context_sheet_actions_mark_read),
                PendingIntent.getBroadcast(
                    context,
                    channelId.hashCode() xor 1,
                    markAsReadIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            ).build()

            val builder = NotificationCompat.Builder(context, CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES)
                .setSmallIcon(R.drawable.ic_stoat_24dp)
                .setContentTitle(notificationTitle)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(0, 200, 100, 200))
                .setAutoCancel(true)
                .addAction(replyAction)
                .addAction(markAsReadAction)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(channelId, NotificationID.NEW_MESSAGE, builder.build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post message notification", e)
        }
    }

    fun postFriendRequestNotification(context: Context, frame: UserRelationshipFrame) {
        try {
            if (frame.status != "Incoming") return
            ChannelRegistrator(context).register()

            val notificationManager = NotificationManagerCompat.from(context)
            if (!notificationManager.areNotificationsEnabled()) return

            val kv = KVStorage(context)
            val isEnabled = runBlocking { kv.getBoolean("notifications_enabled") } ?: true
            if (!isEnabled) return

            val username = frame.user.username ?: context.getString(R.string.unknown)

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                frame.id.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID_GROUP_SOCIAL_FRIENDREQUESTS)
                .setSmallIcon(R.drawable.ic_stoat_24dp)
                .setContentTitle("Friend Request")
                .setContentText("$username sent you a friend request")
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify("friend_${frame.id}", 1001, builder.build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post friend request notification", e)
        }
    }
}
