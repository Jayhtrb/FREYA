package ai.freya.app.data.local.db

import ai.freya.app.data.local.db.dao.ConversationDao
import ai.freya.app.data.local.db.dao.ProjectDao
import ai.freya.app.data.local.db.entity.ConversationEntity
import ai.freya.app.data.local.db.entity.MessageEntity
import ai.freya.app.data.local.db.entity.ProjectEntity
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, ProjectEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FreyaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun projectDao(): ProjectDao
}
