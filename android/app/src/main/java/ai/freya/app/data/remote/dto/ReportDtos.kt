package ai.freya.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class InterviewRequest(
    @SerialName("simulation_id") val simulationId: String,
    @SerialName("agent_id") val agentId: Int,
    val prompt: String,
    val platform: String? = null,
    val timeout: Int? = null,
)

@Serializable
data class InterviewBatchRequest(
    @SerialName("simulation_id") val simulationId: String,
    val interviews: List<InterviewItemDto>,
    val platform: String? = null,
    val timeout: Int? = null,
)

@Serializable
data class InterviewItemDto(
    @SerialName("agent_id") val agentId: Int,
    val prompt: String,
    val platform: String? = null,
)

@Serializable
data class InterviewAllRequest(
    @SerialName("simulation_id") val simulationId: String,
    val prompt: String,
    val platform: String? = null,
    val timeout: Int? = null,
)

@Serializable
data class InterviewHistoryRequest(
    @SerialName("simulation_id") val simulationId: String,
    val platform: String? = null,
    @SerialName("agent_id") val agentId: Int? = null,
    val limit: Int? = null,
)

// ---- Reports ----

@Serializable
data class GenerateReportRequest(
    @SerialName("simulation_id") val simulationId: String,
    @SerialName("force_regenerate") val forceRegenerate: Boolean = false,
)

@Serializable
data class GenerateReportResponseDto(
    @SerialName("simulation_id") val simulationId: String? = null,
    @SerialName("report_id") val reportId: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    val status: String? = null,
    val message: String? = null,
    @SerialName("already_generated") val alreadyGenerated: Boolean = false,
)

@Serializable
data class ReportStatusRequest(
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("simulation_id") val simulationId: String? = null,
)

@Serializable
data class ReportStatusDto(
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("simulation_id") val simulationId: String? = null,
    @SerialName("report_id") val reportId: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val message: String? = null,
    @SerialName("already_completed") val alreadyCompleted: Boolean? = null,
    val error: String? = null,
    val result: JsonElement? = null,
)

@Serializable
data class ReportDto(
    @SerialName("report_id") val reportId: String? = null,
    @SerialName("simulation_id") val simulationId: String? = null,
    val status: String? = null,
    val title: String? = null,
    val outline: JsonElement? = null,
    @SerialName("markdown_content") val markdownContent: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class ReportChatRequest(
    @SerialName("simulation_id") val simulationId: String,
    val message: String,
    @SerialName("chat_history") val chatHistory: List<ReportChatMessageDto> = emptyList(),
)

@Serializable
data class ReportChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ReportChatResponseDto(
    val response: String? = null,
    @SerialName("tool_calls") val toolCalls: JsonElement? = null,
    val sources: JsonElement? = null,
)

@Serializable
data class ReportCheckDto(
    @SerialName("simulation_id") val simulationId: String? = null,
    @SerialName("has_report") val hasReport: Boolean = false,
    @SerialName("report_status") val reportStatus: String? = null,
    @SerialName("report_id") val reportId: String? = null,
    @SerialName("interview_unlocked") val interviewUnlocked: Boolean = false,
)
