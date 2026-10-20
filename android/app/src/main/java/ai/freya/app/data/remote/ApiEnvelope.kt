package ai.freya.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import retrofit2.HttpException
import java.io.IOException

/**
 * The response envelope used by every non-streaming FREYA/MiroFish endpoint:
 *
 *   { "success": true, "data": {}, "error": null }
 *
 * The shape of `data` varies per endpoint (object, array, or primitive), so it is
 * decoded lazily via [requireData] / [decodeData] in the repositories.
 */
@Serializable
data class ApiEnvelope(
    val success: Boolean = false,
    val data: JsonElement? = null,
    val error: String? = null,
    val count: Int? = null,
    val message: String? = null,
    @SerialName("has_report") val hasReport: Boolean? = null,
    val pending: Boolean? = null,
)

/** Thrown for any user-safe API failure (network, auth, or backend-rejected request). */
class FreyaException(
    message: String,
    val code: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Maps transport/HTTP failures to a user-safe [FreyaException]. */
object ApiErrors {

    suspend fun <T> safe(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val message = runCatching {
            e.response()?.errorBody()?.string()?.let { body ->
                FreyaJson.json.decodeFromString<ApiEnvelope>(body).error
            }
        }.getOrNull()
        throw FreyaException(message ?: "Server error (${e.code()})", e.code(), e)
    } catch (e: IOException) {
        throw FreyaException(
            "Can't reach FREYA. Check your connection and the server address.",
            null,
            e,
        )
    } catch (e: FreyaException) {
        throw e
    } catch (e: Exception) {
        throw FreyaException(e.message ?: "Unexpected error", null, e)
    }
}

/** Throws if the envelope reports `success == false`. */
fun ApiEnvelope.requireSuccess() {
    if (!success) throw FreyaException(error ?: "Request failed")
}

/** Decodes and returns the envelope `data` payload as [T], throwing on failure. */
inline fun <reified T> ApiEnvelope.requireData(): T {
    requireSuccess()
    val payload = data ?: throw FreyaException("Missing response data")
    return FreyaJson.json.decodeFromJsonElement<T>(payload)
}

/** Best-effort decode of the envelope `data` payload as [T] (null when absent). */
inline fun <reified T> ApiEnvelope.decodeData(): T? =
    data?.let { FreyaJson.json.decodeFromJsonElement<T>(it) }
