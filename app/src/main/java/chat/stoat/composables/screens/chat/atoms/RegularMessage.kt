package chat.stoat.composables.screens.chat.atoms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.api.settings.LoadedSettings
import chat.stoat.api.settings.MessageReplyStyle
import chat.stoat.callbacks.Action
import chat.stoat.callbacks.ActionChannel
import chat.stoat.composables.chat.Message
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.Message
import com.mikepenz.markdown.model.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Discord Blurple matching Discord's swipe-to-reply button
private val DiscordBlurple = Color(0xFF5865F2)

/**
 * Display a regular message in the LazyColumn of the chat screen with Discord-style swipe-to-reply.
 */
@Composable
fun RegularMessage(
    message: Message,
    channel: Channel?,
    drawerIsOpen: Boolean,
    setDrawerGestureEnabled: (Boolean) -> Unit,
    setDisableScroll: (Boolean) -> Unit,
    showMessageBottomSheet: (String) -> Unit,
    showReactBottomSheet: () -> Unit,
    putTextAtCursorPosition: (String) -> Unit,
    replyToMessage: suspend (String) -> Unit,
    jumpToMessage: (String) -> Unit = {},
    scope: CoroutineScope = rememberCoroutineScope(),
    mdAst: State? = null
) {
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    var isActivated by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 64.dp.toPx() } }
    val maxDragPx = remember(density) { with(density) { 96.dp.toPx() } }

    val canReply = message.id != null
    val swipeEnabled = canReply && LoadedSettings.messageReplyStyle != MessageReplyStyle.None && !drawerIsOpen

    val swipeModifier = if (swipeEnabled) {
        Modifier.pointerInput(message.id, swipeEnabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var totalX = 0f
                var totalY = 0f
                var isDragging = false
                val touchSlop = viewConfiguration.touchSlop

                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val deltaX = change.position.x - change.previousPosition.x
                        val deltaY = change.position.y - change.previousPosition.y

                        if (!isDragging) {
                            totalX += deltaX
                            totalY += deltaY

                            // If vertical movement exceeds horizontal movement, let LazyColumn scroll normally
                            if (abs(totalY) > touchSlop && abs(totalY) > abs(totalX)) {
                                break
                            }

                            // If swiping rightward, let the drawer gesture handle it
                            if (totalX > touchSlop) {
                                break
                            }

                            // If child already consumed horizontal scroll (e.g. code block), skip
                            if (change.isConsumed) {
                                break
                            }

                            // Swiping leftward past touch slop
                            if (totalX < -touchSlop && abs(totalX) > abs(totalY)) {
                                isDragging = true
                                setDrawerGestureEnabled(false)
                                setDisableScroll(true)
                            }
                        }

                        if (isDragging) {
                            change.consume()

                            // Rubber-band resistance past threshold
                            val raw = change.position.x - down.position.x
                            val targetOffset = if (raw < -thresholdPx) {
                                -thresholdPx - (-raw - thresholdPx) * 0.3f
                            } else {
                                raw
                            }.coerceIn(-maxDragPx, 0f)

                            scope.launch {
                                offsetX.snapTo(targetOffset)
                            }

                            val nowActivated = targetOffset <= -thresholdPx
                            if (nowActivated != isActivated) {
                                isActivated = nowActivated
                                if (nowActivated) {
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                }
                            }
                        }
                    }
                } finally {
                    if (isDragging) {
                        val shouldTriggerReply = isActivated
                        isActivated = false
                        if (shouldTriggerReply) {
                            message.id?.let { msgId ->
                                scope.launch {
                                    replyToMessage(msgId)
                                }
                            }
                        }
                        scope.launch {
                            offsetX.animateTo(
                                0f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                        setDrawerGestureEnabled(true)
                        setDisableScroll(false)
                    }
                }
            }
        }
    } else Modifier

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Discord-style Floating Circular Reply Button on the right edge (only composed when actively swiping)
        if (offsetX.value < -2f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .graphicsLayer {
                        val curr = offsetX.value
                        val progress = (abs(curr) / thresholdPx).coerceIn(0f, 1f)
                        val scale = if (isActivated) 1.15f else (0.6f + 0.4f * progress)
                        scaleX = scale
                        scaleY = scale
                        alpha = (abs(curr) / (thresholdPx * 0.35f)).coerceIn(0f, 1f)
                        translationX = (curr + thresholdPx).coerceAtLeast(0f) * 0.25f
                    }
                    .size(42.dp)
                    .background(
                        color = if (isActivated) DiscordBlurple else DiscordBlurple.copy(alpha = 0.85f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_reply_24dp),
                    contentDescription = stringResource(R.string.message_context_sheet_actions_reply),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Message Content with animated horizontal offset
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(swipeModifier)
        ) {
            Message(
                message = message,
                onMessageContextMenu = {
                    message.id?.let { messageId ->
                        showMessageBottomSheet(messageId)
                    }
                },
                onAvatarClick = {
                    if (message.webhook != null) {
                        scope.launch {
                            ActionChannel.send(Action.OpenWebhookSheet)
                        }
                    } else {
                        message.author?.let { author ->
                            scope.launch {
                                ActionChannel.send(Action.OpenUserSheet(author, channel?.server))
                            }
                        }
                    }
                },
                onNameClick = {
                    if (message.webhook != null) {
                        scope.launch {
                            ActionChannel.send(Action.OpenWebhookSheet)
                        }
                    } else {
                        message.author?.let { author ->
                            scope.launch {
                                ActionChannel.send(Action.OpenUserSheet(author, channel?.server))
                            }
                        }
                    }
                },
                canReply = true,
                onReply = {
                    message.id?.let { messageId ->
                        scope.launch {
                            replyToMessage(messageId)
                        }
                    }
                },
                onAddReaction = {
                    message.id?.let {
                        showReactBottomSheet()
                    }
                },
                onJumpToMessage = jumpToMessage,
                fromWebhook = message.webhook != null,
                webhookName = message.webhook?.name,
                mdAst = mdAst
            )
        }
    }
}
