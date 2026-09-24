package chat.stoat.composables.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import chat.stoat.persistence.KVStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileCosmetics(
    val avatarDecoration: String = "none",
    val profileEffect: String = "none",
    val nameplate: String = "none",
    val bannerStyle: String = "none"
)

data class CosmeticOption(val id: String, val title: String, val category: CosmeticCategory)

enum class CosmeticCategory { Avatar, Effect, Nameplate, Banner }

object ProfileCosmeticsCatalog {
    val options = listOf(
        CosmeticOption("none", "None", CosmeticCategory.Avatar),
        CosmeticOption("orbit", "Orbit", CosmeticCategory.Avatar),
        CosmeticOption("halo", "Halo", CosmeticCategory.Avatar),
        CosmeticOption("none", "None", CosmeticCategory.Effect),
        CosmeticOption("aurora", "Aurora", CosmeticCategory.Effect),
        CosmeticOption("spark", "Starlight", CosmeticCategory.Effect),
        CosmeticOption("none", "None", CosmeticCategory.Nameplate),
        CosmeticOption("violet", "Violet", CosmeticCategory.Nameplate),
        CosmeticOption("ember", "Ember", CosmeticCategory.Nameplate),
        CosmeticOption("none", "None", CosmeticCategory.Banner),
        CosmeticOption("dusk", "Dusk", CosmeticCategory.Banner),
        CosmeticOption("ocean", "Ocean", CosmeticCategory.Banner)
    )

    fun options(category: CosmeticCategory) = options.filter { it.category == category }
}

/** Local per-account choices. The Stoat API currently has no fields for these cosmetics. */
object ProfileCosmeticsStore {
    @Volatile
    private var loadedUserId: String? = null

    var current = androidx.compose.runtime.mutableStateOf(ProfileCosmetics())
        private set

    suspend fun load(storage: KVStorage, userId: String) {
        if (loadedUserId == userId) return
        withContext(Dispatchers.IO) {
            val prefix = "profileCosmetics/$userId/"
            current.value = ProfileCosmetics(
                avatarDecoration = storage.get(prefix + "avatar") ?: "none",
                profileEffect = storage.get(prefix + "effect") ?: "none",
                nameplate = storage.get(prefix + "nameplate") ?: "none",
                bannerStyle = storage.get(prefix + "banner") ?: "none"
            )
            loadedUserId = userId
        }
    }

    suspend fun save(storage: KVStorage, userId: String, value: ProfileCosmetics) {
        val prefix = "profileCosmetics/$userId/"
        withContext(Dispatchers.IO) {
            storage.set(prefix + "avatar", value.avatarDecoration)
            storage.set(prefix + "effect", value.profileEffect)
            storage.set(prefix + "nameplate", value.nameplate)
            storage.set(prefix + "banner", value.bannerStyle)
        }
        current.value = value
        loadedUserId = userId
    }
}

@Composable
fun AvatarDecoration(id: String, modifier: Modifier = Modifier) {
    if (id == "none") return
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.055f
        val radius = size.minDimension * 0.455f
        when (id) {
            "orbit" -> {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF66E4FF), Color(0xFF9869FF), Color(0xFFFF70C8), Color(0xFF66E4FF))),
                    startAngle = -25f,
                    sweepAngle = 305f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawCircle(Color.White, radius = stroke * 1.3f, center = Offset(center.x, center.y - radius))
            }
            "halo" -> {
                drawCircle(Color(0xFFFFD36E).copy(alpha = 0.35f), radius + stroke * 1.8f)
                drawCircle(Color(0xFFFFD36E), radius, style = Stroke(stroke * 1.5f))
            }
        }
    }
}

@Composable
fun CosmeticEffectOverlay(id: String, modifier: Modifier = Modifier) {
    if (id == "none") return
    val transition = rememberInfiniteTransition(label = "profile-cosmetic")
    val phase by transition.animateFloat(
        initialValue = -0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "profile-effect-phase"
    )
    val colors = when (id) {
        "spark" -> listOf(Color(0xFF45246B).copy(alpha = .16f), Color(0xFFFFD36E).copy(alpha = .23f), Color.Transparent)
        else -> listOf(Color(0xFF5EE7DF).copy(alpha = .2f), Color(0xFFB490FF).copy(alpha = .3f), Color(0xFFFF83C7).copy(alpha = .14f))
    }
    Box(modifier.background(Brush.linearGradient(colors, start = Offset(phase * 600f, 0f), end = Offset((phase + 1f) * 600f, 700f))))
}

@Composable
fun BoxScope.Nameplate(nameplate: String, modifier: Modifier = Modifier) {
    val brush = nameplateBrush(nameplate) ?: return
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(6.dp))
    )
}

fun nameplateBrush(nameplate: String): Brush? = when (nameplate) {
    "ember" -> Brush.horizontalGradient(listOf(Color(0xFF9F3B29), Color(0xFFEBA849)))
    "violet" -> Brush.horizontalGradient(listOf(Color(0xFF5B45A6), Color(0xFF9B78E5)))
    else -> null
}

fun bannerBrush(style: String): Brush? = when (style) {
    "dusk" -> Brush.linearGradient(listOf(Color(0xFF392766), Color(0xFF9A547E), Color(0xFFE6A873)))
    "ocean" -> Brush.linearGradient(listOf(Color(0xFF123A5A), Color(0xFF2B8A9A), Color(0xFF82CDBB)))
    else -> null
}

fun bannerOverlay(style: String): Brush? = when (style) {
    "dusk" -> Brush.horizontalGradient(listOf(Color(0xFF392766).copy(alpha = .28f), Color(0xFFE6A873).copy(alpha = .18f)))
    "ocean" -> Brush.horizontalGradient(listOf(Color(0xFF123A5A).copy(alpha = .3f), Color(0xFF82CDBB).copy(alpha = .2f)))
    else -> null
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ProfileCosmeticsSettings(userId: String?) {
    if (userId.isNullOrBlank()) return
    val context = LocalContext.current
    val storage = androidx.compose.runtime.remember(context) { KVStorage(context) }
    val scope = rememberCoroutineScope()
    val cosmetics by ProfileCosmeticsStore.current
    LaunchedEffect(userId) { ProfileCosmeticsStore.load(storage, userId) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Profile cosmetics", style = MaterialTheme.typography.titleMedium)
        Text(
            "Preview and equip original frames, effects, nameplates, and banner styles. Saved on this device and visible only to you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CosmeticChoice("Avatar frame", CosmeticCategory.Avatar, cosmetics.avatarDecoration) { id ->
            val updated = cosmetics.copy(avatarDecoration = id)
            ProfileCosmeticsStore.current.value = updated
            scope.launch { ProfileCosmeticsStore.save(storage, userId, updated) }
        }
        CosmeticChoice("Profile effect", CosmeticCategory.Effect, cosmetics.profileEffect) { id ->
            val updated = cosmetics.copy(profileEffect = id)
            ProfileCosmeticsStore.current.value = updated
            scope.launch { ProfileCosmeticsStore.save(storage, userId, updated) }
        }
        CosmeticChoice("Nameplate", CosmeticCategory.Nameplate, cosmetics.nameplate) { id ->
            val updated = cosmetics.copy(nameplate = id)
            ProfileCosmeticsStore.current.value = updated
            scope.launch { ProfileCosmeticsStore.save(storage, userId, updated) }
        }
        CosmeticChoice("Banner style", CosmeticCategory.Banner, cosmetics.bannerStyle) { id ->
            val updated = cosmetics.copy(bannerStyle = id)
            ProfileCosmeticsStore.current.value = updated
            scope.launch { ProfileCosmeticsStore.save(storage, userId, updated) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CosmeticChoice(
    title: String,
    category: CosmeticCategory,
    selectedId: String,
    onChoose: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProfileCosmeticsCatalog.options(category).forEach { option ->
            FilterChip(
                selected = selectedId == option.id,
                onClick = { onChoose(option.id) },
                label = { Text(option.title) }
            )
        }
    }
}
