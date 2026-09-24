package chat.stoat.composables.chat

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.stoat.R
import chat.stoat.ui.theme.ClaudeTokens
import chat.stoat.ui.theme.FragmentMono
import chat.stoat.ui.theme.Newsreader
import chat.stoat.ui.theme.StoatTheme
import chat.stoat.ui.theme.Theme
import chat.stoat.api.settings.UserInterfaceFont

@Composable
fun FormattingToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onHeading: () -> Unit,
    onCode: () -> Unit,
    onQuote: () -> Unit,
    onStrikethrough: () -> Unit,
    onLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bold button: touch target >= 48dp
        IconButton(
            onClick = onBold,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(ClaudeTokens.Shapes.medium)
        ) {
            Text(
                text = "B",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Italic button: touch target >= 48dp
        IconButton(
            onClick = onItalic,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(ClaudeTokens.Shapes.medium)
        ) {
            Text(
                text = "I",
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Heading button (Newsreader serif H)
        IconButton(
            onClick = onHeading,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(ClaudeTokens.Shapes.medium)
        ) {
            Text(
                text = "H",
                fontFamily = Newsreader,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Code button
        IconButton(
            onClick = onCode,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(ClaudeTokens.Shapes.medium)
        ) {
            Text(
                text = "<>",
                fontFamily = FragmentMono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Quote button
        IconButton(
            onClick = onQuote,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(ClaudeTokens.Shapes.medium)
        ) {
            Text(
                text = "“",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Strikethrough button
        IconButton(
            onClick = onStrikethrough,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(ClaudeTokens.Shapes.medium)
        ) {
            Text(
                text = "S",
                textDecoration = TextDecoration.LineThrough,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Link button
        IconButton(
            onClick = onLink,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(ClaudeTokens.Shapes.medium)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_link_24dp),
                contentDescription = "Insert link",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
fun FormattingToolbarLightPreview() {
    StoatTheme(requestedTheme = Theme.Light, requestedUserInterfaceFont = UserInterfaceFont.Default) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            FormattingToolbar(
                onBold = {},
                onItalic = {},
                onHeading = {},
                onCode = {},
                onQuote = {},
                onStrikethrough = {},
                onLink = {}
            )
        }
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun FormattingToolbarDarkPreview() {
    StoatTheme(requestedTheme = Theme.Default, requestedUserInterfaceFont = UserInterfaceFont.Default) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            FormattingToolbar(
                onBold = {},
                onItalic = {},
                onHeading = {},
                onCode = {},
                onQuote = {},
                onStrikethrough = {},
                onLink = {}
            )
        }
    }
}
