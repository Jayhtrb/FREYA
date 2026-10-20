package ai.freya.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---- Memory & preferences ----

@Serializable
data class MemoryDto(
    val preferences: JsonElement? = null,
    @SerialName("project_context") val projectContext: JsonElement? = null,
)

@Serializable
data class PreferenceUpsertRequest(
    val key: String,
    val value: JsonElement,
)

// ---- Settings ----

@Serializable
data class SettingsDto(
    val personality: String? = null,
    val model: String? = null,
    val voice: String? = null,
)

@Serializable
data class SettingsUpdateRequest(
    val personality: String? = null,
    val model: String? = null,
    val voice: String? = null,
)

// ---- Search ----

@Serializable
data class SearchHitDto(
    val type: String? = null,
    val id: String? = null,
    val title: String? = null,
    val snippet: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SearchResultWrapperDto(
    val results: List<SearchHitDto> = emptyList(),
)

// ---- Notifications & jobs ----

@Serializable
data class NotificationDto(
    val id: String? = null,
    val title: String? = null,
    val body: String? = null,
    val read: Boolean = false,
    val type: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class NotificationsDto(
    val notifications: List<NotificationDto> = emptyList(),
)

@Serializable
data class JobDto(
    val id: String? = null,
    @SerialName("job_id") val jobId: String? = null,
    val type: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val message: String? = null,
    val result: JsonElement? = null,
    val error: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class JobsDto(
    val jobs: List<JobDto> = emptyList(),
)

// ---- Tools ----

@Serializable
data class ToolExecuteRequest(
    val tool: String,
    val args: JsonElement = kotlinx.serialization.json.JsonObject(emptyMap()),
)

@Serializable
data class ToolDto(
    val name: String? = null,
    val description: String? = null,
    @SerialName("input_schema") val inputSchema: JsonElement? = null,
)

@Serializable
data class ToolsListDto(
    val tools: List<ToolDto> = emptyList(),
)

// ---- Persona (read-only display data; matches backend Persona.public_view()) ----

@Serializable
data class PersonaDto(
    val name: String? = null,
    val acronym: String? = null,
    val tagline: String? = null,
    val role: String? = null,
    @SerialName("voice_presentation") val voicePresentation: String? = null,
    val greeting: PersonaGreetingDto? = null,
    val voice: PersonaVoiceDto? = null,
)

@Serializable
data class PersonaGreetingDto(
    val title: String? = null,
    @SerialName("body_template") val bodyTemplate: String? = null,
    @SerialName("time_greetings") val timeGreetings: Map<String, String> = emptyMap(),
    @SerialName("time_boundaries_hour") val timeBoundariesHour: Map<String, Int> = emptyMap(),
    @SerialName("display_policy") val displayPolicy: String? = null,
)

@Serializable
data class PersonaVoiceDto(
    val provider: String? = null,
    @SerialName("default_android") val defaultAndroid: String? = null,
    val states: List<String> = emptyList(),
)
