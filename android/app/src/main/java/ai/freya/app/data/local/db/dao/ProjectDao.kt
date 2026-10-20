package ai.freya.app.data.local.db.dao

import ai.freya.app.data.local.db.entity.ProjectEntity
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE project_id = :id")
    suspend fun getProject(id: String): ProjectEntity?

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE project_id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM projects")
    suspend fun clear()
}
