package ai.freya.app.data.repository

import ai.freya.app.data.remote.ApiErrors
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.dto.JobDto
import ai.freya.app.data.remote.dto.JobsDto
import ai.freya.app.data.remote.dto.NotificationDto
import ai.freya.app.data.remote.dto.NotificationsDto
import ai.freya.app.data.remote.dto.PersonaDto
import ai.freya.app.data.remote.dto.SettingsDto
import ai.freya.app.data.remote.dto.SettingsUpdateRequest
import ai.freya.app.data.remote.requireData
import ai.freya.app.data.remote.requireSuccess
import ai.freya.app.domain.model.JobItem
import ai.freya.app.domain.model.NotificationItem

class SettingsRepository(private val api: FreyaApi) {

    suspend fun settings(): SettingsDto =
        ApiErrors.safe { api.settings() }.requireData()

    suspend fun update(personality: String?, model: String?, voice: String?): SettingsDto =
        ApiErrors.safe { api.updateSettings(SettingsUpdateRequest(personality, model, voice)) }.requireData()

    suspend fun persona(): PersonaDto =
        ApiErrors.safe { api.persona() }.requireData()

    suspend fun notifications(): List<NotificationItem> {
        val dto = ApiErrors.safe { api.notifications() }.requireData<NotificationsDto>()
        return dto.notifications.map { it.toDomain() }
    }

    suspend fun markNotificationRead(id: String) {
        ApiErrors.safe { api.markNotificationRead(id) }.requireSuccess()
    }

    suspend fun jobs(): List<JobItem> {
        val dto = ApiErrors.safe { api.jobs() }.requireData<JobsDto>()
        return dto.jobs.map { it.toDomain() }
    }

    suspend fun job(id: String): JobDto =
        ApiErrors.safe { api.job(id) }.requireData()
}

private fun NotificationDto.toDomain(): NotificationItem = NotificationItem(
    id = id ?: "",
    title = title ?: "Notification",
    body = body ?: "",
    read = read,
    createdAt = createdAt,
)

private fun JobDto.toDomain(): JobItem = JobItem(
    id = id ?: jobId ?: "",
    status = status ?: "unknown",
    progress = progress ?: 0,
    message = message ?: "",
    error = error,
)
