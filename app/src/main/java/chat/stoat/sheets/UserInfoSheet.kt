package chat.stoat.sheets

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import chat.stoat.api.settings.CustomBadge
import chat.stoat.api.settings.CustomBadgeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.BrushCompat
import chat.stoat.api.internals.ResourceLocations
import chat.stoat.api.internals.ULID
import chat.stoat.api.routes.user.acceptFriendRequest
import chat.stoat.api.routes.user.fetchUserProfile
import chat.stoat.api.routes.user.friendUser
import chat.stoat.api.routes.user.getOrFetchUser
import chat.stoat.api.routes.user.openDM
import chat.stoat.api.routes.user.unfriendUser
import chat.stoat.callbacks.Action
import chat.stoat.callbacks.ActionChannel
import chat.stoat.composables.chat.UserBadgeRow
import chat.stoat.composables.generic.AvatarViewerDialog
import chat.stoat.composables.generic.NonIdealState
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.composables.generic.presenceFromStatus
import chat.stoat.composables.markdown.prose.ChatMarkdown
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.AutumnResource
import chat.stoat.core.model.schemas.Profile
import chat.stoat.core.model.schemas.User
import chat.stoat.core.model.schemas.UserBadges
import chat.stoat.core.model.schemas.has
import chat.stoat.internals.CustomNicknames
import chat.stoat.persistence.KVStorage
import chat.stoat.screens.chat.dialogs.ChangeNicknameDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDiscordDate(timestampMs: Long): String {
    val date = Date(timestampMs)
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
    return sdf.format(date)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserInfoSheet(
    userId: String,
    serverId: String? = null,
    dismissSheet: suspend () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val kvStorage = remember { KVStorage(context) }

    var user by remember(userId) { mutableStateOf(StoatAPI.userCache[userId]) }
    var isLoadingUser by remember(userId) { mutableStateOf(user == null) }
    val member = serverId?.let { StoatAPI.members.getMember(it, userId) }
    val server = StoatAPI.serverCache[serverId]

    var profile by remember { mutableStateOf<Profile?>(null) }
    var showUserCard by remember { mutableStateOf(false) }
    var showChangeNicknameDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFullAvatar by remember { mutableStateOf(false) }
    var showFullBanner by remember { mutableStateOf(false) }
    var showCustomBadgeSheet by remember { mutableStateOf(false) }
    var assignedBadges by remember { mutableStateOf<Set<CustomBadge>>(emptySet()) }

    LaunchedEffect(userId) {
        if (user == null) {
            try {
                user = getOrFetchUser(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingUser = false
            }
        }
    }

    LaunchedEffect(user?.id) {
        user?.id?.let { id ->
            try {
                profile = fetchUserProfile(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showUserCard) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showUserCard = false }
        ) {
            UserCardSheet(user = user)
        }
    }

    if (showFullAvatar && user != null) {
        val avatarUrl = user?.avatar?.let { "$STOAT_FILES/avatars/${it.id}/original" }
            ?: ResourceLocations.userAvatarOriginalUrl(user)
        AvatarViewerDialog(
            avatarUrl = avatarUrl,
            username = user?.username ?: "user",
            displayName = user?.displayName,
            onDismissRequest = { showFullAvatar = false }
        )
    }

    if (showChangeNicknameDialog && user != null) {
        val currentNick = member?.nickname?.takeIf { it.isNotBlank() }
            ?: CustomNicknames.getNickname(user?.id ?: "")
        ChangeNicknameDialog(
            userId = userId,
            serverId = serverId,
            currentNickname = currentNick,
            onDismissRequest = { showChangeNicknameDialog = false }
        )
    }

    if (showCustomBadgeSheet && user?.id != null) {
        val targetUid = user!!.id!!
        LaunchedEffect(targetUid, showCustomBadgeSheet) {
            assignedBadges = withContext(Dispatchers.IO) {
                CustomBadgeStore.get(context).getBadges(targetUid)
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showCustomBadgeSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shield_crown_24dp),
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Assign Badges",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Tap any badge to give or remove it for ${user?.displayName ?: user?.username}:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CustomBadge.entries.forEach { badge ->
                        val hasBadge = assignedBadges.contains(badge)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (hasBadge) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (hasBadge) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.clickable {
                                scope.launch(Dispatchers.IO) {
                                    CustomBadgeStore.get(context).toggleBadge(targetUid, badge)
                                    val updated = CustomBadgeStore.get(context).getBadges(targetUid)
                                    withContext(Dispatchers.Main) {
                                        assignedBadges = updated
                                    }
                                }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (badge == CustomBadge.Slut) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_female_24dp),
                                        contentDescription = null,
                                        tint = if (hasBadge) MaterialTheme.colorScheme.primary else Color(0xFFE91E63),
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Text(text = badge.emoji, fontSize = 14.sp)
                                }
                                Text(
                                    text = badge.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasBadge) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { showCustomBadgeSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }

    if (user == null && !isLoadingUser) {
        NonIdealState(
            icon = { size ->
                Icon(
                    painter = painterResource(R.drawable.ic_error_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(size)
                )
            },
            title = { Text("User not found") },
            description = { Text("Could not load user information.") }
        )
        return
    }

    if (isLoadingUser || user == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val currentUser = user!!
    val isSelf = currentUser.id == StoatAPI.selfId
    val isServerOwner = server != null && server.owner == currentUser.id
    val isFounder = currentUser.badges.has(UserBadges.Founder)
    val isOwner = isServerOwner || isFounder
    val isVerified = currentUser.badges.has(UserBadges.Verified)

    val effectiveNickname = member?.nickname?.takeIf { it.isNotBlank() }
        ?: CustomNicknames.getNickname(currentUser.id ?: "")
    val displayName = effectiveNickname
        ?: currentUser.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser.username
        ?: "Unknown"
    val username = currentUser.username ?: "user"

    val memberSinceMs = currentUser.id?.let { runCatching { ULID.asTimestamp(it) }.getOrNull() }
        ?: System.currentTimeMillis()

    val mutualServerCount = remember(currentUser.id) {
        if (currentUser.id == null) 0
        else if (isSelf) StoatAPI.serverCache.values.count { it.owner == currentUser.id }
        else StoatAPI.serverCache.values.count { s ->
            s.id != null && (StoatAPI.members.hasMember(s.id!!, currentUser.id!!) || s.owner == currentUser.id)
        }
    }

    val presence = presenceFromStatus(currentUser.status?.presence, currentUser.online ?: false)
    val presenceColor = when {
        currentUser.online == true || currentUser.status?.presence == "Online" -> Color(0xFF23A55A)
        currentUser.status?.presence == "Idle" -> Color(0xFFFAA61A)
        currentUser.status?.presence == "Busy" || currentUser.status?.presence == "Focus" -> Color(0xFFED4245)
        else -> Color(0xFF747F8D)
    }
    val presenceLabel = currentUser.status?.presence ?: if (currentUser.online == true) "Online" else "Offline"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // MAIN PROFILE CARD (matching Image 1 layout)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column {
                // 1. BANNER HEADER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val background = profile?.background
                    if (background != null) {
                        val bgUrl = "$STOAT_FILES/backgrounds/${if (background is AutumnResource) background.id else null}/${if (background is AutumnResource) background.filename else background}"
                        RemoteImage(
                            url = bgUrl,
                            description = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { showFullBanner = true },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF9C9FF8),
                                            Color(0xFFC49BF0),
                                            Color(0xFFE5A0B8)
                                        )
                                    )
                                )
                        )
                    }

                    // Top-right actions (Options menu or Edit)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelf) {
                            IconButton(
                                onClick = { showChangeNicknameDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_camera_24dp),
                                    contentDescription = "Edit Profile",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_more_vert_24dp),
                                        contentDescription = "Options",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("View Profile Picture") },
                                        onClick = {
                                            showMenu = false
                                            showFullAvatar = true
                                        },
                                        leadingIcon = {
                                            Icon(painterResource(R.drawable.ic_account_circle_24dp), contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (serverId != null) "Change Server Nickname" else "Change Nickname") },
                                        onClick = {
                                            showMenu = false
                                            showChangeNicknameDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(painterResource(R.drawable.ic_edit_24dp), contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy User ID") },
                                        onClick = {
                                            currentUser.id?.let { id ->
                                                clipboard.setText(AnnotatedString(id))
                                                Toast.makeText(context, "Copied ID", Toast.LENGTH_SHORT).show()
                                            }
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(painterResource(R.drawable.ic_content_copy_24dp), contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. AVATAR & ACTION BUTTONS ROW
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar with Presence Status Dot
                        Box(contentAlignment = Alignment.BottomEnd) {
                            UserAvatar(
                                username = displayName,
                                userId = currentUser.id ?: "",
                                avatar = currentUser.avatar,
                                size = 96.dp,
                                shape = CircleShape
                            )
                            // Presence Status Dot
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(presenceColor)
                                )
                            }
                        }

                        // Action button on right
                        if (isSelf) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.clickable { showChangeNicknameDialog = true }
                            ) {
                                Text(
                                    text = "Edit profile",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Add/Manage Friend Pill
                                when (currentUser.relationship) {
                                    "Friend" -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.clickable {
                                                scope.launch {
                                                    currentUser.id?.let { unfriendUser(it) }
                                                }
                                            }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Icon(
                                                    painterResource(R.drawable.ic_check_24dp),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color(0xFF23A55A)
                                                )
                                                Text(
                                                    text = "Friends",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                    "Incoming" -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF1D4ED8),
                                            modifier = Modifier.clickable {
                                                scope.launch {
                                                    currentUser.id?.let { acceptFriendRequest(it) }
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = "Accept",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    "Outgoing" -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = "Sent",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF1D4ED8),
                                            modifier = Modifier.clickable {
                                                scope.launch {
                                                    try {
                                                        friendUser("${currentUser.username}#${currentUser.discriminator}")
                                                        Toast.makeText(context, "Friend request sent", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        if (e.message != "NoEffect") {
                                                            Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Icon(
                                                    painterResource(R.drawable.ic_person_add_24dp),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color.White
                                                )
                                                Text(
                                                    text = "Add Friend",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                // Message Icon Button
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            currentUser.id?.let { uid ->
                                                val dm = openDM(uid)
                                                if (dm.id != null) {
                                                    StoatAPI.channelCache[dm.id!!] = dm
                                                    ActionChannel.send(Action.SwitchChannel(dm.id!!))
                                                    dismissSheet()
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_chat_24dp),
                                        contentDescription = "Message",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(8.dp).size(18.dp)
                                    )
                                }

                                // Owner Give Badge Button
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.clickable {
                                        showCustomBadgeSheet = true
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_shield_crown_24dp),
                                        contentDescription = "Give Badge",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.padding(8.dp).size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 3. IDENTITY: NAME, VERIFIED BADGE, USERNAME
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // ONLY show verified badge if isVerified is true
                        if (isVerified) {
                            Icon(
                                painter = painterResource(R.drawable.user_badge_verified),
                                contentDescription = "Verified",
                                tint = Color(0xFF00BCD4),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(10.dp))

                    // 4. STATUS PILLS (Owner only if owner, Online presence)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ONLY show Owner pill if user is actually Owner/Founder
                        if (isOwner) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E3A8A).copy(alpha = 0.5f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.user_badge_founder_new),
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isServerOwner) "Server Owner" else "Owner",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF93C5FD)
                                    )
                                }
                            }
                        }

                        // Online / Presence Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(presenceColor)
                                )
                                Text(
                                    text = presenceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Display other badges if present (official + owner-assigned custom)
                        if ((currentUser.badges ?: 0) > 0 || currentUser.id != null) {
                            UserBadgeRow(
                                badges = currentUser.badges ?: 0L,
                                userId = currentUser.id
                            )
                        }
                    }

                    // Custom status text if present
                    if (currentUser.status?.text != null && currentUser.status!!.text!!.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = "💬", fontSize = 13.sp)
                                Text(
                                    text = currentUser.status!!.text!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 5. ABOUT ME SECTION
                    val bioContent = profile?.content?.takeIf { it.isNotBlank() }
                        ?: if (isSelf) "nah I mean I'm the CEO of this app." else "No bio available."

                    Text(
                        text = "About me",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    ChatMarkdown(content = bioContent)

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    Spacer(Modifier.height(14.dp))

                    // 6. METADATA DETAILS LIST (Member since, Servers, Active on Android)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_note_stack_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Member since ${formatDiscordDate(memberSinceMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tag_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isSelf) "Owner of $mutualServerCount servers" else "$mutualServerCount mutual servers",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_devices_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Active on Android",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Server Roles (if in a server)
                    val memberRoles = member?.roles
                    if (!memberRoles.isNullOrEmpty() && server != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Roles",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            memberRoles.forEach { roleId ->
                                val role = server.roles?.get(roleId)
                                if (role != null) {
                                    val roleBrush = role.colour?.let { BrushCompat.parseColour(it) }
                                        ?: SolidColor(MaterialTheme.colorScheme.primary)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(roleBrush)
                                            )
                                            Text(
                                                text = role.name ?: "Role",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 7. BOTTOM ACTION CARDS
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showUserCard = true },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ios_share_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Share profile",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_forward_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    currentUser.id?.let { id ->
                        clipboard.setText(AnnotatedString(id))
                        Toast.makeText(context, "Copied ID", Toast.LENGTH_SHORT).show()
                    }
                },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_copy_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Copy user ID",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_forward_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}