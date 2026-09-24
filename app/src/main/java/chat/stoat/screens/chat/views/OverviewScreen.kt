package chat.stoat.screens.chat.views

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.user.fetchSelf
import chat.stoat.composables.generic.ListHeader
import chat.stoat.composables.generic.NonIdealState
import chat.stoat.composables.screens.settings.UserOverview
import chat.stoat.composables.skeletons.UserOverviewSkeleton
import chat.stoat.core.model.schemas.User
import chat.stoat.internals.extensions.zero
import chat.stoat.screens.chat.LocalIsConnected
import chat.stoat.screens.settings.SettingsScreenViewModel
import chat.stoat.sheets.UserCardSheet
import chat.stoat.updater.DismodUpdater
import io.sentry.Sentry
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    navController: NavController,
    useDrawer: Boolean,
    onDrawerClicked: () -> Unit,
    includePadding: Boolean = true,
    settingsViewModel: SettingsScreenViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by rememberSaveable { mutableStateOf(true) }
    var user by rememberSaveable { mutableStateOf<User?>(null) }

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

    var showUserCardSheet by rememberSaveable { mutableStateOf(false) }

    if (showUserCardSheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showUserCardSheet = false },
        ) {
            UserCardSheet(user = user)
        }
    }

    Scaffold(
        topBar = {
            Column {
                AnimatedVisibility(LocalIsConnected.current) {
                    Spacer(
                        Modifier.height(
                            WindowInsets.statusBars.asPaddingValues()
                                .calculateTopPadding()
                        )
                    )
                }
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.overview_screen_title),
                            fontWeight = FontWeight.Bold
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
                                contentDescription = stringResource(id = R.string.settings)
                            )
                        }
                    },
                    windowInsets = WindowInsets.zero
                )
            }
        },
        contentWindowInsets = if (includePadding) ScaffoldDefaults.contentWindowInsets else ScaffoldDefaults.contentWindowInsets.exclude(
            NavigationBarDefaults.windowInsets
        )
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pv)
                .verticalScroll(rememberScrollState())
        ) {
            AnimatedContent(targetState = isLoading, label = "isLoading") { loading ->
                if (loading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        UserOverviewSkeleton(false)
                    }
                } else {
                    user?.let { u ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            UserOverview(u, internalPadding = false)
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(40.dp))
                }
            } else {
                // Quick actions: Edit Profile & Share Profile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = { navController.navigate("settings/profile") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.user_info_sheet_edit_profile))
                    }

                    FilledTonalButton(
                        onClick = { showUserCardSheet = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ios_share_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.overview_screen_share_profile))
                    }
                }

                // Account Category
                ListHeader {
                    Text(stringResource(R.string.settings_category_account))
                }
                OverviewListItem(
                    first = true,
                    title = stringResource(R.string.settings_account),
                    icon = R.drawable.ic_lock_24dp,
                    onClick = { navController.navigate("settings/account") }
                )
                Spacer(Modifier.height(2.dp))
                OverviewListItem(
                    title = stringResource(R.string.settings_profile),
                    icon = R.drawable.ic_id_card_24dp,
                    onClick = { navController.navigate("settings/profile") }
                )
                Spacer(Modifier.height(2.dp))
                OverviewListItem(
                    last = true,
                    title = stringResource(R.string.settings_sessions),
                    icon = R.drawable.ic_devices_24dp,
                    onClick = { navController.navigate("settings/sessions") }
                )

                // App Settings Category
                ListHeader {
                    Text(stringResource(R.string.settings_category_general))
                }
                OverviewListItem(
                    first = true,
                    title = stringResource(R.string.settings_appearance),
                    icon = R.drawable.ic_palette_24dp,
                    onClick = { navController.navigate("settings/appearance") }
                )
                Spacer(Modifier.height(2.dp))
                OverviewListItem(
                    title = stringResource(R.string.settings_notifications),
                    icon = R.drawable.ic_notifications_24dp,
                    onClick = { navController.navigate("settings/notifications") }
                )
                Spacer(Modifier.height(2.dp))
                OverviewListItem(
                    title = stringResource(R.string.settings_chat),
                    icon = R.drawable.ic_chat_24dp,
                    onClick = { navController.navigate("settings/chat") }
                )
                Spacer(Modifier.height(2.dp))
                OverviewListItem(
                    last = true,
                    title = stringResource(R.string.settings_language),
                    icon = R.drawable.ic_language_24dp,
                    onClick = { navController.navigate("settings/language") }
                )

                // Dismod Category
                ListHeader {
                    Text("Dismod")
                }
                OverviewListItem(
                    first = true,
                    title = stringResource(R.string.settings_changelog),
                    icon = R.drawable.ic_campaign_24dp,
                    onClick = { navController.navigate("changelog/latest") }
                )
                Spacer(Modifier.height(2.dp))
                OverviewListItem(
                    title = "Check for Updates",
                    icon = R.drawable.ic_download_24dp,
                    onClick = {
                        DismodUpdater.checkForUpdates(context, scope, notifyIfNoUpdate = true)
                    }
                )
                Spacer(Modifier.height(2.dp))
                OverviewListItem(
                    last = true,
                    title = stringResource(R.string.about),
                    icon = R.drawable.ic_info_24dp,
                    onClick = { navController.navigate("about") }
                )

                // Logout
                Spacer(Modifier.height(16.dp))
                OverviewListItem(
                    first = true,
                    last = true,
                    danger = true,
                    title = stringResource(R.string.logout),
                    icon = R.drawable.ic_logout_24dp,
                    onClick = {
                        settingsViewModel.logout()
                        navController.navigate("login/greeting") {
                            popUpTo("chat") {
                                inclusive = true
                            }
                        }
                    }
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun OverviewListItem(
    first: Boolean = false,
    last: Boolean = false,
    danger: Boolean = false,
    title: String,
    icon: Int,
    onClick: () -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        headlineContent = {
            Text(
                text = title,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        },
        trailingContent = {
            if (!danger) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_forward_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(
                when {
                    first && last -> MaterialTheme.shapes.large
                    first -> MaterialTheme.shapes.extraSmall.copy(
                        topStart = MaterialTheme.shapes.large.topStart,
                        topEnd = MaterialTheme.shapes.large.topEnd
                    )
                    last -> MaterialTheme.shapes.extraSmall.copy(
                        bottomStart = MaterialTheme.shapes.large.bottomStart,
                        bottomEnd = MaterialTheme.shapes.large.bottomEnd
                    )
                    else -> MaterialTheme.shapes.extraSmall
                }
            )
            .clickable(onClick = onClick)
    )
}