package ai.freya.app.data.local.db.dao

import ai.freya.app.data.local.db.entity.ConversationEntity
import ai.freya.app.data.local.db.entity.MessageEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE conversation_id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE conversation_id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clear()

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversation_id = :id ORDER BY timestamp ASC")
    fun observeMessages(id: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE conversation_id = :id")
    suspend fun deleteMessages(id: String)
}
