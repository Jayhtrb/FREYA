package ai.freya.app.data.repository

import ai.freya.app.data.remote.ApiErrors
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.FreyaJson
import ai.freya.app.data.remote.dto.MemoryDto
import ai.freya.app.data.remote.dto.PreferenceUpsertRequest
import ai.freya.app.data.remote.requireData
import ai.freya.app.data.remote.requireSuccess
import ai.freya.app.domain.model.MemoryPreference
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class MemoryRepository(private val api: FreyaApi) {

    suspend fun memory(): MemoryDto =
        ApiErrors.safe { api.memory() }.requireData()

    suspend fun preferences(): List<MemoryPreference> {
        val memory = memory()
        return memory.preferences.toPreferences()
    }

    suspend fun deletePreference(key: String) {
        ApiErrors.safe { api.deletePreference(key) }.requireSuccess()
    }

    suspend fun upsertPreference(key: String, value: String) {
        val jsonValue = runCatching { FreyaJson.json.parseToJsonElement(value) }
            .getOrElse { JsonPrimitive(value) }
        ApiErrors.safe { api.upsertPreference(PreferenceUpsertRequest(key, jsonValue)) }.requireSuccess()
    }
}

private fun kotlinx.serialization.json.JsonElement?.toPreferences(): List<MemoryPreference> {
    val obj = this as? JsonObject ?: return emptyList()
    return obj.entries.map { (key, value) ->
        val rendered = when (value) {
            is JsonPrimitive -> value.contentOrNull ?: value.toString()
            else -> value.toString()
        }
        MemoryPreference(key, rendered)
    }
}
