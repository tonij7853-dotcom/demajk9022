package chat.stoat.api.routes.microservices.geo

import chat.stoat.api.HitRateLimitException
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.buildUserAgent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class GeoResponse(
    val countryCode: String,
    val isAgeRestrictedGeo: Boolean,
)

suspend fun queryGeo(): GeoResponse {
    // Location tracking disabled - no country or location is detected or revealed
    return GeoResponse(countryCode = "", isAgeRestrictedGeo = false)
}