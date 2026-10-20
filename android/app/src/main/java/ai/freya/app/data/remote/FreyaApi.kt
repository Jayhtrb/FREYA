package ai.freya.app.data.remote

import ai.freya.app.data.remote.dto.BuildGraphRequest
import ai.freya.app.data.remote.dto.ChatRequest
import ai.freya.app.data.remote.dto.CreateSimulationRequest
import ai.freya.app.data.remote.dto.GenerateReportRequest
import ai.freya.app.data.remote.dto.InterviewAllRequest
import ai.freya.app.data.remote.dto.InterviewBatchRequest
import ai.freya.app.data.remote.dto.InterviewHistoryRequest
import ai.freya.app.data.remote.dto.InterviewRequest
import ai.freya.app.data.remote.dto.LoginRequest
import ai.freya.app.data.remote.dto.PreferenceUpsertRequest
import ai.freya.app.data.remote.dto.PrepareSimulationRequest
import ai.freya.app.data.remote.dto.PrepareStatusRequest
import ai.freya.app.data.remote.dto.RegisterRequest
import ai.freya.app.data.remote.dto.ReportChatRequest
import ai.freya.app.data.remote.dto.ReportStatusRequest
import ai.freya.app.data.remote.dto.SettingsUpdateRequest
import ai.freya.app.data.remote.dto.StartSimulationRequest
import ai.freya.app.data.remote.dto.StopSimulationRequest
import ai.freya.app.data.remote.dto.ToolExecuteRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface FreyaApi {

    // ---- Auth (/api/freya/auth) ----
    @POST("api/freya/auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiEnvelope

    @POST("api/freya/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope

    @GET("api/freya/auth/me")
    suspend fun me(): ApiEnvelope

    @POST("api/freya/auth/logout")
    suspend fun logout(): ApiEnvelope

    // ---- Assistant chat ----
    @POST("api/freya/chat")
    suspend fun chat(@Body body: ChatRequest): ApiEnvelope

    @GET("api/freya/conversations")
    suspend fun conversations(): ApiEnvelope

    @GET("api/freya/conversations/{id}")
    suspend fun conversation(@Path("id") id: String): ApiEnvelope

    @DELETE("api/freya/conversations/{id}")
    suspend fun deleteConversation(@Path("id") id: String): ApiEnvelope

    // ---- Tools ----
    @POST("api/freya/tools")
    suspend fun executeTool(@Body body: ToolExecuteRequest): ApiEnvelope

    @GET("api/freya/tools")
    suspend fun listTools(): ApiEnvelope

    // ---- Memory ----
    @GET("api/freya/memory")
    suspend fun memory(): ApiEnvelope

    @PUT("api/freya/memory/preferences")
    suspend fun upsertPreference(@Body body: PreferenceUpsertRequest): ApiEnvelope

    @DELETE("api/freya/memory/preferences/{key}")
    suspend fun deletePreference(@Path("key") key: String): ApiEnvelope

    @GET("api/freya/memory/search")
    suspend fun searchMemory(@Query("q") q: String): ApiEnvelope

    // ---- Search ----
    @GET("api/freya/search")
    suspend fun search(@Query("q") q: String, @Query("scope") scope: String): ApiEnvelope

    // ---- Notifications ----
    @GET("api/freya/notifications")
    suspend fun notifications(): ApiEnvelope

    @POST("api/freya/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): ApiEnvelope

    // ---- Jobs ----
    @GET("api/freya/jobs")
    suspend fun jobs(): ApiEnvelope

    @GET("api/freya/jobs/{id}")
    suspend fun job(@Path("id") id: String): ApiEnvelope

    // ---- Settings ----
    @GET("api/freya/settings")
    suspend fun settings(): ApiEnvelope

    @PUT("api/freya/settings")
    suspend fun updateSettings(@Body body: SettingsUpdateRequest): ApiEnvelope

    // ---- Persona ----
    @GET("api/freya/persona")
    suspend fun persona(): ApiEnvelope

    // ---- Projects (MiroFish /api/graph) ----
    @Multipart
    @POST("api/graph/ontology/generate")
    suspend fun generateOntology(
        @Part files: List<MultipartBody.Part>,
        @Part("simulation_requirement") simulationRequirement: RequestBody,
        @Part("project_name") projectName: RequestBody,
        @Part("additional_context") additionalContext: RequestBody,
    ): ApiEnvelope

    @POST("api/graph/build")
    suspend fun buildGraph(@Body body: BuildGraphRequest): ApiEnvelope

    @GET("api/graph/project/list")
    suspend fun listProjects(@Query("limit") limit: Int? = null): ApiEnvelope

    @GET("api/graph/project/{id}")
    suspend fun project(@Path("id") id: String): ApiEnvelope

    @DELETE("api/graph/project/{id}")
    suspend fun deleteProject(@Path("id") id: String): ApiEnvelope

    @POST("api/graph/project/{id}/reset")
    suspend fun resetProject(@Path("id") id: String): ApiEnvelope

    @GET("api/graph/task/{taskId}")
    suspend fun task(@Path("taskId") taskId: String): ApiEnvelope

    // ---- Simulations (MiroFish /api/simulation) ----
    @POST("api/simulation/create")
    suspend fun createSimulation(@Body body: CreateSimulationRequest): ApiEnvelope

    @POST("api/simulation/prepare")
    suspend fun prepareSimulation(@Body body: PrepareSimulationRequest): ApiEnvelope

    @POST("api/simulation/prepare/status")
    suspend fun prepareStatus(@Body body: PrepareStatusRequest): ApiEnvelope

    @POST("api/simulation/start")
    suspend fun startSimulation(@Body body: StartSimulationRequest): ApiEnvelope

    @POST("api/simulation/stop")
    suspend fun stopSimulation(@Body body: StopSimulationRequest): ApiEnvelope

    @GET("api/simulation/list")
    suspend fun listSimulations(@Query("project_id") projectId: String? = null): ApiEnvelope

    @GET("api/simulation/history")
    suspend fun simulationHistory(@Query("limit") limit: Int? = null): ApiEnvelope

    @GET("api/simulation/{id}")
    suspend fun simulation(@Path("id") id: String): ApiEnvelope

    @GET("api/simulation/{id}/run-status")
    suspend fun runStatus(@Path("id") id: String): ApiEnvelope

    @GET("api/simulation/{id}/profiles")
    suspend fun profiles(@Path("id") id: String, @Query("platform") platform: String? = null): ApiEnvelope

    @POST("api/simulation/interview")
    suspend fun interview(@Body body: InterviewRequest): ApiEnvelope

    @POST("api/simulation/interview/batch")
    suspend fun interviewBatch(@Body body: InterviewBatchRequest): ApiEnvelope

    @POST("api/simulation/interview/all")
    suspend fun interviewAll(@Body body: InterviewAllRequest): ApiEnvelope

    @POST("api/simulation/interview/history")
    suspend fun interviewHistory(@Body body: InterviewHistoryRequest): ApiEnvelope

    @POST("api/simulation/env-status")
    suspend fun envStatus(@Body body: StopSimulationRequest): ApiEnvelope

    // ---- Reports (MiroFish /api/report) ----
    @POST("api/report/generate")
    suspend fun generateReport(@Body body: GenerateReportRequest): ApiEnvelope

    @POST("api/report/generate/status")
    suspend fun reportStatus(@Body body: ReportStatusRequest): ApiEnvelope

    @GET("api/report/{id}")
    suspend fun report(@Path("id") id: String): ApiEnvelope

    @GET("api/report/by-simulation/{simulationId}")
    suspend fun reportBySimulation(@Path("simulationId") simulationId: String): ApiEnvelope

    @GET("api/report/list")
    suspend fun listReports(@Query("simulation_id") simulationId: String? = null): ApiEnvelope

    @GET("api/report/check/{simulationId}")
    suspend fun reportCheck(@Path("simulationId") simulationId: String): ApiEnvelope

    @Streaming
    @GET("api/report/{id}/download")
    suspend fun downloadReport(@Path("id") id: String): ResponseBody

    @POST("api/report/chat")
    suspend fun reportChat(@Body body: ReportChatRequest): ApiEnvelope
}
