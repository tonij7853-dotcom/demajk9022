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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.stoat.R
import chat.stoat.core.model.schemas.UserBadges
import chat.stoat.core.model.schemas.has

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
        tint = Color(0xFFFFC107)
    ),
    BadgeEntry(
        badge = UserBadges.OGMember,
        label = "OG member",
        icon = { painterResource(R.drawable.user_badge_og_member) },
        tint = Color(0xFFFF9800)
    ),
    // Additional legacy badges mapped to better display
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

@Composable
fun UserBadgeList(badges: Long) {
    val effectiveBadges = badges or UserBadges.Supporter.value
    val allEntries = badgeEntries()
    val activeBadges = allEntries.filter { effectiveBadges has it.badge }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        activeBadges.forEach { entry ->
            BadgeListEntry(entry.badge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserBadgeRow(badges: Long) {
    val effectiveBadges = badges or UserBadges.Supporter.value
    val allEntries = badgeEntries()
    val activeBadges = allEntries.filter { effectiveBadges has it.badge }

    FlowRow(
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
    }
}