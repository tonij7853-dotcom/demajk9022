package chat.stoat.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import chat.stoat.api.internals.FriendRequests
import chat.stoat.screens.main.dialogs.AddFriendDialog
import chat.stoat.screens.main.dialogs.NotificationsSheet
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.realtime.DisconnectionState
import chat.stoat.api.realtime.RealtimeSocket
import chat.stoat.api.internals.ChannelUtils
import chat.stoat.api.settings.LoadedSettings
import chat.stoat.composables.generic.GroupIcon
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.composables.generic.presenceFromStatus
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.User
import chat.stoat.internals.extensions.zero
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(navController: NavController) {
    val context = LocalContext.current

    val notesChannel = remember(StoatAPI.channelCache.values.toList()) {
        StoatAPI.channelCache.values.firstOrNull { it.channelType == ChannelType.SavedMessages }
    }

    val dmAbleChannels = remember(StoatAPI.channelCache.values.toList()) {
        StoatAPI.channelCache.values
            .filter { it.channelType == ChannelType.DirectMessage || it.channelType == ChannelType.Group }
            .filter {
                if (it.channelType == ChannelType.DirectMessage) {
                    it.active == true || it.lastMessageID != null
                } else true
            }
            .sortedBy { it.lastMessageID ?: it.id }
            .reversed()
    }

    val isConnecting = RealtimeSocket.disconnectionState != DisconnectionState.Connected

    var showNotificationsSheet by rememberSaveable { mutableStateOf(false) }
    var showAddFriendDialog by rememberSaveable { mutableStateOf(false) }

    val incomingRequestsCount = remember(StoatAPI.userCache.values.toList()) {
        FriendRequests.getIncoming().size
    }

    val unreadServersCount = remember(StoatAPI.serverCache.values.toList(), StoatAPI.channelCache.values.toList()) {
        StoatAPI.serverCache.values.count { server ->
            val sid = server.id ?: return@count false
            StoatAPI.unreads.serverHasUnread(sid)
        }
    }

    val totalNotificationsCount = incomingRequestsCount + unreadServersCount
    var showReconnectAction by rememberSaveable { mutableStateOf(false) }
    val reconnectScope = rememberCoroutineScope()

    LaunchedEffect(isConnecting, dmAbleChannels.isEmpty(), notesChannel == null) {
        if (isConnecting && dmAbleChannels.isEmpty() && notesChannel == null) {
            delay(12_000)
            showReconnectAction = true
        } else {
            showReconnectAction = false
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(NavigationBarDefaults.windowInsets),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.main_tab_conversations),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showNotificationsSheet = true }) {
                        if (totalNotificationsCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text(
                                            text = if (totalNotificationsCount > 99) "99+" else totalNotificationsCount.toString()
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_notifications_24dp),
                                    contentDescription = "Notifications & Requests"
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_notifications_24dp),
                                contentDescription = "Notifications & Requests"
                            )
                        }
                    }

                    IconButton(onClick = { showAddFriendDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person_add_24dp),
                            contentDescription = "Add Friend"
                        )
                    }
                },
                windowInsets = WindowInsets.zero
            )
        },
    ) { pv ->
        if (dmAbleChannels.isEmpty() && notesChannel == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pv)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isConnecting && !showReconnectAction) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connecting to conversations...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (isConnecting) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_forum_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            "Still trying to connect",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Your conversations will appear when Dismod reconnects. Check your connection, then try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { reconnectScope.launch { StoatAPI.connectWS() } }) {
                            Text("Retry connection")
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_forum_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "No conversations",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "You don't have any conversations yet. When a friend messages you or you start a chat, it will show up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pv),
            ) {
                if (notesChannel != null) {
                    item(key = "saved_messages") {
                        val lastMessage = notesChannel.lastMessageID?.let { StoatAPI.messageCache[it] }
                        val preview = when {
                            lastMessage != null -> lastMessage.content?.takeIf { it.isNotBlank() } ?: "Note saved"
                            else -> "Notes to yourself"
                        }

                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.channel_notes),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    preview,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    StoatAPI.userCache[StoatAPI.selfId]?.let {
                                        UserAvatar(
                                            username = it.username.toString(),
                                            avatar = it.avatar,
                                            userId = it.id.toString(),
                                            size = 48.dp,
                                            shape = RoundedCornerShape(LoadedSettings.avatarRadius)
                                        )
                                    }
                                    Badge {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_keep_24dp),
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("main/conversation/${notesChannel.id}")
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }

                items(dmAbleChannels, key = { it.id ?: "" }) { channel ->
                    val lastMessage = channel.lastMessageID?.let { StoatAPI.messageCache[it] }
                    val hasUnread = channel.lastMessageID?.let {
                        StoatAPI.unreads.hasUnread(channel.id ?: "", it, serverId = null)
                    } ?: false

                    when (channel.channelType) {
                        ChannelType.Group -> {
                            val groupName = channel.name ?: "Group Chat"
                            val previewText = when {
                                lastMessage != null -> {
                                    val authorName = if (lastMessage.author == StoatAPI.selfId) {
                                        "You: "
                                    } else {
                                        StoatAPI.userCache[lastMessage.author]?.let { User.resolveDefaultName(it) }?.let { "$it: " } ?: ""
                                    }
                                    val content = lastMessage.content?.takeIf { it.isNotBlank() } ?: "Sent an attachment"
                                    "$authorName$content"
                                }
                                else -> "Tap to start chatting"
                            }

                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = groupName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = previewText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = {
                                    GroupIcon(
                                        name = groupName,
                                        size = 48.dp,
                                        icon = channel.icon
                                    )
                                },
                                trailingContent = {
                                    if (hasUnread) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    navController.navigate("main/conversation/${channel.id}")
                                }
                            )
                        }

                        else -> {
                            val partnerId = ChannelUtils.resolveDMPartner(channel)
                            val partner = partnerId?.let { StoatAPI.userCache[it] }
                            val partnerName = partner?.let { User.resolveDefaultName(it) }
                                ?: partner?.username
                                ?: channel.name
                                ?: stringResource(R.string.unknown)

                            val previewText = when {
                                lastMessage != null -> {
                                    val prefix = if (lastMessage.author == StoatAPI.selfId) "You: " else ""
                                    val content = lastMessage.content?.takeIf { it.isNotBlank() } ?: "Sent an attachment"
                                    "$prefix$content"
                                }
                                else -> "Tap to start chatting"
                            }

                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = partnerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = previewText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = {
                                    UserAvatar(
                                        username = partnerName,
                                        avatar = partner?.avatar ?: channel.icon,
                                        userId = partner?.id ?: channel.id ?: "",
                                        presence = presenceFromStatus(
                                            partner?.status?.presence,
                                            partner?.online ?: false
                                        ),
                                        size = 48.dp,
                                        presenceSize = 14.dp,
                                        shape = RoundedCornerShape(LoadedSettings.avatarRadius)
                                    )
                                },
                                trailingContent = {
                                    if (hasUnread) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    navController.navigate("main/conversation/${channel.id}")
                                }
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }

    if (showNotificationsSheet) {
        NotificationsSheet(
            navController = navController,
            onDismiss = { showNotificationsSheet = false }
        )
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            onDismiss = { showAddFriendDialog = false }
        )
    }
}