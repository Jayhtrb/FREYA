package ai.freya.app.data.repository

import ai.freya.app.data.local.db.dao.ConversationDao
import ai.freya.app.data.local.db.entity.ConversationEntity
import ai.freya.app.data.local.db.entity.MessageEntity
import ai.freya.app.data.remote.ApiErrors
import ai.freya.app.data.remote.ChatSseClient
import ai.freya.app.data.remote.ChatStreamEvent
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.dto.ChatRequest
import ai.freya.app.data.remote.dto.ChatResponseDto
import ai.freya.app.data.remote.dto.ConversationDto
import ai.freya.app.data.remote.dto.ConversationListDto
import ai.freya.app.data.remote.dto.StreamChatRequest
import ai.freya.app.data.remote.requireData
import ai.freya.app.data.remote.requireSuccess
import ai.freya.app.util.parseIsoToMillis
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val api: FreyaApi,
    private val sseClient: ChatSseClient,
    private val conversationDao: ConversationDao,
) {

    fun observeCachedConversations(): Flow<List<ConversationEntity>> =
        conversationDao.observeConversations()

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        conversationDao.observeMessages(conversationId)

    /** Non-streaming fallback. */
    suspend fun sendChat(message: String, conversationId: String?): ChatResponseDto {
        val response = ApiErrors.safe {
            api.chat(ChatRequest(message, conversationId, stream = false))
        }.requireData<ChatResponseDto>()
        cacheConversation(response.conversationId, response.replyText())
        return response
    }

    fun streamChat(message: String, conversationId: String?): Flow<ChatStreamEvent> =
        sseClient.stream(StreamChatRequest(message, conversationId))

    suspend fun loadConversations(): List<ConversationDto> {
        val list = ApiErrors.safe { api.conversations() }.requireData<ConversationListDto>()
        list.conversations.forEach { conv ->
            val id = conv.conversationId ?: conv.id ?: return@forEach
            conversationDao.upsert(
                ConversationEntity(
                    conversationId = id,
                    title = conv.title,
                    updatedAt = parseIsoToMillis(conv.updatedAt ?: conv.createdAt),
                    preview = conv.lastMessage,
                )
            )
        }
        return list.conversations
    }

    suspend fun deleteConversation(conversationId: String) {
        ApiErrors.safe { api.deleteConversation(conversationId) }.requireSuccess()
        conversationDao.deleteMessages(conversationId)
        conversationDao.deleteConversation(conversationId)
    }

    suspend fun cacheConversation(conversationId: String?, preview: String?) {
        if (conversationId.isNullOrBlank()) return
        conversationDao.upsert(
            ConversationEntity(
                conversationId = conversationId,
                title = null,
                updatedAt = System.currentTimeMillis(),
                preview = preview,
            )
        )
    }

    suspend fun cacheMessage(conversationId: String?, role: String, content: String) {
        if (conversationId.isNullOrBlank()) return
        conversationDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                timestamp = System.currentTimeMillis(),
            )
        )
    }
}

/** Extracts human-readable reply text from a possibly-string-or-object reply field. */
fun ChatResponseDto.replyText(): String? {
    val reply = this.reply ?: return null
    return when (reply) {
        is kotlinx.serialization.json.JsonPrimitive -> reply.content
        is kotlinx.serialization.json.JsonObject ->
            (reply["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: (reply["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: (reply["reply"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        else -> reply.toString()
    }
}
