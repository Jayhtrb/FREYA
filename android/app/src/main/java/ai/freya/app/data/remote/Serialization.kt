package ai.freya.app.data.remote

import kotlinx.serialization.json.Json

/**
 * Single shared [Json] configuration used for both Retrofit request/response
 * conversion and manual [ApiEnvelope] payload decoding.
 */
object FreyaJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }
}
