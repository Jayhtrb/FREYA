package ai.freya.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
)
