package ai.freya.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatRequest(
    val message: String,
    @SerialName("conversation_id") val conversationId: String? = null,
    val stream: Boolean = false,
)

@Serializable
data class StreamChatRequest(
    val message: String,
    @SerialName("conversation_id") val conversationId: String? = null,
)

@Serializable
data class ChatResponseDto(
    @SerialName("conversation_id") val conversationId: String? = null,
    val reply: JsonElement? = null,
    val model: String? = null,
    val finished: Boolean = false,
)

@Serializable
data class ConversationDto(
    @SerialName("conversation_id") val conversationId: String? = null,
    val id: String? = null,
    val title: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("message_count") val messageCount: Int? = null,
)

@Serializable
data class ConversationListDto(
    val conversations: List<ConversationDto> = emptyList(),
)

@Serializable
data class ChatMessageDto(
    val role: String? = null,
    val content: String? = null,
    val timestamp: String? = null,
)

@Serializable
data class ConversationDetailDto(
    val conversation: ConversationWithMessagesDto? = null,
)

@Serializable
data class ConversationWithMessagesDto(
    @SerialName("conversation_id") val conversationId: String? = null,
    val id: String? = null,
    val title: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val messages: List<ChatMessageDto> = emptyList(),
)
