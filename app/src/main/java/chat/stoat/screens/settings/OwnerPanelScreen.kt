package chat.stoat.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.settings.CustomBadge
import chat.stoat.api.settings.CustomBadgeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

/**
 * Owner-only panel for assigning custom badges to any user by ID.
 * Opened by long-pressing the version text in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnerPanelScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    val selfId = StoatAPI.selfId ?: ""

    var targetUserId by remember { mutableStateOf(selfId) }
    var isLoading by remember { mutableStateOf(false) }
    val assignedBadges = remember { mutableStateMapOf<CustomBadge, Boolean>() }
    var loadedForUserId by remember { mutableStateOf<String?>(null) }

    var isOwner by remember { mutableStateOf(CustomBadgeStore.isCurrentUserOwner(context)) }
    var ownerPasscode by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf(false) }

    fun loadBadges(userId: String) {
        if (userId.isBlank()) return
        coroutineScope.launch {
            isLoading = true
            val store = CustomBadgeStore.get(context)
            val current = withContext(Dispatchers.IO) { store.getBadges(userId) }
            CustomBadge.entries.forEach { badge ->
                assignedBadges[badge] = badge in current
            }
            loadedForUserId = userId
            isLoading = false
        }
    }

    fun toggleBadge(badge: CustomBadge) {
        val userId = loadedForUserId ?: return
        val newVal = !(assignedBadges[badge] ?: false)
        assignedBadges[badge] = newVal
        coroutineScope.launch(Dispatchers.IO) {
            val store = CustomBadgeStore.get(context)
            if (newVal) store.addBadge(userId, badge)
            else store.removeBadge(userId, badge)
            withContext(Dispatchers.Main) {
                snackbar.showSnackbar(
                    if (newVal) "✅ ${badge.label} assigned to $userId"
                    else "🗑 ${badge.label} removed from $userId"
                )
            }
        }
    }

    // Auto-load self badges on open if owner
    LaunchedEffect(isOwner) {
        if (isOwner && selfId.isNotBlank()) loadBadges(selfId)
    }

    if (!isOwner) {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { Text("👑 Owner Access", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(painter = painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = "Back")
                        }
                    }
                )
            }
        ) { pv ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pv)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield_crown_24dp),
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Owner Access Restricted",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Assigning roles and badges is strictly restricted to the Dismod app owner. Enter the owner passcode to unlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = ownerPasscode,
                    onValueChange = {
                        ownerPasscode = it
                        passcodeError = false
                    },
                    label = { Text("Owner Passcode") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    isError = passcodeError,
                    supportingText = if (passcodeError) {
                        { Text("Invalid owner passcode", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (CustomBadgeStore.authenticateOwner(context, ownerPasscode)) {
                            isOwner = true
                        } else {
                            passcodeError = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock Owner Access")
                }
            }
        }
        return
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = "👑 Owner Panel",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { pv ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pv)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── INFO CARD ──────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👑", fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Owner Panel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Assign or revoke custom badges for any user. Badges are stored locally on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // ── MY USER ID ────────────────────────────────────────────────
            if (selfId.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Your User ID",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                selfId,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("User ID", selfId))
                            coroutineScope.launch { snackbar.showSnackbar("📋 User ID copied!") }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_content_copy_24dp),
                                contentDescription = "Copy"
                            )
                        }
                    }
                }
            }

            // ── USER ID INPUT ─────────────────────────────────────────────
            Text(
                "Assign badges to a user",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = targetUserId,
                onValueChange = { targetUserId = it },
                label = { Text("User ID") },
                placeholder = { Text("Paste a user ID here") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    loadBadges(targetUserId.trim())
                }),
                trailingIcon = {
                    IconButton(onClick = {
                        keyboard?.hide()
                        loadBadges(targetUserId.trim())
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24dp),
                            contentDescription = "Load badges"
                        )
                    }
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (selfId.isNotBlank()) {
                            targetUserId = selfId
                            loadBadges(selfId)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) { Text("Use my ID") }

                Button(
                    onClick = {
                        keyboard?.hide()
                        loadBadges(targetUserId.trim())
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Load badges") }
            }

            // ── BADGE GRID ────────────────────────────────────────────────
            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (loadedForUserId != null) {
                Text(
                    "Badges for: $loadedForUserId",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomBadge.entries.forEach { badge ->
                        val assigned = assignedBadges[badge] ?: false
                        FilterChip(
                            selected = assigned,
                            onClick = { toggleBadge(badge) },
                            label = { Text("${badge.emoji} ${badge.label}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        val userId = loadedForUserId ?: return@Button
                        coroutineScope.launch(Dispatchers.IO) {
                            CustomBadgeStore.get(context).setBadges(userId, emptySet())
                            CustomBadge.entries.forEach { assignedBadges[it] = false }
                            withContext(Dispatchers.Main) {
                                snackbar.showSnackbar("🗑 All custom badges removed from $userId")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove all badges from this user")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
