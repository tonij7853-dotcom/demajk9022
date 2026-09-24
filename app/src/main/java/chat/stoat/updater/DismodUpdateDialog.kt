package chat.stoat.updater

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.stoat.BuildConfig
import chat.stoat.R

@Composable
fun DismodUpdateDialog(
    state: DismodUpdateState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    when (state) {
        is DismodUpdateState.UpdateAvailable -> {
            val info = state.info
            AlertDialog(
                onDismissRequest = {
                    if (!info.forceUpdate) onDismiss()
                },
                icon = {
                    Image(
                        painter = painterResource(id = R.drawable.dismod_logo),
                        contentDescription = "Dismod Logo",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                },
                title = {
                    Text(
                        text = "Update Available",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "➔",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "v${info.versionName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (!info.changelog.isNullOrBlank()) {
                            Text(
                                text = "What's New:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = info.changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = "A new version of Dismod is ready with the latest improvements and features.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            DismodUpdater.startUpdate(context, coroutineScope, info)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Update Now", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    if (!info.forceUpdate) {
                        TextButton(onClick = onDismiss) {
                            Text("Later")
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        is DismodUpdateState.Downloading -> {
            val info = state.info
            val progress = state.progress
            AlertDialog(
                onDismissRequest = { /* Prevent dismiss while downloading */ },
                icon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.5.dp
                    )
                },
                title = {
                    Text(
                        text = "Downloading Update...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Dismod v${info.versionName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { /* Wait for download */ },
                        enabled = false,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Installing soon...")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        is DismodUpdateState.ReadyToInstall -> {
            val file = state.apkFile
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text("Download Complete")
                },
                text = {
                    Text("The update has been downloaded. Tap Install to finish updating Dismod.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            DismodUpdater.installApk(context, file)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Install Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        is DismodUpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text("Update Notice")
                },
                text = {
                    Text(state.message)
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        else -> {
            // Idle or Checking - no dialog displayed
        }
    }
}
