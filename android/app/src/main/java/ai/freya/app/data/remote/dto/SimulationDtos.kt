package ai.freya.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CreateSimulationRequest(
    @SerialName("project_id") val projectId: String,
    @SerialName("graph_id") val graphId: String? = null,
    @SerialName("enable_twitter") val enableTwitter: Boolean = true,
    @SerialName("enable_reddit") val enableReddit: Boolean = true,
)

@Serializable
data class SimulationDto(
    @SerialName("simulation_id") val simulationId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("graph_id") val graphId: String? = null,
    val status: String? = null,
    @SerialName("enable_twitter") val enableTwitter: Boolean = true,
    @SerialName("enable_reddit") val enableReddit: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("entities_count") val entitiesCount: Int? = null,
    @SerialName("entity_types") val entityTypes: List<String> = emptyList(),
    val error: String? = null,
    // Enrichment fields present on /history and /{id} responses.
    @SerialName("simulation_requirement") val simulationRequirement: String? = null,
    @SerialName("current_round") val currentRound: Int? = null,
    @SerialName("total_rounds") val totalRounds: Int? = null,
    @SerialName("runner_status") val runnerStatus: String? = null,
    @SerialName("report_id") val reportId: String? = null,
)

@Serializable
data class PrepareSimulationRequest(
    @SerialName("simulation_id") val simulationId: String,
    @SerialName("entity_types") val entityTypes: List<String>? = null,
    @SerialName("use_llm_for_profiles") val useLlmForProfiles: Boolean = true,
    @SerialName("parallel_profile_count") val parallelProfileCount: Int? = null,
    @SerialName("force_regenerate") val forceRegenerate: Boolean = false,
)

@Serializable
data class PrepareSimulationResponseDto(
    @SerialName("simulation_id") val simulationId: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val message: String? = null,
    @SerialName("already_prepared") val alreadyPrepared: Boolean = false,
    @SerialName("prepare_info") val prepareInfo: JsonElement? = null,
    @SerialName("expected_entities_count") val expectedEntitiesCount: Int? = null,
    @SerialName("entity_types") val entityTypes: List<String> = emptyList(),
    val error: String? = null,
)

@Serializable
data class PrepareStatusDto(
    @SerialName("simulation_id") val simulationId: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val message: String? = null,
    @SerialName("already_prepared") val alreadyPrepared: Boolean? = null,
    @SerialName("prepare_info") val prepareInfo: JsonElement? = null,
    @SerialName("expected_entities_count") val expectedEntitiesCount: Int? = null,
    @SerialName("entity_types") val entityTypes: List<String> = emptyList(),
    val error: String? = null,
    val result: JsonElement? = null,
)

@Serializable
data class PrepareStatusRequest(
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("simulation_id") val simulationId: String? = null,
)

@Serializable
data class StartSimulationRequest(
    @SerialName("simulation_id") val simulationId: String,
    val platform: String = "parallel",
    @SerialName("max_rounds") val maxRounds: Int? = null,
    @SerialName("enable_graph_memory_update") val enableGraphMemoryUpdate: Boolean = false,
    val force: Boolean = false,
)

@Serializable
data class StopSimulationRequest(
    @SerialName("simulation_id") val simulationId: String,
)

@Serializable
data class RunStatusDto(
    @SerialName("simulation_id") val simulationId: String? = null,
    @SerialName("runner_status") val runnerStatus: String? = null,
    @SerialName("current_round") val currentRound: Int? = null,
    @SerialName("total_rounds") val totalRounds: Int? = null,
    @SerialName("progress_percent") val progressPercent: Double? = null,
    @SerialName("twitter_actions_count") val twitterActionsCount: Int? = null,
    @SerialName("reddit_actions_count") val redditActionsCount: Int? = null,
    @SerialName("total_actions_count") val totalActionsCount: Int? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("simulated_hours") val simulatedHours: Double? = null,
    @SerialName("total_simulation_hours") val totalSimulationHours: Double? = null,
    @SerialName("twitter_running") val twitterRunning: Boolean? = null,
    @SerialName("reddit_running") val redditRunning: Boolean? = null,
)

@Serializable
data class SimulationProfilesDto(
    val platform: String? = null,
    val count: Int? = null,
    val profiles: JsonElement? = null,
    @SerialName("total_expected") val totalExpected: Int? = null,
    @SerialName("is_generating") val isGenerating: Boolean? = null,
)
