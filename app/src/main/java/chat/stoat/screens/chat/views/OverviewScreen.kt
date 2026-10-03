package chat.stoat.screens.chat.views

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.ULID
import chat.stoat.api.routes.server.fetchMember
import chat.stoat.api.routes.user.fetchSelf
import chat.stoat.api.routes.user.fetchUserProfile
import chat.stoat.api.settings.CustomBadgeStore
import chat.stoat.composables.generic.NonIdealState
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.composables.markdown.prose.ChatMarkdown
import chat.stoat.api.internals.ResourceLocations
import chat.stoat.composables.generic.AvatarViewerDialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.SolidColor
import chat.stoat.api.internals.BrushCompat
import chat.stoat.composables.chat.UserBadgeRow
import chat.stoat.core.model.schemas.Role
import chat.stoat.core.model.schemas.UserBadges
import chat.stoat.core.model.schemas.has
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.AutumnResource
import chat.stoat.core.model.schemas.Profile
import chat.stoat.core.model.schemas.User
import chat.stoat.internals.extensions.zero
import chat.stoat.sheets.UserCardSheet
import io.sentry.Sentry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatProfileDate(timestampMs: Long): String {
    val date = Date(timestampMs)
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
    return sdf.format(date)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(
    navController: NavController,
    useDrawer: Boolean,
    onDrawerClicked: () -> Unit,
    includePadding: Boolean = true
) {
    val context = LocalContext.current
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var user by rememberSaveable { mutableStateOf<User?>(null) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var showUserCardSheet by rememberSaveable { mutableStateOf(false) }
    var showFullAvatar by remember { mutableStateOf(false) }
    var showFullBanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val inCache = StoatAPI.userCache[StoatAPI.selfId]
        if (inCache != null) {
            user = inCache
            isLoading = false
        } else {
            try {
                fetchSelf().let {
                    user = it
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e("OverviewScreen", "Failed to fetch self", e)
                Sentry.captureException(e)
                isLoading = false
            }
        }
    }

    LaunchedEffect(user?.id) {
        val id = user?.id ?: return@LaunchedEffect
        try {
            profile = fetchUserProfile(id)
        } catch (e: Exception) {
            Log.w("OverviewScreen", "Failed to load profile details", e)
        }
    }

    if (showUserCardSheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showUserCardSheet = false },
        ) {
            UserCardSheet(user = user)
        }
    }

    if (showFullAvatar && user != null) {
        val avatarUrl = ResourceLocations.userAvatarOriginalUrl(user)
        AvatarViewerDialog(
            avatarUrl = avatarUrl,
            username = user?.username ?: "user",
            displayName = user?.displayName,
            onDismissRequest = { showFullAvatar = false }
        )
    }

    if (showFullBanner && profile?.background != null) {
        val background = profile?.background
        val bgUrl = when {
            background?.id != null && background.filename != null ->
                "$STOAT_FILES/backgrounds/${background.id}/${background.filename}"
            background?.id != null ->
                "$STOAT_FILES/backgrounds/${background.id}/original"
            else -> null
        }
        if (bgUrl != null) {
            AvatarViewerDialog(
                avatarUrl = bgUrl,
                username = user?.username ?: "user",
                displayName = "Banner",
                onDismissRequest = { showFullBanner = false }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    if (useDrawer) {
                        IconButton(onClick = onDrawerClicked) {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu_24dp),
                                contentDescription = stringResource(id = R.string.menu)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_24dp),
                            contentDescription = stringResource(id = R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                windowInsets = WindowInsets.zero
            )
        }
    ) { pv ->
        if (user == null && !isLoading) {
            NonIdealState(
                icon = { size ->
                    Icon(
                        painter = painterResource(R.drawable.ic_error_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(size)
                    )
                },
                title = { Text(stringResource(R.string.overview_screen_error)) },
                description = { Text(stringResource(R.string.overview_screen_error_description)) }
            )
            return@Scaffold
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pv),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(Modifier.size(40.dp))
            }
            return@Scaffold
        }

        val currentUser = user!!
        val displayName = currentUser.displayName?.takeIf { it.isNotBlank() } ?: currentUser.username ?: "User"
        val username = currentUser.username ?: "user"
        val memberSinceMs = currentUser.id?.let { runCatching { ULID.asTimestamp(it) }.getOrNull() }
            ?: System.currentTimeMillis()
        val ownedServersCount = remember(currentUser.id) {
            StoatAPI.serverCache.values.count { it.owner == currentUser.id }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pv)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // MAIN PROFILE CARD (matching Image 1)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column {
                    // BANNER HEADER
                    val isOwner = currentUser.badges.has(UserBadges.Founder)
                    val isVerified = currentUser.badges.has(UserBadges.Verified)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        val background = profile?.background
                        if (background != null) {
                            val bgUrl = when {
                                background.id != null && background.filename != null ->
                                    "$STOAT_FILES/backgrounds/${background.id}/${background.filename}"
                                background.id != null ->
                                    "$STOAT_FILES/backgrounds/${background.id}/original"
                                else -> null
                            }
                            if (bgUrl != null) {
                                RemoteImage(
                                    url = bgUrl,
                                    description = null,
                                    forceAnimate = true,  // enable GIF animation for profile banners
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { showFullBanner = true },
                                    contentScale = ContentScale.Crop
                                )
                            }
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

                        // Camera edit icon in top-right of banner
                        IconButton(
                            onClick = { navController.navigate("settings/profile") },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_camera_24dp),
                                contentDescription = "Change Banner",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // AVATAR & EDIT PROFILE BUTTON
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
                            // Circular Avatar (with green online presence dot)
                            Box(
                                contentAlignment = Alignment.BottomEnd,
                                modifier = Modifier.clickable { showFullAvatar = true }
                            ) {
                                UserAvatar(
                                    username = displayName,
                                    userId = currentUser.id ?: "",
                                    avatar = currentUser.avatar,
                                    size = 96.dp,
                                    shape = CircleShape
                                )
                                // Green Online Status Dot
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
                                            .background(Color(0xFF23A55A))
                                    )
                                }
                            }

                            // "Edit profile" button (Dark rounded pill)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.clickable { navController.navigate("settings/profile") }
                            ) {
                                Text(
                                    text = "Edit profile",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // NAME & VERIFIED BADGE
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

                        // STATUS PILLS (Owner + Online)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isOwner) {
                                // Owner Pill
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
                                            text = "Owner",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF93C5FD)
                                        )
                                    }
                                }
                            }

                            // Online Pill
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
                                            .background(Color(0xFF23A55A))
                                    )
                                    Text(
                                        text = "Online",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Cloud badge sync on screen view
                        LaunchedEffect(currentUser.id) {
                            CustomBadgeStore.get(context).syncWithCloud()
                        }

                        // BADGES (Official + Synced custom badges)
                        if ((currentUser.badges ?: 0) > 0 || !currentUser.id.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            UserBadgeRow(
                                badges = currentUser.badges ?: 0L,
                                userId = currentUser.id
                            )
                        }

                        // SERVER ROLES (Roles assigned to this user across servers)
                        var userRolesList by remember(currentUser.id) {
                            val selfId = currentUser.id ?: ""
                            val result = mutableListOf<Pair<String, Role>>()
                            StoatAPI.serverCache.values.forEach { srv ->
                                val member = srv.id?.let { StoatAPI.members.getMember(it, selfId) }
                                member?.roles?.forEach { roleId ->
                                    val r = srv.roles?.get(roleId)
                                    if (r != null) {
                                        result.add((srv.name ?: "Server") to r)
                                    }
                                }
                            }
                            mutableStateOf(result)
                        }

                        LaunchedEffect(currentUser.id, StoatAPI.serverCache.size) {
                            val selfId = currentUser.id ?: return@LaunchedEffect
                            fun updateRoles() {
                                val result = mutableListOf<Pair<String, Role>>()
                                StoatAPI.serverCache.values.forEach { srv ->
                                    val member = srv.id?.let { StoatAPI.members.getMember(it, selfId) }
                                    member?.roles?.forEach { roleId ->
                                        val r = srv.roles?.get(roleId)
                                        if (r != null) {
                                            result.add((srv.name ?: "Server") to r)
                                        }
                                    }
                                }
                                userRolesList = result
                            }
                            updateRoles()

                            // If self member is missing in any server cache, fetch it
                            StoatAPI.serverCache.values.forEach { srv ->
                                val sId = srv.id ?: return@forEach
                                if (!StoatAPI.members.hasMember(sId, selfId)) {
                                    try {
                                        fetchMember(sId, selfId)
                                        updateRoles()
                                    } catch (_: Exception) {}
                                }
                            }
                        }

                        if (userRolesList.isNotEmpty()) {
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
                                userRolesList.forEach { (serverName, role) ->
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
                                                text = if (StoatAPI.serverCache.size > 1) "${role.name ?: "Role"} (${serverName})" else (role.name ?: "Role"),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ABOUT ME SECTION
                        val bioContent = profile?.content?.takeIf { it.isNotBlank() }
                            ?: "nah I mean I'm the CEO of this app."

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

                        // DETAILS LIST (Member since, Owner of servers, Active on Android)
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
                                text = "Member since ${formatProfileDate(memberSinceMs)}",
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
                                text = "Owner of $ownedServersCount servers",
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

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // BOTTOM ACTION CARDS (Share profile, Privacy and safety, Profile theme)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showUserCardSheet = true },
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
                    .clickable { navController.navigate("settings/account") },
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
                            painter = painterResource(R.drawable.ic_lock_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Privacy and safety",
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
                    .clickable { navController.navigate("settings/appearance") },
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
                            painter = painterResource(R.drawable.ic_palette_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Profile theme",
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
}