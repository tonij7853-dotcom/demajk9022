package chat.stoat.screens.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.realtime.DisconnectionState
import chat.stoat.api.realtime.RealtimeSocket
import chat.stoat.api.internals.CategorisedChannelList
import chat.stoat.api.internals.ChannelUtils
import chat.stoat.composables.generic.IconPlaceholder
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.composables.screens.chat.ChannelIcon
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.sheets.AddServerSheet
import chat.stoat.sheets.ServerContextSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CommunitiesScreen(navController: NavController) {
    val servers = remember(StoatAPI.serverCache.values.toList()) {
        StoatAPI.serverCache.values.toList()
    }

    var selectedServerId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddServerSheet by remember { mutableStateOf(false) }
    var contextSheetServerId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val currentServer = servers.firstOrNull { it.id == selectedServerId } ?: servers.firstOrNull()
    val isConnecting = RealtimeSocket.disconnectionState != DisconnectionState.Connected

    if (servers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isConnecting) {
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
                        text = "Loading communities...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        painter = painterResource(R.drawable.ic_tag_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "No communities yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "You haven't joined or created any servers yet. Create your first server to get started!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showAddServerSheet = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create a Server", fontWeight = FontWeight.SemiBold)
                }
            }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Rail: Server List
            LazyColumn(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(servers, key = { it.id ?: "" }) { server ->
                    val isSelected = server.id == currentServer?.id
                    val srvId = server.id ?: ""
                    val serverMentionCount = if (srvId.isNotEmpty()) StoatAPI.unreads.getServerMentionCount(srvId) else 0
                    val hasUnread = if (srvId.isNotEmpty()) StoatAPI.unreads.serverHasUnread(srvId) else false

                    val pillHeight by animateDpAsState(
                        targetValue = when {
                            isSelected -> 36.dp
                            serverMentionCount > 0 -> 14.dp
                            hasUnread -> 8.dp
                            else -> 0.dp
                        },
                        label = "pillHeight"
                    )

                    val cornerRadius by animateDpAsState(
                        targetValue = if (isSelected) 16.dp else 24.dp,
                        label = "cornerRadius"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Left Selection Pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(4.dp)
                                .height(pillHeight)
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .background(if (serverMentionCount > 0 && !isSelected) Color(0xFFED4245) else MaterialTheme.colorScheme.onSurface)
                        )

                        // Server Icon
                        Box(contentAlignment = Alignment.BottomEnd) {
                            val iconModifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(cornerRadius))
                                .combinedClickable(
                                    onClick = {
                                        selectedServerId = server.id
                                    },
                                    onLongClick = {
                                        contextSheetServerId = server.id
                                    }
                                )

                            val iconUrl = server.icon?.id?.let { "$STOAT_FILES/icons/$it" }
                            if (iconUrl != null) {
                                RemoteImage(
                                    url = iconUrl,
                                    description = server.name,
                                    modifier = iconModifier
                                )
                            } else {
                                IconPlaceholder(
                                    name = server.name ?: "Server",
                                    modifier = iconModifier,
                                    fontSize = 18.sp
                                )
                            }

                            if (serverMentionCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 4.dp, y = 4.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFED4245))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (serverMentionCount > 99) "99+" else serverMentionCount.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "divider") {
                    HorizontalDivider(
                        modifier = Modifier
                            .width(36.dp)
                            .padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }

                // Add Server Button
                item(key = "add_server") {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { showAddServerSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add_24dp),
                            contentDescription = "Add Server",
                            tint = Color(0xFF23A55A),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            // Right Pane: Selected Server Channels
            if (currentServer != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Server Header
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentServer.name ?: "Server",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { contextSheetServerId = currentServer.id },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert_24dp),
                                    contentDescription = "Server Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Channel List
                    val categorisedChannels = remember(currentServer, StoatAPI.channelCache.values.toList()) {
                        ChannelUtils.categoriseServerFlat(currentServer)
                    }

                    if (categorisedChannels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No channels yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Channels created in this server will appear here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(categorisedChannels) { item ->
                                when (item) {
                                    is CategorisedChannelList.Category -> {
                                        Text(
                                            text = item.category.title?.uppercase() ?: "CHANNELS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                                        )
                                    }

                                     is CategorisedChannelList.Channel -> {
                                        val channel = item.channel
                                        val chId = channel.id ?: ""
                                        val mentionCount = if (chId.isNotEmpty()) StoatAPI.unreads.getMentionCount(chId) else 0
                                        val hasUnread = channel.lastMessageID?.let {
                                            StoatAPI.unreads.hasUnread(chId, it, serverId = currentServer.id)
                                        } ?: false

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    navController.navigate("main/conversation/${channel.id}")
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ChannelIcon(
                                                channel = channel,
                                                modifier = Modifier.size(20.dp)
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Text(
                                                text = channel.name ?: "channel",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (hasUnread || mentionCount > 0) FontWeight.Bold else FontWeight.Medium,
                                                color = if (hasUnread || mentionCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )

                                            if (mentionCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFED4245))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (mentionCount > 99) "99+" else mentionCount.toString(),
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        lineHeight = 12.sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            } else if (hasUnread) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddServerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddServerSheet = false }
        ) {
            AddServerSheet(
                onDismiss = { showAddServerSheet = false }
            )
        }
    }

    if (contextSheetServerId != null) {
        ModalBottomSheet(
            onDismissRequest = { contextSheetServerId = null }
        ) {
            ServerContextSheet(
                serverId = contextSheetServerId!!,
                onReportServer = {},
                onHideSheet = {
                    contextSheetServerId = null
                }
            )
        }
    }
}
