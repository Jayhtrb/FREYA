package ai.freya.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey @ColumnInfo(name = "project_id") val projectId: String,
    val name: String,
    val status: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "graph_id") val graphId: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
)
