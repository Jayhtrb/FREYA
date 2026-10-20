package ai.freya.app.data.repository

import ai.freya.app.data.remote.ApiErrors
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.dto.GenerateReportRequest
import ai.freya.app.data.remote.dto.GenerateReportResponseDto
import ai.freya.app.data.remote.dto.ReportChatMessageDto
import ai.freya.app.data.remote.dto.ReportChatRequest
import ai.freya.app.data.remote.dto.ReportChatResponseDto
import ai.freya.app.data.remote.dto.ReportCheckDto
import ai.freya.app.data.remote.dto.ReportDto
import ai.freya.app.data.remote.dto.ReportStatusDto
import ai.freya.app.data.remote.dto.ReportStatusRequest
import ai.freya.app.data.remote.requireData
import okhttp3.ResponseBody

class ReportRepository(private val api: FreyaApi) {

    suspend fun generate(simulationId: String): GenerateReportResponseDto =
        ApiErrors.safe { api.generateReport(GenerateReportRequest(simulationId)) }.requireData()

    suspend fun status(simulationId: String, taskId: String?): ReportStatusDto =
        ApiErrors.safe { api.reportStatus(ReportStatusRequest(taskId, simulationId)) }.requireData()

    suspend fun bySimulation(simulationId: String): ReportDto =
        ApiErrors.safe { api.reportBySimulation(simulationId) }.requireData()

    suspend fun getReport(reportId: String): ReportDto =
        ApiErrors.safe { api.report(reportId) }.requireData()

    suspend fun list(simulationId: String? = null): List<ReportDto> =
        ApiErrors.safe { api.listReports(simulationId) }.requireData()

    suspend fun check(simulationId: String): ReportCheckDto =
        ApiErrors.safe { api.reportCheck(simulationId) }.requireData()

    suspend fun download(reportId: String): ResponseBody =
        ApiErrors.safe { api.downloadReport(reportId) }

    suspend fun chat(
        simulationId: String,
        message: String,
        history: List<ReportChatMessageDto> = emptyList(),
    ): ReportChatResponseDto =
        ApiErrors.safe { api.reportChat(ReportChatRequest(simulationId, message, history)) }.requireData()
}
