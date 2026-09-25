package chat.stoat.sheets

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import chat.stoat.api.internals.ULID
import chat.stoat.api.internals.solidColor
import chat.stoat.api.routes.user.acceptFriendRequest
import chat.stoat.api.routes.user.fetchUserProfile
import chat.stoat.api.routes.user.friendUser
import chat.stoat.api.routes.user.getOrFetchUser
import chat.stoat.api.routes.user.openDM
import chat.stoat.api.routes.user.unfriendUser
import chat.stoat.api.settings.Experiments
import chat.stoat.api.settings.FeatureFlags
import chat.stoat.callbacks.Action
import chat.stoat.callbacks.ActionChannel
import chat.stoat.composables.chat.UserBadgeRow
import chat.stoat.api.internals.ResourceLocations
import chat.stoat.composables.generic.AvatarViewerDialog
import chat.stoat.composables.generic.NonIdealState
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.composables.generic.presenceFromStatus
import chat.stoat.composables.markdown.prose.ChatMarkdown
import chat.stoat.composables.profile.CosmeticEffectOverlay
import chat.stoat.composables.profile.ProfileCosmeticsStore
import chat.stoat.composables.profile.bannerBrush
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.AutumnResource
import chat.stoat.core.model.schemas.Profile
import chat.stoat.core.model.schemas.User
import chat.stoat.internals.CustomNicknames
import chat.stoat.persistence.KVStorage
import chat.stoat.screens.chat.dialogs.ChangeNicknameDialog
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
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
    var showServerIdentityOptions by remember { mutableStateOf(false) }
    var showChangeNicknameDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFullAvatar by remember { mutableStateOf(false) }
    var showFullBanner by remember { mutableStateOf(false) }

    var userNote by remember(userId) {
        mutableStateOf("")
    }

    LaunchedEffect(userId) {
        userNote = kvStorage.get("user_note/$userId") ?: ""
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

    if (showChangeNicknameDialog && user != null) {
        val currentNick = member?.nickname?.takeIf { it.isNotBlank() }
            ?: CustomNicknames.getNickname(user!!.id ?: "")
        ChangeNicknameDialog(
            userId = user!!.id ?: userId,
            serverId = serverId,
            currentNickname = currentNick,
            onDismissRequest = { showChangeNicknameDialog = false },
            onNicknameSaved = {
                showChangeNicknameDialog = false
            }
        )
    }

    if (showUserCard && user != null) {
        val sheetState = rememberModalBottomSheetState(true)
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showUserCard = false }
        ) {
            UserCardSheet(user)
        }
    }

    if (showServerIdentityOptions && user?.id != null) {
        val sheetState = rememberModalBottomSheetState(true)
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showServerIdentityOptions = false }
        ) {
            ServerIdentityOptionsSheet(
                userId = user!!.id!!
            )
        }
    }

    if (showFullAvatar && user != null) {
        val fullAvatarUrl = member?.avatar?.let { "$STOAT_FILES/avatars/${it.id}/original" }
            ?: user?.avatar?.let { "$STOAT_FILES/avatars/${it.id}/original" }
            ?: ResourceLocations.userAvatarOriginalUrl(user)
        AvatarViewerDialog(
            avatarUrl = fullAvatarUrl,
            username = user!!.username ?: "user",
            displayName = user!!.displayName,
            onDismissRequest = { showFullAvatar = false }
        )
    }

    if (showFullBanner && profile?.background != null && user != null) {
        val background = profile!!.background
        val fullBannerUrl = if (background is AutumnResource && background.id != null) {
            "$STOAT_FILES/backgrounds/${background.id}/original"
        } else {
            "$STOAT_FILES/backgrounds/${if (background is AutumnResource) background.id else null}/${if (background is AutumnResource) background.filename else background}"
        }
        AvatarViewerDialog(
            avatarUrl = fullBannerUrl,
            username = user!!.username ?: "user",
            displayName = "${user!!.displayName ?: user!!.username}'s Banner",
            onDismissRequest = { showFullBanner = false }
        )
    }

    if (isLoadingUser) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (user == null) {
        NonIdealState(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_error_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(it)
                )
            },
            title = {
                Text(text = stringResource(R.string.user_info_sheet_user_not_found))
            },
            description = {
                Text(text = stringResource(R.string.user_info_sheet_user_not_found_description))
            }
        )
        Spacer(Modifier.height(24.dp))
        return
    }

    val currentUser = user!!
    val isSelf = currentUser.id != null && currentUser.id == StoatAPI.selfId
    val cosmetics by ProfileCosmeticsStore.current

    LaunchedEffect(currentUser.id, isSelf) {
        if (isSelf && currentUser.id != null) {
            ProfileCosmeticsStore.load(kvStorage, currentUser.id!!)
        }
    }

    val avatarDecoration = if (isSelf) cosmetics.avatarDecoration else "none"
    val profileEffect = if (isSelf) cosmetics.profileEffect else "none"
    val chosenBanner = if (isSelf) bannerBrush(cosmetics.bannerStyle) else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        // 1. BANNER HEADER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .then(
                    if (profile?.background != null) {
                        Modifier.clickable { showFullBanner = true }
                    } else {
                        Modifier
                    }
                )
        ) {
            val background = profile?.background
            if (background != null) {
                val bgUrl = "$STOAT_FILES/backgrounds/${if (background is AutumnResource) background.id else null}/${if (background is AutumnResource) background.filename else background}"
                RemoteImage(
                    url = bgUrl,
                    description = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            chosenBanner ?: Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF1E1F22),
                                    Color(0xFF2B2D31),
                                    Color(0xFF1E1F22)
                                )
                            )
                        )
                )
            }

            if (profileEffect != "none") {
                CosmeticEffectOverlay(profileEffect, Modifier.fillMaxSize())
            }

            // Top action buttons: 3 dots menu, server identity, or card badge
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (FeatureFlags.userCardsGranted) {
                    IconButton(
                        onClick = { showUserCard = true }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_badge_24dp),
                            contentDescription = "Trading Card",
                            tint = Color.White
                        )
                    }
                }

                if (Experiments.enableServerIdentityOptions.isEnabled) {
                    IconButton(
                        onClick = { showServerIdentityOptions = true }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_psychology_alt_24dp),
                            contentDescription = "Server Identity",
                            tint = Color.White
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
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
                        if (profile?.background != null) {
                            DropdownMenuItem(
                                text = { Text("View Banner") },
                                onClick = {
                                    showMenu = false
                                    showFullBanner = true
                                },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_photo_library_24dp), contentDescription = null)
                                }
                            )
                        }
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
                        DropdownMenuItem(
                            text = { Text("Copy Username") },
                            onClick = {
                                clipboard.setText(AnnotatedString("${currentUser.username}#${currentUser.discriminator}"))
                                Toast.makeText(context, "Copied username", Toast.LENGTH_SHORT).show()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_tag_24dp), contentDescription = null)
                            }
                        )
                    }
                }
            }
        }

        // 2. AVATAR OVERLAY & PROFILE BODY
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column {
                // Gap for avatar overlap
                Spacer(modifier = Modifier.height(52.dp))

                // Display Name + Edit Nickname Icon + Pronouns
                val effectiveNickname = member?.nickname?.takeIf { it.isNotBlank() }
                    ?: CustomNicknames.getNickname(currentUser.id ?: "")
                val displayName = effectiveNickname
                    ?: currentUser.displayName?.takeIf { it.isNotBlank() }
                    ?: currentUser.username
                    ?: "Unknown"

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = { showChangeNicknameDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit_24dp),
                            contentDescription = "Change Nickname",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    currentUser.pronouns?.trim()?.takeIf { it.isNotEmpty() }?.let { pronouns ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = pronouns,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Username & Badges row directly below display name (Discord style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "@${currentUser.username ?: ""}${if (currentUser.discriminator != null && currentUser.discriminator != "0000") "#${currentUser.discriminator}" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if ((currentUser.badges ?: 0) > 0) {
                        UserBadgeRow(badges = currentUser.badges!!)
                    }
                }

                // Mutual Server indicator (Discord style)
                val mutualServerCount = remember(currentUser.id) {
                    if (currentUser.id == null || currentUser.id == StoatAPI.selfId) 0
                    else StoatAPI.serverCache.values.count { s ->
                        s.id != null && (StoatAPI.members.hasMember(s.id!!, currentUser.id!!) || s.owner == currentUser.id)
                    }
                }
                if (mutualServerCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_group_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$mutualServerCount Mutual Server${if (mutualServerCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. CUSTOM STATUS BUBBLE (if present)
                if (currentUser.status?.text != null && currentUser.status!!.text!!.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "💬",
                                fontSize = 15.sp
                            )
                            Text(
                                text = currentUser.status!!.text!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. ACTION BUTTONS (Discord style: Wide primary + Message icon + Call icon)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isSelf) {
                        when (currentUser.relationship) {
                            "Friend" -> {
                                FilledTonalButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                currentUser.id?.let { unfriendUser(it) }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_check_24dp),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Friends", fontWeight = FontWeight.Bold)
                                }
                            }
                            "Incoming" -> {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                currentUser.id?.let { acceptFriendRequest(it) }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_person_add_24dp),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Accept Friend", fontWeight = FontWeight.Bold)
                                }
                            }
                            "Outgoing" -> {
                                FilledTonalButton(
                                    onClick = {},
                                    enabled = false,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                ) {
                                    Text("Request Sent", fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
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
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_person_add_24dp),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Friend", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Message icon button
                        FilledTonalIconButton(
                            onClick = {
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
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chat_24dp),
                                contentDescription = "Send Message",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Call icon button
                        FilledTonalIconButton(
                            onClick = {
                                Toast.makeText(context, "Voice call not supported in this channel", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_call_24dp__fill),
                                contentDescription = "Call",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Edit Nickname icon button
                        FilledTonalIconButton(
                            onClick = { showChangeNicknameDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit_24dp),
                                contentDescription = "Change Nickname",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Button(
                            onClick = { showChangeNicknameDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_edit_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (serverId != null) "Edit Server Nickname" else "Edit Nickname", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                Spacer(modifier = Modifier.height(14.dp))

                // 5. BIO / ABOUT ME (Discord style)
                val bioContent = profile?.content?.takeIf { it.isNotBlank() } ?: currentUser.status?.text
                if (!bioContent.isNullOrBlank()) {
                    Text(
                        text = "Bio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SelectionContainer {
                        ChatMarkdown(content = bioContent, serverId = serverId)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 6. MEMBER SINCE (Discord style)
                val joinedAtMs = member?.joinedAt?.let {
                    runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull()
                }
                val accountAtMs = currentUser.id?.let {
                    runCatching { ULID.asTimestamp(it) }.getOrNull()
                }

                Text(
                    text = "Member Since",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (accountAtMs != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.stoat_logo_white),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatDiscordDate(accountAtMs),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (joinedAtMs != null) {
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_group_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatDiscordDate(joinedAtMs),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 7. ROLES SECTION (Discord style pills)
                val memberRoles = member?.roles?.mapNotNull { roleId -> server?.roles?.get(roleId) }
                    ?.sortedBy { it.rank ?: 0.0 }
                if (!memberRoles.isNullOrEmpty()) {
                    Text(
                        text = "Roles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        memberRoles.forEach { role ->
                            val roleColor = role.colour?.let { BrushCompat.parseColour(it) }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                roleColor ?: Brush.solidColor(MaterialTheme.colorScheme.primary)
                                            )
                                    )
                                    Text(
                                        text = role.name ?: "",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 8. MODERATOR ACTIONS (Discord style Manage card)
                if (serverId != null) {
                    Text(
                        text = "Moderator Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChangeNicknameDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Manage",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 9. NOTE (only visible to you) (Discord style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Note (only visible to you)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_note_stack_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = userNote,
                        onValueChange = {
                            userNote = it
                            scope.launch {
                                currentUser.id?.let { uid ->
                                    kvStorage.set("user_note/$uid", it)
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                "Click to add a note",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        ),
                        maxLines = 3
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Circular Avatar overlapping banner with presence status dot
            Box(
                modifier = Modifier
                    .offset(y = (-44).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showFullAvatar = true }
                    .padding(4.dp)
            ) {
                UserAvatar(
                    username = currentUser.displayName ?: currentUser.username ?: "Unknown",
                    userId = currentUser.id ?: ULID.makeSpecial(0),
                    avatar = currentUser.avatar,
                    size = 88.dp,
                    presenceSize = 24.dp,
                    decorationId = avatarDecoration,
                    presence = presenceFromStatus(currentUser.status?.presence, currentUser.online ?: false),
                    onClick = { showFullAvatar = true }
                )
            }
        }
    }
}