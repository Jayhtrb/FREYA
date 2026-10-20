package ai.freya.app.data.repository

import ai.freya.app.data.remote.ApiErrors
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.FreyaException
import ai.freya.app.data.remote.dto.CreateSimulationRequest
import ai.freya.app.data.remote.dto.InterviewAllRequest
import ai.freya.app.data.remote.dto.InterviewHistoryRequest
import ai.freya.app.data.remote.dto.InterviewRequest
import ai.freya.app.data.remote.dto.PrepareSimulationRequest
import ai.freya.app.data.remote.dto.PrepareSimulationResponseDto
import ai.freya.app.data.remote.dto.PrepareStatusDto
import ai.freya.app.data.remote.dto.PrepareStatusRequest
import ai.freya.app.data.remote.dto.RunStatusDto
import ai.freya.app.data.remote.dto.SimulationDto
import ai.freya.app.data.remote.dto.SimulationProfilesDto
import ai.freya.app.data.remote.dto.StartSimulationRequest
import ai.freya.app.data.remote.dto.StopSimulationRequest
import ai.freya.app.data.remote.requireData
import kotlinx.serialization.json.JsonElement

class SimulationRepository(private val api: FreyaApi) {

    suspend fun createSimulation(projectId: String): SimulationDto =
        ApiErrors.safe { api.createSimulation(CreateSimulationRequest(projectId)) }.requireData()

    suspend fun prepare(simulationId: String, force: Boolean = false): PrepareSimulationResponseDto =
        ApiErrors.safe {
            api.prepareSimulation(PrepareSimulationRequest(simulationId, forceRegenerate = force))
        }.requireData()

    suspend fun prepareStatus(simulationId: String, taskId: String?): PrepareStatusDto =
        ApiErrors.safe { api.prepareStatus(PrepareStatusRequest(taskId, simulationId)) }.requireData()

    suspend fun start(simulationId: String, platform: String = "parallel", maxRounds: Int? = null): RunStatusDto =
        ApiErrors.safe {
            api.startSimulation(StartSimulationRequest(simulationId, platform, maxRounds))
        }.requireData()

    suspend fun stop(simulationId: String): RunStatusDto =
        ApiErrors.safe { api.stopSimulation(StopSimulationRequest(simulationId)) }.requireData()

    suspend fun runStatus(simulationId: String): RunStatusDto =
        ApiErrors.safe { api.runStatus(simulationId) }.requireData()

    suspend fun getSimulation(simulationId: String): SimulationDto =
        ApiErrors.safe { api.simulation(simulationId) }.requireData()

    suspend fun listSimulations(projectId: String? = null): List<SimulationDto> =
        ApiErrors.safe { api.listSimulations(projectId) }.requireData()

    suspend fun profiles(simulationId: String, platform: String? = null): SimulationProfilesDto =
        ApiErrors.safe { api.profiles(simulationId, platform) }.requireData()

    suspend fun interview(simulationId: String, agentId: Int, prompt: String, platform: String? = null): JsonElement {
        val env = ApiErrors.safe { api.interview(InterviewRequest(simulationId, agentId, prompt, platform)) }
        if (!env.success) throw FreyaException(env.error ?: "Interview failed")
        return env.data ?: throw FreyaException("No interview result")
    }

    suspend fun interviewAll(simulationId: String, prompt: String, platform: String? = null): JsonElement {
        val env = ApiErrors.safe { api.interviewAll(InterviewAllRequest(simulationId, prompt, platform)) }
        if (!env.success) throw FreyaException(env.error ?: "Interview failed")
        return env.data ?: throw FreyaException("No interview result")
    }

    suspend fun interviewHistory(simulationId: String): JsonElement {
        val env = ApiErrors.safe { api.interviewHistory(InterviewHistoryRequest(simulationId)) }
        if (!env.success) throw FreyaException(env.error ?: "Could not load interview history")
        return env.data ?: throw FreyaException("No interview history")
    }
}
