package chat.stoat.sheets

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import chat.stoat.api.routes.user.fetchUserProfile
import chat.stoat.api.routes.user.getOrFetchUser
import chat.stoat.api.settings.Experiments
import chat.stoat.api.settings.FeatureFlags
import chat.stoat.composables.chat.UserBadgeRow
import chat.stoat.composables.generic.NonIdealState
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.composables.generic.presenceFromStatus
import chat.stoat.composables.markdown.prose.ChatMarkdown
import chat.stoat.composables.profile.CosmeticEffectOverlay
import chat.stoat.composables.profile.ProfileCosmeticsStore
import chat.stoat.composables.profile.bannerBrush
import chat.stoat.composables.screens.settings.UserButtons
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.AutumnResource
import chat.stoat.core.model.schemas.Profile
import chat.stoat.core.model.schemas.User
import chat.stoat.persistence.KVStorage
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

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

    var user by remember(userId) { mutableStateOf(StoatAPI.userCache[userId]) }
    var isLoadingUser by remember(userId) { mutableStateOf(user == null) }
    val member = serverId?.let { StoatAPI.members.getMember(it, userId) }
    val server = StoatAPI.serverCache[serverId]

    var profile by remember { mutableStateOf<Profile?>(null) }
    var showUserCard by remember { mutableStateOf(false) }
    var showServerIdentityOptions by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

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
            ProfileCosmeticsStore.load(KVStorage(context), currentUser.id!!)
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
                                    Color(0xFF3C4370),
                                    Color(0xFF5865F2),
                                    Color(0xFF4752C4)
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
                Spacer(modifier = Modifier.height(44.dp))

                // Display Name + Pronouns
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displayName = currentUser.displayName?.takeIf { it.isNotBlank() }
                        ?: currentUser.username
                        ?: "Unknown"

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

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

                // Handle (@username#1234)
                Text(
                    text = "@${currentUser.username ?: ""}${if (currentUser.discriminator != null && currentUser.discriminator != "0000") "#${currentUser.discriminator}" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 3. CUSTOM STATUS BUBBLE (Discord speech bubble)
                if (currentUser.status?.text != null && currentUser.status!!.text!!.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "💬",
                                fontSize = 16.sp
                            )
                            Text(
                                text = currentUser.status!!.text!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. ACTION BUTTONS (Send Message / Add Friend / Edit Profile)
                UserButtons(currentUser, dismissSheet)

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Spacer(modifier = Modifier.height(16.dp))

                // 5. ABOUT ME / BIO
                if (profile?.content?.isNotBlank() == true) {
                    Text(
                        text = "ABOUT ME",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SelectionContainer {
                        ChatMarkdown(content = profile!!.content!!, serverId = serverId)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 6. ROLES SECTION (If server member has roles)
                val memberRoles = member?.roles?.mapNotNull { roleId -> server?.roles?.get(roleId) }
                    ?.sortedBy { it.rank ?: 0.0 }
                if (!memberRoles.isNullOrEmpty()) {
                    Text(
                        text = "ROLES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 7. BADGES SECTION
                if ((currentUser.badges ?: 0) > 0) {
                    Text(
                        text = "BADGES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    UserBadgeRow(badges = currentUser.badges!!)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 8. MEMBER DATES (Member since)
                val joinedAt = member?.joinedAt?.let {
                    DateUtils.getRelativeTimeSpanString(
                        Instant.parse(it).toEpochMilliseconds(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                }
                val accountAt = currentUser.id?.let {
                    DateUtils.getRelativeTimeSpanString(
                        ULID.asTimestamp(it),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                }

                Text(
                    text = "MEMBER SINCE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (joinedAt != null && server?.name != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = joinedAt,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (accountAt != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dismod",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = accountAt,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Circular Avatar overlapping banner
            Box(
                modifier = Modifier
                    .offset(y = (-40).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp)
            ) {
                UserAvatar(
                    username = currentUser.displayName ?: currentUser.username ?: "Unknown",
                    userId = currentUser.id ?: ULID.makeSpecial(0),
                    avatar = currentUser.avatar,
                    size = 76.dp,
                    decorationId = avatarDecoration,
                    presence = presenceFromStatus(currentUser.status?.presence, currentUser.online ?: false)
                )
            }
        }
    }
}