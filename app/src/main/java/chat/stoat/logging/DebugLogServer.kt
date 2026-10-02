package chat.stoat.logging

import android.os.Build
import android.util.Log
import chat.stoat.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Lightweight embedded HTTP debug server running locally inside the app.
 * Allows an AI assistant or developer to fetch real-time logs, errors, and system status
 * directly over Wi-Fi or USB ADB (via `adb forward tcp:8088 tcp:8088`) without requiring
 * manual copy/pasting.
 */
object DebugLogServer {
    private const val TAG = "DebugLogServer"
    const val DEFAULT_PORT = 8088

    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isRunning = false
    private val clientPool = Executors.newCachedThreadPool()

    var activePort: Int = DEFAULT_PORT
        private set

    fun start() {
        if (isRunning) return
        try {
            var ss: ServerSocket? = null
            var port = DEFAULT_PORT
            for (p in DEFAULT_PORT..DEFAULT_PORT + 5) {
                try {
                    ss = ServerSocket(p)
                    port = p
                    break
                } catch (_: Exception) {}
            }
            if (ss == null) {
                Log.w(TAG, "Unable to bind DebugLogServer to any port in 8088..8093")
                return
            }

            serverSocket = ss
            activePort = port
            isRunning = true

            val thread = Thread({
                Log.i(TAG, "DebugLogServer running on port $port")
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        clientPool.submit { handleClient(client) }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }, "DebugLogServer-Acceptor")
            thread.isDaemon = true
            thread.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting DebugLogServer", e)
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val uri = parts[1]

            val path = uri.substringBefore("?")
            val query = uri.substringAfter("?", "")

            when {
                path == "/ping" -> {
                    sendResponse(writer, 200, "text/plain", "pong\n")
                }
                path == "/status" || path == "/" -> {
                    val statusJson = """
                        {
                            "status": "online",
                            "app": "Dismod",
                            "version": "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            "screen": "${AppLogger.currentScreen}",
                            "sessionId": "${AppLogger.sessionId}",
                            "port": $activePort,
                            "ip": "${getLocalIpAddress() ?: "unknown"}",
                            "device": "${Build.MANUFACTURER} ${Build.MODEL}",
                            "totalLogSize": "${AppLogger.getTotalLogSizeFormatted()}",
                            "lastCloudLogUrl": "${CloudLogStorage.lastCloudLogUrl}",
                            "webhookConfigured": ${CloudLogStorage.webhookUrl.isNotBlank()}
                        }
                    """.trimIndent()
                    sendResponse(writer, 200, "application/json", statusJson)
                }
                path == "/logs/cloud" -> {
                    val existing = CloudLogStorage.lastCloudLogUrl
                    if (existing.isNotBlank()) {
                        sendResponse(writer, 200, "application/json", """{"url":"$existing"}""")
                    } else {
                        val result = kotlinx.coroutines.runBlocking {
                            CloudLogStorage.uploadLogsToCloud()
                        }
                        val url = result.getOrNull() ?: ""
                        sendResponse(writer, 200, "application/json", """{"url":"$url"}""")
                    }
                }
                path == "/logs" || path == "/logs/ai" -> {
                    val limit = query.substringAfter("limit=", "200").toIntOrNull() ?: 200
                    val formatted = AppLogger.getLogsFormattedForAi(limit, errorsOnly = false)
                    sendResponse(writer, 200, "text/plain; charset=utf-8", formatted)
                }
                path == "/logs/errors" -> {
                    val limit = query.substringAfter("limit=", "200").toIntOrNull() ?: 200
                    val formatted = AppLogger.getLogsFormattedForAi(limit, errorsOnly = true)
                    sendResponse(writer, 200, "text/plain; charset=utf-8", formatted)
                }
                path == "/logs/raw" -> {
                    val raw = AppLogger.readAllLogLines().joinToString("\n")
                    sendResponse(writer, 200, "text/plain; charset=utf-8", raw)
                }
                path == "/logs/clear" && (method == "POST" || method == "GET" || method == "DELETE") -> {
                    AppLogger.clearLogs()
                    sendResponse(writer, 200, "application/json", "{\"cleared\":true}")
                }
                else -> {
                    sendResponse(writer, 404, "text/plain", "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendResponse(writer: PrintWriter, statusCode: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode OK\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        writer.print(body)
        writer.flush()
    }
}
