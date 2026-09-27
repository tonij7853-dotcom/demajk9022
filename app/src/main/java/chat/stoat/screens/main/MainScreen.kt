package chat.stoat.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.screens.chat.views.OverviewScreen

enum class MainScreenTab {
    Communities,
    Conversations,
    Overview
}

@Composable
fun MainScreen(navController: NavController) {
    var currentTab by rememberSaveable { mutableStateOf(MainScreenTab.Communities) }

    val communitiesMentions = StoatAPI.unreads.getTotalCommunitiesMentionCount()
    val conversationsMentions = StoatAPI.unreads.getTotalConversationsMentionCount()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == MainScreenTab.Communities,
                    onClick = { currentTab = MainScreenTab.Communities },
                    icon = {
                        if (communitiesMentions > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = Color(0xFFED4245),
                                        contentColor = Color.White
                                    ) {
                                        Text(if (communitiesMentions > 99) "99+" else "$communitiesMentions")
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_tag_24dp),
                                    contentDescription = null,
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_tag_24dp),
                                contentDescription = null,
                            )
                        }
                    },
                    label = {
                        Text("Servers")
                    }
                )
                NavigationBarItem(
                    selected = currentTab == MainScreenTab.Conversations,
                    onClick = { currentTab = MainScreenTab.Conversations },
                    icon = {
                        val chatIcon = painterResource(
                            if (currentTab == MainScreenTab.Conversations) {
                                R.drawable.ic_chat_24dp
                            } else {
                                R.drawable.ic_chat_24dp
                            }
                        )
                        if (conversationsMentions > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = Color(0xFFED4245),
                                        contentColor = Color.White
                                    ) {
                                        Text(if (conversationsMentions > 99) "99+" else "$conversationsMentions")
                                    }
                                }
                            ) {
                                Icon(
                                    painter = chatIcon,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            Icon(
                                painter = chatIcon,
                                contentDescription = null,
                            )
                        }
                    },
                    label = {
                        Text("Chats")
                    }
                )
                NavigationBarItem(
                    selected = currentTab == MainScreenTab.Overview,
                    onClick = { currentTab = MainScreenTab.Overview },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_person_24dp),
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text("You")
                    }
                )
            }
        },
    ) { pv ->
        Box(Modifier.padding(pv)) {
            when (currentTab) {
                MainScreenTab.Communities -> {
                    CommunitiesScreen(
                        navController
                    )
                }
                MainScreenTab.Conversations -> {
                    ConversationsScreen(
                        navController
                    )
                }

                MainScreenTab.Overview -> {
                    OverviewScreen(
                        navController,
                        useDrawer = false,
                        onDrawerClicked = {},
                        includePadding = false
                    )
                }
            }
        }
    }
}