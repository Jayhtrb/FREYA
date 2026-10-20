package ai.freya.app.domain.model

data class ChatMessage(
    val role: String,
    val content: String,
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
