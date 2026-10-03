package chat.stoat.composables.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.stoat.R
import chat.stoat.api.settings.CustomBadge
import chat.stoat.api.settings.CustomBadgeStore
import chat.stoat.core.model.schemas.UserBadges
import chat.stoat.core.model.schemas.has
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Data class representing a badge entry for the grid display
data class BadgeEntry(
    val badge: UserBadges,
    val label: String,
    val icon: @Composable () -> Painter,
    val tint: Color
)

@Composable
fun badgeEntries(): List<BadgeEntry> = listOf(
    BadgeEntry(
        badge = UserBadges.Founder,
        label = "Founder",
        icon = { painterResource(R.drawable.user_badge_founder_new) },
        tint = Color(0xFFFF9800)
    ),
    BadgeEntry(
        badge = UserBadges.EarlyAdopter,
        label = "Early adopter",
        icon = { painterResource(R.drawable.user_badge_early_adopter_new) },
        tint = Color(0xFF9C27B0)
    ),
    BadgeEntry(
        badge = UserBadges.BugHunter,
        label = "Bug hunter",
        icon = { painterResource(R.drawable.user_badge_bug_hunter) },
        tint = Color(0xFFF44336)
    ),
    BadgeEntry(
        badge = UserBadges.BetaTester,
        label = "Beta tester",
        icon = { painterResource(R.drawable.user_badge_beta_tester) },
        tint = Color(0xFF009688)
    ),
    BadgeEntry(
        badge = UserBadges.Verified,
        label = "Verified",
        icon = { painterResource(R.drawable.user_badge_verified) },
        tint = Color(0xFF00BCD4)
    ),
    BadgeEntry(
        badge = UserBadges.TopContributor,
        label = "Top contributor",
        icon = { painterResource(R.drawable.user_badge_top_contributor) },
        tint = Color(0xFFFF5722)
    ),
    BadgeEntry(
        badge = UserBadges.NightOwl,
        label = "Night owl",
        icon = { painterResource(R.drawable.user_badge_night_owl) },
        tint = Color(0xFF757575)
    ),
    BadgeEntry(
        badge = UserBadges.Booster,
        label = "Booster",
        icon = { painterResource(R.drawable.user_badge_booster) },
        tint = Color(0xFFE040FB)
    ),
    BadgeEntry(
        badge = UserBadges.OGMember,
        label = "OG member",
        icon = { painterResource(R.drawable.user_badge_og_member) },
        tint = Color(0xFFFF9800)
    ),
    BadgeEntry(
        badge = UserBadges.Developer,
        label = "Developer",
        icon = { painterResource(R.drawable.user_badge_developer) },
        tint = Color(0xFF4CAF50)
    ),
    BadgeEntry(
        badge = UserBadges.Supporter,
        label = "Supporter",
        icon = { painterResource(R.drawable.user_badge_supporter) },
        tint = Color(0xFFE91E63)
    ),
    BadgeEntry(
        badge = UserBadges.Translator,
        label = "Translator",
        icon = { painterResource(R.drawable.user_badge_translator) },
        tint = Color(0xFF3F51B5)
    ),
    BadgeEntry(
        badge = UserBadges.ResponsibleDisclosure,
        label = "Security",
        icon = { painterResource(R.drawable.user_badge_disclosure) },
        tint = Color(0xFF607D8B)
    ),
    BadgeEntry(
        badge = UserBadges.PlatformModeration,
        label = "Moderator",
        icon = { painterResource(R.drawable.user_badge_moderation) },
        tint = Color(0xFF795548)
    ),
    BadgeEntry(
        badge = UserBadges.ActiveSupporter,
        label = "Active Supporter",
        icon = { painterResource(R.drawable.ic_emoji_people_24dp) },
        tint = Color(0xFFE91E63)
    ),
    BadgeEntry(
        badge = UserBadges.Paw,
        label = "Paw",
        icon = { painterResource(R.drawable.user_badge_paw) },
        tint = Color(0xFF8BC34A)
    ),
)

// ─── Custom badge display colours ─────────────────────────────────────────────
val customBadgeColor: Map<CustomBadge, Color> = mapOf(
    CustomBadge.Slut      to Color(0xFFE91E63),
    CustomBadge.Homie     to Color(0xFF795548),
    CustomBadge.Simp      to Color(0xFFFF4081),
    CustomBadge.Gigachad  to Color(0xFF1565C0),
    CustomBadge.Clown     to Color(0xFFFF6F00),
    CustomBadge.Goat      to Color(0xFF2E7D32),
    CustomBadge.Rat       to Color(0xFF6D4C41),
    CustomBadge.Nerd      to Color(0xFF1976D2),
    CustomBadge.King      to Color(0xFFFFB300),
    CustomBadge.Cursed    to Color(0xFF6A1B9A),
)

@Composable
fun BadgeGridItem(
    label: String,
    icon: Painter,
    tint: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 16.dp, horizontal = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.12f))
        ) {
            Image(
                painter = icon,
                contentDescription = label,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp,
            maxLines = 2
        )
    }
}

/** Compact emoji-based display for a custom badge chip. */
@Composable
private fun CustomBadgeChip(badge: CustomBadge) {
    val tint = customBadgeColor[badge] ?: Color(0xFFE91E63)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Special: Slut badge shows female icon from drawable
            if (badge == CustomBadge.Slut) {
                Icon(
                    painter = painterResource(R.drawable.ic_female_24dp),
                    contentDescription = badge.label,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(
                    text = badge.emoji,
                    fontSize = 12.sp,
                    lineHeight = 14.sp
                )
            }
            Spacer(Modifier.width(3.dp))
            Text(
                text = badge.label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BadgeListEntry(badge: UserBadges) {
    val entries = badgeEntries()
    val entry = entries.firstOrNull { it.badge == badge } ?: return
    val icon = entry.icon()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(entry.tint.copy(alpha = 0.12f))
        ) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = entry.label
        )
    }
}

/**
 * Full badge list (used in profile sheet). Shows both official Revolt badges
 * and owner-assigned custom badges for [userId].
 */
@Composable
fun UserBadgeList(badges: Long, userId: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val allEntries = badgeEntries()
    val activeBadges = remember(badges) { allEntries.filter { badges has it.badge } }

    var customBadges by remember(userId) { mutableStateOf<Set<CustomBadge>>(emptySet()) }
    LaunchedEffect(userId) {
        if (!userId.isNullOrBlank()) {
            CustomBadgeStore.get(context).observeBadges(userId).collect {
                customBadges = it
            }
        } else {
            customBadges = emptySet()
        }
    }

    if (activeBadges.isEmpty() && customBadges.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        activeBadges.forEach { entry ->
            BadgeListEntry(entry.badge)
        }
        // Custom badges (owner-assigned)
        customBadges.forEach { badge ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CustomBadgeChip(badge)
            }
        }
    }
}

/**
 * Inline badge row (used in profile card header). Shows both official and custom badges.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserBadgeRow(badges: Long, userId: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val allEntries = badgeEntries()
    val activeBadges = remember(badges) { allEntries.filter { badges has it.badge } }

    var customBadges by remember(userId) { mutableStateOf<Set<CustomBadge>>(emptySet()) }
    LaunchedEffect(userId) {
        if (!userId.isNullOrBlank()) {
            CustomBadgeStore.get(context).observeBadges(userId).collect {
                customBadges = it
            }
        } else {
            customBadges = emptySet()
        }
    }

    if (activeBadges.isEmpty() && customBadges.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        activeBadges.forEach { entry ->
            val icon = entry.icon()
            Image(
                painter = icon,
                contentDescription = entry.label,
                modifier = Modifier.size(32.dp)
            )
        }
        customBadges.forEach { badge ->
            CustomBadgeChip(badge)
        }
    }
}