package ai.freya.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProjectFileDto(
    val filename: String? = null,
    val size: Int = 0,
    val path: String? = null,
)

@Serializable
data class ProjectDto(
    @SerialName("project_id") val projectId: String? = null,
    val name: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val files: List<ProjectFileDto> = emptyList(),
    @SerialName("total_text_length") val totalTextLength: Int = 0,
    val ontology: JsonElement? = null,
    @SerialName("analysis_summary") val analysisSummary: String? = null,
    @SerialName("graph_id") val graphId: String? = null,
    @SerialName("graph_build_task_id") val graphBuildTaskId: String? = null,
    @SerialName("simulation_requirement") val simulationRequirement: String? = null,
    @SerialName("chunk_size") val chunkSize: Int = 500,
    @SerialName("chunk_overlap") val chunkOverlap: Int = 50,
    val error: String? = null,
)

@Serializable
data class OntologyGenerateResponseDto(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("project_name") val projectName: String? = null,
    val ontology: JsonElement? = null,
    @SerialName("analysis_summary") val analysisSummary: String? = null,
    val files: List<ProjectFileDto> = emptyList(),
    @SerialName("total_text_length") val totalTextLength: Int = 0,
)

@Serializable
data class BuildGraphRequest(
    @SerialName("project_id") val projectId: String,
    @SerialName("graph_name") val graphName: String? = null,
    @SerialName("chunk_size") val chunkSize: Int? = null,
    @SerialName("chunk_overlap") val chunkOverlap: Int? = null,
)

@Serializable
data class BuildGraphResponseDto(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("graph_id") val graphId: String? = null,
    val resumed: Boolean? = null,
    val reused: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class TaskDto(
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("task_type") val taskType: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val progress: Int? = null,
    val message: String? = null,
    @SerialName("progress_detail") val progressDetail: JsonElement? = null,
    val result: JsonElement? = null,
    val error: String? = null,
    val metadata: JsonElement? = null,
)
