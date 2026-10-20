package ai.freya.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Masked per-provider status (never echoes secret values). */
@Serializable
data class KeysStatusDto(
    val deepseek: KeysNestedStatusDto = KeysNestedStatusDto(),
    val llm: LlmStatusDto = LlmStatusDto(),
    val zep: KeysNestedStatusDto = KeysNestedStatusDto(),
) {
    val anySet: Boolean
        get() = deepseek.set || llm.set || zep.set
}

@Serializable
data class KeysNestedStatusDto(
    val set: Boolean = false,
)

@Serializable
data class LlmStatusDto(
    val set: Boolean = false,
    @SerialName("base_url") val baseUrl: String? = null,
    val model: String? = null,
)

@Serializable
data class KeysUpdateRequest(
    @SerialName("deepseek_api_key") val deepseekApiKey: String? = null,
    @SerialName("llm_api_key") val llmApiKey: String? = null,
    @SerialName("llm_base_url") val llmBaseUrl: String? = null,
    @SerialName("llm_model_name") val llmModelName: String? = null,
    @SerialName("zep_api_key") val zepApiKey: String? = null,
)
