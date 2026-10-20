package ai.freya.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class UserDto(
    @SerialName("user_id") val userId: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AuthResponseDto(
    val token: String? = null,
    val user: UserDto? = null,
)

@Serializable
data class MeResponseDto(
    val user: UserDto? = null,
)

@Serializable
data class OkDto(
    val ok: Boolean = false,
)
