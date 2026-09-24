package chat.stoat.composables.chat

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.stoat.R
import chat.stoat.activities.StoatTweenFloat
import chat.stoat.activities.StoatTweenInt
import chat.stoat.api.internals.BrushCompat
import chat.stoat.api.settings.UserInterfaceFont
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.composables.screens.chat.ChannelIcon
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.Member
import chat.stoat.formatting.MarkdownFormattingHelper
import chat.stoat.formatting.MarkdownVisualTransformation
import chat.stoat.internals.Autocomplete
import chat.stoat.ui.theme.ClaudeTokens
import chat.stoat.ui.theme.StoatTheme
import chat.stoat.ui.theme.Theme
import kotlinx.coroutines.launch

private fun CharSequence.isEmptyOrOnlyNewlines(): Boolean {
    return this.lines().all { it.isEmpty() || it.all { c -> c == '\n' } }
}

private fun TextFieldValue.lastWord(): String? {
    return this.text.substring(0, this.selection.min)
        .split(" ").lastOrNull()
}

private fun CharSequence.lastWordStartsAt(): Int {
    return this.lastIndexOf(" ")
}

sealed class AutocompleteSuggestion {
    data class User(
        val user: chat.stoat.core.model.schemas.User,
        val member: Member?,
        val query: String
    ) : AutocompleteSuggestion()

    data class Channel(
        val channel: chat.stoat.core.model.schemas.Channel,
        val query: String
    ) : AutocompleteSuggestion()

    data class Emoji(
        val shortcode: String,
        val unicode: String?,
        val custom: chat.stoat.core.model.schemas.Emoji?,
        val query: String
    ) : AutocompleteSuggestion()

    data class Role(
        val role: chat.stoat.core.model.schemas.Role,
        val id: String,
        val query: String
    ) : AutocompleteSuggestion()

    data class MassMention(
        val content: String
    ) : AutocompleteSuggestion()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageField(
    initialValue: String,
    onValueChange: (String) -> Unit,
    onAddAttachment: () -> Unit,
    onCommitAttachment: (Uri) -> Unit,
    onPickEmoji: () -> Unit,
    onSendMessage: () -> Unit,
    channelType: ChannelType,
    channelName: String,
    modifier: Modifier = Modifier,
    forceSendButton: Boolean = false,
    sendEnabled: Boolean = true,
    canAttach: Boolean = true,
    disabled: Boolean = false,
    failedValidation: Boolean = false,
    serverId: String? = null,
    channelId: String? = null,
    valueIsBlank: Boolean = false,
    editMode: Boolean = false,
    initialValueDirtyMarker: Any = Unit,
    cancelEdit: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
) {
    val placeholderResource = when (channelType) {
        ChannelType.DirectMessage -> R.string.message_field_placeholder_dm
        ChannelType.Group -> R.string.message_field_placeholder_group
        ChannelType.TextChannel -> R.string.message_field_placeholder_text
        ChannelType.VoiceChannel -> R.string.message_field_placeholder_voice
        ChannelType.SavedMessages -> R.string.message_field_placeholder_notes
    }

    val sendButtonVisible = (!valueIsBlank || forceSendButton) && !disabled && !failedValidation

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = initialValue, selection = TextRange(initialValue.length)))
    }

    var showFormattingToolbar by remember { mutableStateOf(false) }

    val autocompleteSuggestions = remember { mutableStateListOf<AutocompleteSuggestion>() }
    val autocompleteSuggestionState = rememberLazyListState()

    val receiveContentListener = remember {
        ReceiveContentListener { transferableContent ->
            transferableContent.consume { item ->
                val uri = item.uri
                if (uri != null) {
                    onCommitAttachment(uri)
                }
                uri != null
            }
        }
    }

    LaunchedEffect(initialValue, initialValueDirtyMarker) {
        if (initialValue != textFieldValue.text) {
            textFieldValue = TextFieldValue(text = initialValue, selection = TextRange(initialValue.length))
        }
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(textFieldValue.text) {
        onValueChange(textFieldValue.text)

        scope.launch {
            autocompleteSuggestionState.animateScrollToItem(0)
        }
        autocompleteSuggestions.clear()

        if (textFieldValue.text.isNotBlank() &&
            (textFieldValue.selection.min == textFieldValue.selection.max)
        ) {
            val lastWord = textFieldValue.lastWord()
            if (lastWord != null) {
                when {
                    lastWord.startsWith(':') && !lastWord.endsWith(':') -> {
                        autocompleteSuggestions.addAll(Autocomplete.emoji(lastWord.substring(1)))
                    }
                    lastWord.startsWith('@') -> {
                        if (channelId != null && serverId != null) {
                            autocompleteSuggestions.addAll(
                                Autocomplete.userOrRole(channelId, serverId, lastWord.substring(1))
                            )
                        }
                    }
                    lastWord.startsWith('#') -> {
                        if (serverId != null) {
                            autocompleteSuggestions.addAll(
                                Autocomplete.channel(serverId, lastWord.substring(1))
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(editMode) {
        if (editMode) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    fun replaceWord(replacement: String) {
        val lastWordStartsAt = textFieldValue.text
            .substring(0, textFieldValue.selection.max)
            .lastWordStartsAt()
        val replaceStart = if (lastWordStartsAt == -1) 0 else (lastWordStartsAt + 1)
        val replaceEnd = textFieldValue.selection.max
        val newText = textFieldValue.text.replaceRange(replaceStart, replaceEnd, replacement)
        val newCursor = replaceStart + replacement.length
        textFieldValue = TextFieldValue(text = newText, selection = TextRange(newCursor))
    }

    val visualTransformation = remember(MaterialTheme.colorScheme.onSurface) {
        MarkdownVisualTransformation(
            markerColor = Color(0x66888888),
            codeBackground = Color(0x1F888888),
            quoteColor = Color(0xAA888888)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Autocomplete suggestions
        AnimatedVisibility(
            visible = autocompleteSuggestions.isNotEmpty(),
            enter = expandIn(initialSize = { full -> IntSize(full.width, 0) }),
            exit = shrinkOut(targetSize = { full -> IntSize(full.width, 0) })
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                state = autocompleteSuggestionState
            ) {
                items(autocompleteSuggestions.size, key = {
                    when (val item = autocompleteSuggestions[it]) {
                        is AutocompleteSuggestion.User -> item.user.id ?: it.toString()
                        is AutocompleteSuggestion.Channel -> item.channel.id ?: it.toString()
                        is AutocompleteSuggestion.Emoji -> item.shortcode
                        is AutocompleteSuggestion.Role -> item.id
                        is AutocompleteSuggestion.MassMention -> item.content
                    }
                }) {
                    when (val item = autocompleteSuggestions[it]) {
                        is AutocompleteSuggestion.User -> {
                            SuggestionChip(
                                onClick = {
                                    replaceWord("@${item.user.username}#${item.user.discriminator} ")
                                },
                                label = { Text("@${item.user.username}#${item.user.discriminator}") },
                                icon = {
                                    UserAvatar(
                                        username = item.user.username ?: stringResource(R.string.unknown),
                                        userId = item.user.id ?: "",
                                        avatar = item.user.avatar,
                                        rawUrl = item.member?.avatar?.id?.let { "$STOAT_FILES/avatars/$it" },
                                        size = SuggestionChipDefaults.IconSize,
                                    )
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        is AutocompleteSuggestion.Role -> {
                            SuggestionChip(
                                onClick = {
                                    replaceWord("<%${item.id}> ")
                                },
                                label = {
                                    Text(
                                        text = "@${item.role.name}",
                                        style = item.role.colour?.let {
                                            LocalTextStyle.current.copy(brush = BrushCompat.parseColour(it))
                                        } ?: LocalTextStyle.current
                                    )
                                },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                item.role.colour?.let { BrushCompat.parseColour(it) }
                                                    ?: SolidColor(MaterialTheme.colorScheme.primaryContainer)
                                            )
                                            .size(SuggestionChipDefaults.IconSize)
                                            .align(Alignment.CenterHorizontally),
                                    )
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        is AutocompleteSuggestion.Channel -> {
                            SuggestionChip(
                                onClick = {
                                    val replacement = if (item.channel.name?.contains(" ", ignoreCase = true) == true) {
                                        "<#${item.channel.id}> "
                                    } else {
                                        "#${item.channel.name} "
                                    }
                                    replaceWord(replacement)
                                },
                                label = { Text("#${item.channel.name}") },
                                icon = {
                                    if (item.channel.channelType != null) {
                                        ChannelIcon(
                                            channel = item.channel,
                                            modifier = Modifier.size(SuggestionChipDefaults.IconSize)
                                        )
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        is AutocompleteSuggestion.Emoji -> {
                            SuggestionChip(
                                onClick = {
                                    replaceWord("${item.shortcode} ")
                                },
                                label = {
                                    if (item.custom != null) {
                                        Text(":${item.custom.name}:")
                                    } else {
                                        Text(item.shortcode)
                                    }
                                },
                                icon = {
                                    if (item.unicode != null) {
                                        Text(
                                            item.unicode,
                                            modifier = Modifier
                                                .size(SuggestionChipDefaults.IconSize)
                                                .align(Alignment.CenterHorizontally),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    } else {
                                        RemoteImage(
                                            url = "$STOAT_FILES/emojis/${item.custom?.id}",
                                            description = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(SuggestionChipDefaults.IconSize)
                                                .align(Alignment.CenterHorizontally)
                                        )
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        is AutocompleteSuggestion.MassMention -> {
                            SuggestionChip(
                                onClick = {
                                    replaceWord("@${item.content} ")
                                },
                                label = { Text("@${item.content}") },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_campaign_24dp),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(SuggestionChipDefaults.IconSize)
                                            .align(Alignment.CenterHorizontally)
                                    )
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }

        // Formatting toolbar (expands when "Aa" is clicked)
        AnimatedVisibility(
            visible = showFormattingToolbar,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            FormattingToolbar(
                onBold = {
                    val res = MarkdownFormattingHelper.toggleBold(textFieldValue.text, textFieldValue.selection)
                    textFieldValue = TextFieldValue(res.text, res.selection)
                },
                onItalic = {
                    val res = MarkdownFormattingHelper.toggleItalic(textFieldValue.text, textFieldValue.selection)
                    textFieldValue = TextFieldValue(res.text, res.selection)
                },
                onHeading = {
                    val res = MarkdownFormattingHelper.toggleHeading(textFieldValue.text, textFieldValue.selection)
                    textFieldValue = TextFieldValue(res.text, res.selection)
                },
                onCode = {
                    val res = MarkdownFormattingHelper.toggleCode(textFieldValue.text, textFieldValue.selection)
                    textFieldValue = TextFieldValue(res.text, res.selection)
                },
                onQuote = {
                    val res = MarkdownFormattingHelper.toggleQuote(textFieldValue.text, textFieldValue.selection)
                    textFieldValue = TextFieldValue(res.text, res.selection)
                },
                onStrikethrough = {
                    val res = MarkdownFormattingHelper.toggleStrikethrough(textFieldValue.text, textFieldValue.selection)
                    textFieldValue = TextFieldValue(res.text, res.selection)
                },
                onLink = {
                    val res = MarkdownFormattingHelper.insertLink(textFieldValue.text, textFieldValue.selection)
                    textFieldValue = TextFieldValue(res.text, res.selection)
                },
                modifier = Modifier
                    .clip(ClaudeTokens.Shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.heightIn(min = 4.dp))

        // Floating rounded composer body
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ClaudeTokens.Shapes.composerShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(
                    width = ClaudeTokens.Borders.hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = ClaudeTokens.Shapes.composerShape
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading "+" button with touch target >= 48dp
            AnimatedVisibility(canAttach) {
                IconButton(
                    onClick = {
                        if (!editMode) {
                            focusManager.clearFocus()
                            onAddAttachment()
                        }
                    },
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("add_attachment")
                ) {
                    Icon(
                        Icons.Default.Add,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(id = R.string.add_attachment_alt),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Text input with live markdown styling (VisualTransformation)
            BasicTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                visualTransformation = visualTransformation,
                textStyle = LocalTextStyle.current.copy(
                    color = if (failedValidation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.None,
                    showKeyboardOnFocus = false
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 136.dp)
                    .verticalScroll(rememberScrollState())
                    .onFocusChanged { onFocusChange(it.isFocused) }
                    .focusRequester(focusRequester)
                    .contentReceiver(receiveContentListener)
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp) {
                            // Hardware shortcut: Ctrl/Cmd + B -> Bold
                            if ((it.isCtrlPressed || it.isMetaPressed) && it.key == Key.B) {
                                val res = MarkdownFormattingHelper.toggleBold(textFieldValue.text, textFieldValue.selection)
                                textFieldValue = TextFieldValue(res.text, res.selection)
                                return@onKeyEvent true
                            }
                            // Hardware shortcut: Ctrl/Cmd + I -> Italic
                            if ((it.isCtrlPressed || it.isMetaPressed) && it.key == Key.I) {
                                val res = MarkdownFormattingHelper.toggleItalic(textFieldValue.text, textFieldValue.selection)
                                textFieldValue = TextFieldValue(res.text, res.selection)
                                return@onKeyEvent true
                            }
                            // Hardware shortcut: Ctrl/Cmd + Enter -> Send
                            if ((it.isCtrlPressed || it.isMetaPressed) && it.key == Key.Enter && !it.isShiftPressed && !it.isAltPressed) {
                                if (sendEnabled) onSendMessage()
                                return@onKeyEvent true
                            }
                            // Escape -> Cancel edit
                            if (it.key == Key.Escape) {
                                cancelEdit()
                                return@onKeyEvent true
                            }
                        }
                        false
                    },
                decorationBox = { innerTextField ->
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                        if (textFieldValue.text.isEmptyOrOnlyNewlines()) {
                            Text(
                                stringResource(placeholderResource, channelName),
                                style = LocalTextStyle.current.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 15.sp
                                ),
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // "Aa" Text Formatting toggle button with touch target >= 48dp
            IconButton(
                onClick = { showFormattingToolbar = !showFormattingToolbar },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text(
                    text = "Aa",
                    fontWeight = if (showFormattingToolbar) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    color = if (showFormattingToolbar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Emoji button with touch target >= 48dp
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    onPickEmoji()
                },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("pick_emoji")
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mood_24dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = stringResource(id = R.string.pick_emoji_alt),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Terracotta Send button with touch target >= 48dp
            AnimatedVisibility(
                visible = sendButtonVisible,
                enter = expandIn(initialSize = { full -> IntSize(0, full.height) }) +
                        slideInHorizontally(animationSpec = StoatTweenInt, initialOffsetX = { -it }) +
                        fadeIn(animationSpec = StoatTweenFloat),
                exit = shrinkOut(targetSize = { full -> IntSize(0, full.height) }) +
                        slideOutHorizontally(animationSpec = StoatTweenInt, targetOffsetX = { it }) +
                        fadeOut(animationSpec = StoatTweenFloat)
            ) {
                IconButton(
                    onClick = { if (sendEnabled) onSendMessage() },
                    enabled = sendEnabled,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("send_message")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (sendEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = when {
                                editMode -> painterResource(R.drawable.ic_edit_24dp)
                                else -> painterResource(R.drawable.ic_send_24dp)
                            },
                            tint = if (sendEnabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            contentDescription = stringResource(id = R.string.send_alt),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Composer Light", showBackground = true)
@Composable
fun MessageFieldLightPreview() {
    StoatTheme(requestedTheme = Theme.Light, requestedUserInterfaceFont = UserInterfaceFont.Default) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MessageField(
                initialValue = "Hello world! Check out **Claude** style.",
                onValueChange = {},
                onAddAttachment = {},
                onCommitAttachment = {},
                onPickEmoji = {},
                onSendMessage = {},
                channelType = ChannelType.DirectMessage,
                channelName = "General",
                sendEnabled = true
            )
        }
    }
}

@Preview(name = "Composer Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun MessageFieldDarkPreview() {
    StoatTheme(requestedTheme = Theme.Default, requestedUserInterfaceFont = UserInterfaceFont.Default) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MessageField(
                initialValue = "Drafting a quiet *terracotta* message...",
                onValueChange = {},
                onAddAttachment = {},
                onCommitAttachment = {},
                onPickEmoji = {},
                onSendMessage = {},
                channelType = ChannelType.TextChannel,
                channelName = "general",
                sendEnabled = true
            )
        }
    }
}
