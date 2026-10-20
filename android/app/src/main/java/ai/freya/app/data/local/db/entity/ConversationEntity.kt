package ai.freya.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val title: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val preview: String?,
)
