package chat.stoat.screens.chat.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.stoat.api.routes.server.editMemberNickname
import chat.stoat.internals.CustomNicknames
import kotlinx.coroutines.launch

@Composable
fun ChangeNicknameDialog(
    userId: String,
    serverId: String? = null,
    currentNickname: String? = null,
    onDismissRequest: () -> Unit,
    onNicknameSaved: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nickname by remember { mutableStateOf(currentNickname ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = if (serverId != null) "Change Server Nickname" else "Change Nickname",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (serverId != null) {
                        "Enter a nickname for this user in this server. Leave empty to reset to default."
                    } else {
                        "Enter a custom nickname for this user in your DMs. Leave empty to reset."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = {
                        if (it.length <= 32) nickname = it
                    },
                    label = { Text("Nickname") },
                    placeholder = { Text("Enter nickname...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = "${nickname.length}/32",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    scope.launch {
                        try {
                            val newNick = nickname.trim().takeIf { it.isNotEmpty() }
                            if (serverId != null) {
                                editMemberNickname(serverId, userId, newNick)
                            } else {
                                CustomNicknames.setNickname(context, userId, newNick)
                            }
                            Toast.makeText(context, "Nickname updated", Toast.LENGTH_SHORT).show()
                            onNicknameSaved(newNick)
                            onDismissRequest()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: "Failed to update nickname",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(18.dp)
                    )
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!currentNickname.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            if (isSaving) return@TextButton
                            isSaving = true
                            scope.launch {
                                try {
                                    if (serverId != null) {
                                        editMemberNickname(serverId, userId, null)
                                    } else {
                                        CustomNicknames.setNickname(context, userId, null)
                                    }
                                    Toast.makeText(context, "Nickname reset", Toast.LENGTH_SHORT).show()
                                    onNicknameSaved(null)
                                    onDismissRequest()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to reset", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(
                    onClick = onDismissRequest,
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
