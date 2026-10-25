package ai.freya.app.data.remote

import ai.freya.app.data.remote.dto.StreamChatRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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
    data class Done(val conversationId: String?, val model: String? = null) : ChatStreamEvent
    data class Error(val message: String) : ChatStreamEvent

    /**
     * Forward-compatible tool activity. The current backend (`/api/freya/chat/stream`) emits
     * only delta/done/error; when it starts emitting `tool_start` / `tool_result`, the Core
     * switches to TOOL EXECUTION automatically. Nothing in the app fabricates these.
     */
    data class ToolStarted(val name: String) : ChatStreamEvent
    data class ToolFinished(val name: String, val ok: Boolean) : ChatStreamEvent
}

/**
 * Streams the assistant reply from POST /api/freya/chat/stream using OkHttp SSE.
 * Parsing is tolerant so plain-text `data:` payloads still work.
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
                val event = SseParser.parse(type, data) ?: return
                trySend(event)
                if (event is ChatStreamEvent.Done || event is ChatStreamEvent.Error) close()
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val message = when (response?.code) {
                    401 -> "Session expired. Please sign in again."
                    429 -> "Rate limit reached. Wait a moment and try again."
                    null -> "Can't reach FREYA's backend: ${t?.message ?: "connection failed"}"
                    else -> "Stream interrupted (HTTP ${response.code})"
                }
                trySend(ChatStreamEvent.Error(message))
                close()
            }
        }

        val eventSource = factory.newEventSource(httpRequest, listener)
        awaitClose { eventSource.cancel() }
    }
}

/** Pure SSE payload parser (unit-tested). */
object SseParser {
    fun parse(type: String?, data: String): ChatStreamEvent? {
        val text = data.trim()
        // Deltas keep whitespace exactly (a delta may be just " " or "\n").
        val obj = runCatching { FreyaJson.json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
            (obj?.get(k) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
        return when (type) {
            "error" -> ChatStreamEvent.Error(str("error", "message") ?: text.ifBlank { "Unknown error" })
            "done" -> ChatStreamEvent.Done(conversationId = str("conversation_id", "id"), model = str("model"))
            "tool_start", "tool_call" -> ChatStreamEvent.ToolStarted(str("tool", "name") ?: "tool")
            "tool_result", "tool_end" -> ChatStreamEvent.ToolFinished(
                name = str("tool", "name") ?: "tool",
                ok = (obj?.get("ok") as? JsonPrimitive)?.booleanOrNull ?: (obj?.get("error") == null),
            )
            else -> {
                if (obj != null) {
                    val raw = listOf("delta", "content", "text").firstNotNullOfOrNull { k ->
                        (obj[k] as? JsonPrimitive)?.contentOrNull
                    }
                    raw?.takeIf { it.isNotEmpty() }?.let { ChatStreamEvent.Delta(it) }
                } else {
                    data.takeIf { it.isNotEmpty() }?.let { ChatStreamEvent.Delta(it) }
                }
            }
        }
    }
}
