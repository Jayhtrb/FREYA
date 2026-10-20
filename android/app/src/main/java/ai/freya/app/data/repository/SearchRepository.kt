package ai.freya.app.data.repository

import ai.freya.app.data.remote.ApiErrors
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.FreyaException
import ai.freya.app.data.remote.FreyaJson
import ai.freya.app.data.remote.dto.SearchHitDto
import ai.freya.app.data.remote.dto.SearchResultWrapperDto
import ai.freya.app.domain.model.SearchHit
import kotlinx.serialization.json.decodeFromJsonElement

class SearchRepository(private val api: FreyaApi) {

    suspend fun search(query: String, scope: String): List<SearchHit> {
        val env = ApiErrors.safe { api.search(query, scope) }
        if (!env.success) throw FreyaException(env.error ?: "Search failed")
        val data = env.data ?: return emptyList()
        val hits = try {
            FreyaJson.json.decodeFromJsonElement<List<SearchHitDto>>(data)
        } catch (_: Exception) {
            FreyaJson.json.decodeFromJsonElement<SearchResultWrapperDto>(data).results
        }
        return hits.mapNotNull { hit ->
            val id = hit.id ?: return@mapNotNull null
            SearchHit(
                type = hit.type ?: "unknown",
                id = id,
                title = hit.title ?: id,
                snippet = hit.snippet ?: "",
                updatedAt = hit.updatedAt,
            )
        }
    }
}
