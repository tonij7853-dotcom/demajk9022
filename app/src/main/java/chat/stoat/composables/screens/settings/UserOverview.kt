package chat.stoat.composables.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.SpecialUsers
import chat.stoat.api.internals.ULID
import chat.stoat.api.internals.solidColor
import chat.stoat.api.routes.user.fetchUserProfile
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.composables.generic.presenceFromStatus
import chat.stoat.composables.profile.CosmeticEffectOverlay
import chat.stoat.composables.profile.Nameplate
import chat.stoat.composables.profile.ProfileCosmeticsStore
import chat.stoat.composables.profile.bannerBrush
import chat.stoat.composables.profile.bannerOverlay
import chat.stoat.composables.generic.AvatarViewerDialog
import chat.stoat.api.internals.ResourceLocations
import chat.stoat.persistence.KVStorage
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.AutumnResource
import chat.stoat.core.model.schemas.Profile
import chat.stoat.core.model.schemas.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Composable
fun SelfUserOverview() {
    val selfUser = StoatAPI.userCache[StoatAPI.selfId] ?: return

    UserOverview(selfUser)
}

@Composable
fun UserOverview(user: User, internalPadding: Boolean = true, cardHeight: Dp = 128.dp) {
    var profile by remember { mutableStateOf<Profile?>(null) }

    LaunchedEffect(user) {
        try {
            if (profile == null) {
                profile = fetchUserProfile(user.id ?: ULID.makeSpecial(0))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    RawUserOverview(user, profile, internalPadding = internalPadding, cardHeight = cardHeight)
}

@Composable
fun RawUserOverview(
    user: User,
    profile: Profile? = null,
    pfpUrl: String? = null,
    backgroundUrl: String? = null,
    internalPadding: Boolean = true,
    cardHeight: Dp = 128.dp
) {
    val context = LocalContext.current
    var showFullAvatar by remember { mutableStateOf(false) }
    val cosmetics by ProfileCosmeticsStore.current
    val isSelf = user.id != null && user.id == StoatAPI.selfId
    LaunchedEffect(user.id, isSelf) {
        if (isSelf) {
            ProfileCosmeticsStore.load(KVStorage(context), user.id!!)
        }
    }
    val avatarDecoration = if (isSelf) cosmetics.avatarDecoration else "none"
    val profileEffect = if (isSelf) cosmetics.profileEffect else "none"
    val nameplate = if (isSelf) cosmetics.nameplate else "none"
    val chosenBanner = if (isSelf) bannerBrush(cosmetics.bannerStyle) else null
    var teamMemberFlair by remember { mutableStateOf<Brush?>(null) }

    LaunchedEffect(user) {
        runBlocking(Dispatchers.IO) {
            user.id?.let {
                teamMemberFlair = SpecialUsers.teamFlairAsBrush(
                    context,
                    it
                )
            }
        }
    }

    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = Modifier
            .height(cardHeight)
            .padding(horizontal = if (internalPadding) 16.dp else 0.dp)
            .clip(MaterialTheme.shapes.large)
            .then(
                if (user.id in SpecialUsers.TEAM_MEMBER_FLAIRS.keys) {
                    Modifier
                        .border(
                            width = 4.dp,
                            brush = teamMemberFlair
                                ?: Brush.solidColor(Color.Transparent),
                            shape = MaterialTheme.shapes.large
                        )
                } else {
                    Modifier
                }
            )
    ) {
        val background = backgroundUrl ?: profile?.background
        val contentColour = if (background != null) Color.White else LocalContentColor.current
        val pronouns = user.pronouns?.trim()?.takeIf { it.isNotEmpty() }

        if (background != null) {
            RemoteImage(
                url = backgroundUrl
                    ?: "$STOAT_FILES/backgrounds/${if (background is AutumnResource) background.id else null}/${if (background is AutumnResource) background.filename else background}",
                description = null,
                modifier = Modifier
                    .height(cardHeight)
                    .fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )

            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .height(cardHeight)
                    .fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .background(chosenBanner ?: Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surfaceContainer)))
                    .height(cardHeight)
                    .fillMaxWidth()
            )
        }

        if (background != null) {
            bannerOverlay(cosmetics.bannerStyle)?.let { brush ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .background(brush)
                )
            }
        }

        if (profileEffect != "none") {
            CosmeticEffectOverlay(profileEffect, Modifier.fillMaxWidth().height(cardHeight))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            UserAvatar(
                username = user.displayName ?: stringResource(id = R.string.unknown),
                rawUrl = pfpUrl,
                userId = user.id ?: ULID.makeSpecial(0),
                avatar = user.avatar,
                size = if (cardHeight > 128.dp) 76.dp else 48.dp,
                decorationId = avatarDecoration,
                presence = presenceFromStatus(user.status?.presence, user.online ?: false),
                onClick = { showFullAvatar = true }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (nameplate != "none") Nameplate(nameplate, Modifier.matchParentSize())
                Text(
                text = AnnotatedString.Builder().apply {
                    // make sure
                    // - the display name is not null or blank
                    // - the display name is not the same as the username; both trimmed
                    if (!user.displayName.isNullOrBlank() && user.displayName!!.trim() != user.username?.trim()) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(user.displayName)
                        pop()
                        append("\n")
                    }
                    append("${user.username}")
                    pushStyle(SpanStyle(fontWeight = FontWeight.ExtraLight))
                    append("#${user.discriminator}")
                    pop()
                }.toAnnotatedString(),
                color = contentColour,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = if (nameplate == "none") 0.dp else 8.dp, vertical = if (nameplate == "none") 0.dp else 4.dp)
                )
            }

            pronouns?.let {
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = it,
                    color = contentColour,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .widthIn(max = 140.dp)
                )
            }
        }
    }

    if (showFullAvatar) {
        val fullUrl = pfpUrl
            ?: user.avatar?.let { "$STOAT_FILES/avatars/${it.id}" }
            ?: ResourceLocations.userAvatarUrl(user)
        AvatarViewerDialog(
            avatarUrl = fullUrl,
            username = user.username ?: "user",
            displayName = user.displayName,
            onDismissRequest = { showFullAvatar = false }
        )
    }
}
