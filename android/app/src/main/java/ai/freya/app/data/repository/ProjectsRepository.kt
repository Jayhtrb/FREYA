package ai.freya.app.data.repository

import ai.freya.app.data.local.db.dao.ProjectDao
import ai.freya.app.data.local.db.entity.ProjectEntity
import ai.freya.app.data.remote.ApiErrors
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.dto.BuildGraphRequest
import ai.freya.app.data.remote.dto.BuildGraphResponseDto
import ai.freya.app.data.remote.dto.OntologyGenerateResponseDto
import ai.freya.app.data.remote.dto.ProjectDto
import ai.freya.app.data.remote.dto.TaskDto
import ai.freya.app.data.remote.requireData
import ai.freya.app.data.remote.requireSuccess
import ai.freya.app.domain.model.UploadFile
import ai.freya.app.util.parseIsoToMillis
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ProjectsRepository(
    private val api: FreyaApi,
    private val projectDao: ProjectDao,
) {

    fun observeCachedProjects(): Flow<List<ProjectEntity>> = projectDao.observeProjects()

    suspend fun listProjects(): List<ProjectDto> {
        val projects = ApiErrors.safe { api.listProjects(limit = 100) }.requireData<List<ProjectDto>>()
        projects.forEach { project ->
            val id = project.projectId ?: return@forEach
            projectDao.upsert(project.toEntity())
        }
        return projects
    }

    suspend fun getProject(id: String): ProjectDto =
        ApiErrors.safe { api.project(id) }.requireData()

    suspend fun generateOntology(
        files: List<UploadFile>,
        simulationRequirement: String,
        projectName: String,
        additionalContext: String,
    ): OntologyGenerateResponseDto {
        val parts = files.map { file ->
            val mediaType = file.mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
            MultipartBody.Part.createFormData("files", file.name, file.bytes.toRequestBody(mediaType))
        }
        val envelope = ApiErrors.safe {
            api.generateOntology(
                files = parts,
                simulationRequirement = simulationRequirement.toRequestBody("text/plain".toMediaType()),
                projectName = projectName.toRequestBody("text/plain".toMediaType()),
                additionalContext = additionalContext.toRequestBody("text/plain".toMediaType()),
            )
        }
        return envelope.requireData()
    }

    suspend fun buildGraph(projectId: String, graphName: String? = null): BuildGraphResponseDto =
        ApiErrors.safe { api.buildGraph(BuildGraphRequest(projectId, graphName)) }.requireData()

    suspend fun task(taskId: String): TaskDto =
        ApiErrors.safe { api.task(taskId) }.requireData()

    suspend fun deleteProject(id: String) {
        ApiErrors.safe { api.deleteProject(id) }.requireSuccess()
        projectDao.delete(id)
    }
}

private fun ProjectDto.toEntity(): ProjectEntity = ProjectEntity(
    projectId = projectId ?: "",
    name = name ?: "Unnamed Project",
    status = status,
    createdAt = parseIsoToMillis(createdAt),
    updatedAt = parseIsoToMillis(updatedAt),
    graphId = graphId,
    cachedAt = System.currentTimeMillis(),
)
