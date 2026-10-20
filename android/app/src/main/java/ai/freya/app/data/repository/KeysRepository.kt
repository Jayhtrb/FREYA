package ai.freya.app.data.repository

import ai.freya.app.data.remote.ApiErrors
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.dto.KeysStatusDto
import ai.freya.app.data.remote.dto.KeysUpdateRequest
import ai.freya.app.data.remote.requireData
import ai.freya.app.data.remote.requireSuccess

class KeysRepository(private val api: FreyaApi) {

    suspend fun status(): KeysStatusDto =
        ApiErrors.safe { api.keysStatus() }.requireData()

    suspend fun save(
        deepseek: String?,
        llm: String?,
        llmBaseUrl: String?,
        llmModel: String?,
        zep: String?,
    ): KeysStatusDto =
        ApiErrors.safe {
            api.updateKeys(
                KeysUpdateRequest(
                    deepseekApiKey = deepseek,
                    llmApiKey = llm,
                    llmBaseUrl = llmBaseUrl,
                    llmModelName = llmModel,
                    zepApiKey = zep,
                )
            )
        }.requireData()

    suspend fun clear() {
        ApiErrors.safe { api.clearKeys() }.requireSuccess()
    }
}
