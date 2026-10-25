package ai.freya.app.data.repository

import ai.freya.app.data.remote.ApiErrors
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.dto.ToolDto
import ai.freya.app.data.remote.dto.ToolExecuteRequest
import ai.freya.app.data.remote.dto.ToolsListDto
import ai.freya.app.data.remote.requireData
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Backend tool registry (GET/POST /api/freya/tools). */
class ToolsRepository(private val api: FreyaApi) {

    suspend fun list(): List<ToolDto> =
        ApiErrors.safe { api.listTools() }.requireData<ToolsListDto>().tools

    /** Executes a tool and returns the backend's `result` payload exactly as received. */
    suspend fun execute(name: String, args: JsonObject): JsonElement {
        val data = ApiErrors.safe { api.executeTool(ToolExecuteRequest(name, args)) }.requireData<JsonObject>()
        return data["result"] ?: JsonNull
    }
}
