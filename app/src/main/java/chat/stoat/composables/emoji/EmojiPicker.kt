package chat.stoat.composables.emoji

import android.util.TypedValue
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.stoat.R
import chat.stoat.callbacks.Action
import chat.stoat.callbacks.ActionChannel
import chat.stoat.composables.generic.IconPlaceholder
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.internals.Category
import chat.stoat.internals.EmojiImpl
import chat.stoat.internals.EmojiPickerItem
import chat.stoat.internals.DismodEmojiManager
import kotlinx.coroutines.launch

@Composable
fun EmojiPicker(
    onSearchFocus: (Boolean) -> Unit = {},
    bottomInset: Dp = 0.dp,
    isReactionPicker: Boolean = false,
    onEmojiSelected: (String) -> Unit,
) {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current

    val emojiImpl = remember { EmojiImpl() }
    val dismodEmojis by DismodEmojiManager.emojisFlow.collectAsState()
    val pickerList = remember(emojiImpl, dismodEmojis) { emojiImpl.flatPickerList() }
    val servers = remember(emojiImpl) { emojiImpl.serversWithEmotes() }
    val dismodPacks = remember(dismodEmojis) { DismodEmojiManager.getPacks() }
    val categorySpans = remember(pickerList) { emojiImpl.categorySpans(pickerList) }

    val gridState = rememberLazyGridState()
    val categoryRowScrollState = rememberScrollState()

    val scope = rememberCoroutineScope()

    val spanCount = 7

    // The current category is the one that the user is currently looking at.
    val currentCategory = remember(gridState, categorySpans) {
        derivedStateOf {
            val firstVisible = gridState.firstVisibleItemIndex
            val firstCategory =
                categorySpans.entries.firstOrNull {
                    it.value.first <= firstVisible && it.value.second >= firstVisible
                }?.key

            firstCategory
        }
    }

    LaunchedEffect(currentCategory.value) {
        val offset = categorySpans.entries.indexOfFirst { it.key == currentCategory.value }
        if (offset >= 0) {
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                41f,
                view.resources.displayMetrics
            ).toInt()
            categoryRowScrollState.animateScrollTo(offset * px)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<EmojiPickerItem>() }
    LaunchedEffect(searchQuery) {
        searchResults.clear()
        if (searchQuery.isBlank()) return@LaunchedEffect
        searchResults.addAll(emojiImpl.searchForEmoji(searchQuery))
        gridState.scrollToItem(0)
    }

    val onServerEmoteInfo: (String) -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        scope.launch {
            ActionChannel.send(
                Action.EmoteInfo(
                    it
                )
            )
        }
    }
    val onEmojiClick: (EmojiPickerItem) -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when (it) {
            is EmojiPickerItem.UnicodeEmoji -> onEmojiSelected(it.character)
            is EmojiPickerItem.ServerEmote -> onEmojiSelected(":${it.emote.id}:")
            is EmojiPickerItem.DismodEmoji -> {
                if (isReactionPicker) {
                    onEmojiSelected(it.item.shortcode)
                } else {
                    onEmojiSelected(DismodEmojiManager.formatMarkdown(it.item) + " ")
                }
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                },
                textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .onFocusChanged {
                        onSearchFocus(it.isFocused)
                    }
            ) { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.emoji_picker_search_placeholder),
                            style = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                    innerTextField()

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_24dp),
                            contentDescription = stringResource(R.string.emoji_picker_clear_search),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    searchQuery = ""
                                    focusManager.clearFocus()
                                }
                                .padding(4.dp)
                                .size(20.dp)
                                .align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }

        if (dismodPacks.isNotEmpty() || servers.isNotEmpty()) {
            AnimatedVisibility(searchResults.isEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(categoryRowScrollState)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .height(37.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismodPacks.forEach { (packName, packIcon) ->
                        val cat = Category.DismodEmojiCategory(packName, packIcon)
                        Column(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    scope.launch {
                                        val index = pickerList.indexOfFirst {
                                            it is EmojiPickerItem.Section && it.category == cat
                                        }
                                        if (index >= 0) gridState.scrollToItem(index)
                                    }
                                }
                                .then(
                                    if (currentCategory.value == cat) {
                                        Modifier.background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .aspectRatio(1f)
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = packIcon,
                                fontSize = 16.sp
                            )
                        }
                    }
                    servers.forEach { server ->
                        Column(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    scope.launch {
                                        val index =
                                            pickerList.indexOfFirst {
                                                it is EmojiPickerItem.Section && it.category is Category.ServerEmoteCategory && it.category.server == server
                                            }
                                        if (index >= 0) gridState.scrollToItem(index)
                                    }
                                }
                                .then(
                                    if (currentCategory.value is Category.ServerEmoteCategory && (currentCategory.value as Category.ServerEmoteCategory).server == server) {
                                        Modifier.background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .aspectRatio(1f)
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (server.icon == null) {
                                IconPlaceholder(
                                    name = server.name ?: stringResource(R.string.unknown),
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .fillMaxSize()
                                )
                            } else {
                                RemoteImage(
                                    url = "$STOAT_FILES/icons/${server.icon!!.id}",
                                    allowAnimation = false,
                                    description = server.name,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pickerList.isEmpty() && searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "✨",
                        fontSize = 40.sp
                    )
                    Text(
                        text = "No custom emojis yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Add your own emojis from the catalog manager site to see them here!",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(spanCount),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (searchResults.isNotEmpty()) {
                    item(
                        key = "searchResultsHeader",
                        span = {
                            GridItemSpan(spanCount)
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.emoji_picker_search_results_header),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    }
                }

                // Search results do not get a key, this is intentional.
                items(
                    searchResults.size,
                    span = {
                        val item = searchResults[it]
                        when (item) {
                            is EmojiPickerItem.DismodEmoji -> GridItemSpan(1)
                            is EmojiPickerItem.ServerEmote -> GridItemSpan(1)
                            is EmojiPickerItem.Section -> GridItemSpan(spanCount)
                            else -> GridItemSpan(1)
                        }
                    }
                ) { index ->
                    PickerItem(
                        item = searchResults[index],
                        onClick = onEmojiClick,
                        onServerEmoteInfo = onServerEmoteInfo,
                        lesserHeaders = true
                    )
                }

                if (searchResults.isNotEmpty()) {
                    item(
                        key = "searchResultsFooter",
                        span = {
                            GridItemSpan(spanCount)
                        }
                    ) {
                        HorizontalDivider()
                    }
                }

                items(
                    pickerList.size,
                    span = {
                        val item = pickerList[it]
                        when (item) {
                            is EmojiPickerItem.DismodEmoji -> GridItemSpan(1)
                            is EmojiPickerItem.ServerEmote -> GridItemSpan(1)
                            is EmojiPickerItem.Section -> GridItemSpan(spanCount)
                            else -> GridItemSpan(1)
                        }
                    }
                ) { index ->
                    PickerItem(
                        item = pickerList[index],
                        onClick = onEmojiClick,
                        onServerEmoteInfo = onServerEmoteInfo
                    )
                }

                item(
                    key = "bottomInset",
                    span = {
                        GridItemSpan(spanCount)
                    }
                ) {
                    Spacer(Modifier.height(bottomInset))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColumnScope.PickerItem(
    item: EmojiPickerItem,
    onClick: (EmojiPickerItem) -> Unit,
    onServerEmoteInfo: (String) -> Unit,
    lesserHeaders: Boolean = false
) {
    when (item) {
        is EmojiPickerItem.DismodEmoji -> {
            Column(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        onClick(item)
                    }
                    .aspectRatio(1f)
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RemoteImage(
                    url = item.item.mediaUrl,
                    description = item.item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        is EmojiPickerItem.ServerEmote -> {
            Column(
                modifier = Modifier
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { onClick(item) },
                        onLongClick = { item.emote.id?.let { onServerEmoteInfo(it) } }
                    )
                    .aspectRatio(1f)
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RemoteImage(
                    url = "$STOAT_FILES/emojis/${item.emote.id}",
                    description = item.emote.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
        }

        is EmojiPickerItem.Section -> {
            Text(
                when (item.category) {
                    is Category.DismodEmojiCategory -> "${item.category.emoji} ${item.category.name}"
                    is Category.UnicodeEmojiCategory -> stringResource(
                        item.category.definition.nameResource
                    )

                    is Category.ServerEmoteCategory ->
                        item.category.server.name
                            ?: stringResource(R.string.unknown)
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .then(
                        if (lesserHeaders) {
                            Modifier.alpha(.7f)
                        } else {
                            Modifier
                        }
                    )
            )
        }

        is EmojiPickerItem.UnicodeEmoji -> {
            // Android emojis removed from picker; fallback no-op if ever present
        }
    }
}
