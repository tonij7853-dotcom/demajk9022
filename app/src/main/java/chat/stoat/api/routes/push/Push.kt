package chat.stoat.api.routes.push

import chat.stoat.api.StoatHttp
import chat.stoat.api.api
import chat.stoat.api.routes.account.WebPushData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

suspend fun subscribePush(
    endpoint: String = "fcm",
    auth: String,
    p256diffieHellman: String? = null,
): Boolean {
    return try {
        val data = WebPushData(
            endpoint = endpoint,
            p256diffieHellman = p256diffieHellman ?: "",
            auth = auth
        )

        val response: HttpResponse = StoatHttp.post("/push/subscribe".api()) {
            setBody(data)
            contentType(ContentType.Application.Json)
        }
        val isSuccess = response.status.value in 200..299
        logcat("Push", if (isSuccess) LogPriority.INFO else LogPriority.WARN) {
            "subscribePush endpoint=$endpoint status=${response.status.value}"
        }
        isSuccess
    } catch (e: Exception) {
        logcat("Push", LogPriority.ERROR) { "subscribePush failed for endpoint $endpoint: ${e.asLog()}" }
        false
    }
}

suspend fun unsubscribePush(): Boolean {
    return try {
        val response: HttpResponse = StoatHttp.post("/push/unsubscribe".api())
        val isSuccess = response.status.value in 200..299
        logcat("Push", if (isSuccess) LogPriority.INFO else LogPriority.WARN) {
            "unsubscribePush status=${response.status.value}"
        }
        isSuccess
    } catch (e: Exception) {
        logcat("Push", LogPriority.ERROR) { "unsubscribePush failed: ${e.asLog()}" }
        false
    }
}