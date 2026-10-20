package ai.freya.app.data.remote

import ai.freya.app.data.remote.dto.StreamChatRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

sealed interface ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent
    data class Done(val conversationId: String?) : ChatStreamEvent
    data class Error(val message: String) : ChatStreamEvent
}

/**
 * Streams the assistant reply from POST /api/freya/chat/stream using OkHttp SSE.
 * The backend emits `delta`, `done`, and `error` events; parsing is tolerant so
 * plain-text `data:` payloads still work.
 */
class ChatSseClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String,
) {

    private val factory: EventSource.Factory by lazy { EventSources.createFactory(okHttpClient) }

    fun stream(request: StreamChatRequest): Flow<ChatStreamEvent> = callbackFlow {
        val body = FreyaJson.json.encodeToString(StreamChatRequest.serializer(), request)
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/freya/chat/stream")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                when (type) {
                    "error" -> {
                        trySend(ChatStreamEvent.Error(extractText(data, "error")))
                        close()
                    }
                    "done" -> {
                        trySend(ChatStreamEvent.Done(extractId(data)))
                        close()
                    }
                    else -> {
                        extractDelta(data)?.let { trySend(ChatStreamEvent.Delta(it)) }
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val message = when (response?.code) {
                    401 -> "Session expired. Please sign in again."
                    else -> "Stream interrupted: ${t?.message ?: "unknown error"}"
                }
                trySend(ChatStreamEvent.Error(message))
                close()
            }
        }

        val eventSource = factory.newEventSource(httpRequest, listener)
        awaitClose { eventSource.cancel() }
    }

    private fun extractDelta(data: String): String? {
        val text = data.trim()
        if (text.isEmpty()) return null
        val parsed = runCatching { FreyaJson.json.parseToJsonElement(text) }.getOrNull()
        return when (parsed) {
            is JsonObject ->
                parsed["delta"]?.jsonPrimitive?.contentOrNull
                    ?: parsed["content"]?.jsonPrimitive?.contentOrNull
                    ?: parsed["text"]?.jsonPrimitive?.contentOrNull
            else -> text
        }
    }

    private fun extractText(data: String, key: String): String {
        val text = data.trim()
        val parsed = runCatching { FreyaJson.json.parseToJsonElement(text) }.getOrNull()
        return if (parsed is JsonObject) {
            parsed[key]?.jsonPrimitive?.contentOrNull
                ?: parsed["message"]?.jsonPrimitive?.contentOrNull
                ?: text
        } else text
    }

    private fun extractId(data: String): String? {
        val text = data.trim()
        val parsed = runCatching { FreyaJson.json.parseToJsonElement(text) }.getOrNull()
        return if (parsed is JsonObject) {
            parsed["conversation_id"]?.jsonPrimitive?.contentOrNull
                ?: parsed["id"]?.jsonPrimitive?.contentOrNull
        } else null
    }
}
