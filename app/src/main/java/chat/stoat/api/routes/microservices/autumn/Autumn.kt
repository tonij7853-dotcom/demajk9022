package chat.stoat.api.routes.microservices.autumn

import chat.stoat.api.HitRateLimitException
import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.AutumnError
import chat.stoat.core.model.schemas.AutumnId
import chat.stoat.logging.AppLogger
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.File

const val MAX_ATTACHMENTS_PER_MESSAGE = 5

data class FileArgs(
    val file: File,
    val filename: String,
    val contentType: String,
    val spoiler: Boolean = false,
    val pickerIdentifier: String? = null,
)

suspend fun uploadToAutumn(
    file: File,
    name: String,
    tag: String,
    contentType: ContentType,
    onProgress: (Long, Long) -> Unit = { _, _ -> }
): String {
    val uploadUrl = "$STOAT_FILES/$tag"
    val startTime = System.currentTimeMillis()

    AppLogger.i("autumn_upload_started", mapOf(
        "tag" to tag,
        "filename" to name,
        "size_bytes" to file.length(),
        "content_type" to contentType.toString()
    ))

    val response = try {
        StoatHttp.post(uploadUrl) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            file.readBytes(),
                            Headers.build {
                                append(HttpHeaders.ContentType, contentType.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                            }
                        )
                    }
                )
            )
            header(StoatAPI.TOKEN_HEADER_NAME, StoatAPI.sessionToken)
            onUpload { bytesSentTotal, contentLength ->
                contentLength?.let { onProgress(bytesSentTotal, it) }
            }
        }
    } catch (e: Exception) {
        AppLogger.e("autumn_upload_network_failed", mapOf(
            "tag" to tag,
            "filename" to name,
            "size_bytes" to file.length(),
            "error" to (e.message ?: "")
        ), e)
        throw e
    }

    val responseText = response.bodyAsText()
    val durationMs = System.currentTimeMillis() - startTime

    AppLogger.i("autumn_upload_response", mapOf(
        "tag" to tag,
        "status_code" to response.status.value,
        "duration_ms" to durationMs,
        "response_body" to responseText.take(300)
    ))

    try {
        val autumnId = StoatJson.decodeFromString(AutumnId.serializer(), responseText)
        AppLogger.i("autumn_upload_success", mapOf(
            "tag" to tag,
            "autumn_id" to autumnId.id,
            "duration_ms" to durationMs
        ))
        return autumnId.id
    } catch (e: Exception) {
        AppLogger.w("autumn_upload_parse_failed", mapOf(
            "tag" to tag,
            "status_code" to response.status.value,
            "response" to responseText
        ))
        try {
            val error = StoatJson.decodeFromString(AutumnError.serializer(), responseText)
            throw Exception(error.type)
        } catch (e: Exception) {
            if (response.status == HttpStatusCode.TooManyRequests) {
                throw HitRateLimitException()
            }
            if (response.status == HttpStatusCode.PayloadTooLarge) {
                throw Exception("File too large")
            }
            throw Exception("Unknown error: ${response.status.value} $responseText")
        }
    }
}
