package chat.stoat.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.logging.AppLogger
import chat.stoat.logging.DebugLogServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ParsedLogItem(
    val rawJson: String,
    val time: String,
    val level: String,
    val action: String,
    val screen: String,
    val sessionId: String,
    val detailsFormatted: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var logLines by remember { mutableStateOf<List<ParsedLogItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalSizeText by remember { mutableStateOf("0 KB") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Errors", "GIF/Media"

    fun reloadLogs() {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val lines = AppLogger.readAllLogLines().asReversed() // Newest first
            val parsed = lines.mapNotNull { line ->
                try {
                    val obj = JSONObject(line)
                    val detailsObj = obj.optJSONObject("details")
                    val detailsStr = detailsObj?.toString(2) ?: "{}"
                    ParsedLogItem(
                        rawJson = line,
                        time = obj.optString("time", ""),
                        level = obj.optString("level", "INFO"),
                        action = obj.optString("action", "unknown"),
                        screen = obj.optString("screen", ""),
                        sessionId = obj.optString("session_id", ""),
                        detailsFormatted = detailsStr
                    )
                } catch (_: Exception) {
                    null
                }
            }
            val sizeStr = AppLogger.getTotalLogSizeFormatted()
            withContext(Dispatchers.Main) {
                logLines = parsed
                totalSizeText = sizeStr
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        AppLogger.setCurrentScreen("DebugLogsScreen")
        reloadLogs()
    }

    val filteredLogs = remember(logLines, searchQuery, selectedFilter) {
        logLines.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "Errors" -> item.level == "ERROR" || item.level == "WARN"
                "GIF/Media" -> item.action.contains("gif", ignoreCase = true) ||
                        item.action.contains("media", ignoreCase = true) ||
                        item.action.contains("image", ignoreCase = true)
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.action.contains(searchQuery, ignoreCase = true) ||
                    item.screen.contains(searchQuery, ignoreCase = true) ||
                    item.detailsFormatted.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Debug Logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${logLines.size} entries • Total $totalSizeText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { reloadLogs() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_undo_24dp),
                            contentDescription = "Refresh",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── LIVE DEBUG SERVER STATUS & DIRECT CONNECT ──
            var showServerDetails by remember { mutableStateOf(false) }
            val localIp = remember { DebugLogServer.getLocalIpAddress() }
            val port = DebugLogServer.activePort

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showServerDetails = !showServerDetails },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Live Log Server: Active (Port $port)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (showServerDetails) "Hide Info" else "Direct Connect Info",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (showServerDetails) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Direct fetch endpoints for AI debugging (no copy-paste needed):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))

                        if (localIp != null) {
                            val wifiUrl = "http://$localIp:$port/logs"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Live Wi-Fi URL", wifiUrl))
                                        Toast.makeText(context, "Copied Wi-Fi URL to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Wi-Fi Direct Fetch URL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(wifiUrl, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontSize = 11.sp)
                                }
                                Icon(painter = painterResource(R.drawable.ic_content_copy_24dp), contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                        }

                        val adbCmd = "adb forward tcp:$port tcp:$port"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCmd))
                                    Toast.makeText(context, "Copied ADB command to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("USB ADB Forward Command", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(adbCmd, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontSize = 11.sp)
                            }
                            Icon(painter = painterResource(R.drawable.ic_content_copy_24dp), contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ── EXPORT ACTION BUTTONS (Copy 200, Copy Errors, Share, Clear, Test Error) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Copy Last 200 Logs (For AI)
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val text = AppLogger.getLogsFormattedForAi(maxCount = 200, errorsOnly = false)
                                withContext(Dispatchers.Main) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Dismod Logs for AI", text))
                                    Toast.makeText(context, "Copied last 200 logs for AI to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_content_copy_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Last 200 (AI)")
                    }

                    // 2. Copy Errors Only
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val text = AppLogger.getLogsFormattedForAi(maxCount = 200, errorsOnly = true)
                                withContext(Dispatchers.Main) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Dismod Errors for AI", text))
                                    Toast.makeText(context, "Copied errors only to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_report_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Errors Only")
                    }

                    // 3. Share Log File
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val file = AppLogger.createExportFile(context)
                                withContext(Dispatchers.Main) {
                                    if (file != null && file.exists()) {
                                        try {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.provider",
                                                file
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, "Dismod App Diagnostics Logs")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Dismod Logs"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "No log files available to share", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_file_export_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Share Log File")
                    }

                    // 4. Trigger Test Error (Verify logger capture)
                    OutlinedButton(
                        onClick = {
                            AppLogger.e(
                                action = "test_diagnostic_error_triggered",
                                details = mapOf(
                                    "test_note" to "Diagnostic test triggered from Debug Logs screen",
                                    "device_time" to System.currentTimeMillis()
                                ),
                                throwable = RuntimeException("Dismod Verified Test Exception: Logging system is active")
                            )
                            Toast.makeText(context, "Logged test error!", Toast.LENGTH_SHORT).show()
                            reloadLogs()
                        }
                    ) {
                        Text("Trigger Test Error", color = Color(0xFFEF5350))
                    }

                    // 5. Clear Logs
                    OutlinedButton(
                        onClick = {
                            AppLogger.clearLogs()
                            reloadLogs()
                            Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Clear Logs")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Search field & filter chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        placeholder = { Text("Filter logs...", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close_24dp),
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )

                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = selectedFilter == "Errors",
                        onClick = { selectedFilter = "Errors" },
                        label = { Text("Errors", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = selectedFilter == "GIF/Media",
                        onClick = { selectedFilter = "GIF/Media" },
                        label = { Text("GIFs", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // ── LOG LIST ──────────────────────────────────────────────────────────
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty() || selectedFilter != "All") "No matching logs found" else "No logs recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredLogs, key = { it.time + it.action + it.detailsFormatted.hashCode() }) { item ->
                        LogCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(item: ParsedLogItem) {
    var expanded by remember { mutableStateOf(false) }

    val levelColor = when (item.level) {
        "ERROR" -> Color(0xFFEF5350)
        "WARN" -> Color(0xFFFFA726)
        "INFO" -> Color(0xFF42A5F5)
        else -> Color(0xFF9E9E9E)
    }

    val cardBg = when (item.level) {
        "ERROR" -> Color(0xFF2A1515)
        "WARN" -> Color(0xFF2B2012)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Level Badge
                Surface(
                    color = levelColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.level,
                        color = levelColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Action Name
                Text(
                    text = item.action,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Time (Short HH:mm:ss)
                val timeShort = if (item.time.length >= 19) item.time.substring(11, 19) else item.time
                Text(
                    text = timeShort,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            // Screen name info
            if (item.screen.isNotEmpty()) {
                Text(
                    text = "screen: ${item.screen}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Expanded details or single-line preview
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp)),
                        color = Color(0xFF101216)
                    ) {
                        Text(
                            text = item.detailsFormatted,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            ),
                            color = Color(0xFFECEFF1),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
