package ai.freya.app.domain.model

import java.util.concurrent.atomic.AtomicLong

private val ids = AtomicLong(1)

enum class MessageStatus { STREAMING, DONE, STOPPED, FAILED }

enum class MessageKind { TEXT, TOOL_RESULT }

/** Measured facts about one assistant turn, shown under "Technical details". */
data class TurnMeta(
    val model: String? = null,
    val conversationId: String? = null,
    val transport: String = "stream",
    val firstTokenMs: Long? = null,
    val totalMs: Long? = null,
    val chars: Int = 0,
    val toolEvents: List<String> = emptyList(),
)

data class ChatMessage(
    val role: String,
    val content: String,
    val id: Long = ids.getAndIncrement(),
    val status: MessageStatus = MessageStatus.DONE,
    val kind: MessageKind = MessageKind.TEXT,
    val meta: TurnMeta? = null,
    val toolName: String? = null,
    /** +1 helpful, -1 not helpful, 0 none. Stored on device only. */
    val feedback: Int = 0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isUser: Boolean get() = role == "user"
}

data class UserProfile(
    val userId: String,
    val email: String?,
    val displayName: String?,
)

data class SearchHit(
    val type: String,
    val id: String,
    val title: String,
    val snippet: String,
    val updatedAt: String?,
)

data class MemoryPreference(
    val key: String,
    val value: String,
)

data class UploadFile(
    val name: String,
    val bytes: ByteArray,
    val mimeType: String,
)

data class JobItem(
    val id: String,
    val status: String,
    val progress: Int,
    val message: String,
    val error: String?,
)

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: String?,
)
