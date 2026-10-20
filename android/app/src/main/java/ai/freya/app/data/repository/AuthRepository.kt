package ai.freya.app.data.repository

import ai.freya.app.data.local.datastore.FreyaDataStore
import ai.freya.app.data.local.datastore.StoredUser
import ai.freya.app.data.remote.ApiErrors
import ai.freya.app.data.remote.AuthTokenStore
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.FreyaException
import ai.freya.app.data.remote.dto.AuthResponseDto
import ai.freya.app.data.remote.dto.LoginRequest
import ai.freya.app.data.remote.dto.MeResponseDto
import ai.freya.app.data.remote.dto.RegisterRequest
import ai.freya.app.data.remote.dto.UserDto
import ai.freya.app.data.remote.requireData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val api: FreyaApi,
    private val tokenStore: AuthTokenStore,
    private val dataStore: FreyaDataStore,
) {

    val storedUser: Flow<StoredUser?> = dataStore.storedUser

    fun isAuthenticated(): Flow<Boolean> =
        dataStore.authToken.map { !it.isNullOrBlank() }

    suspend fun login(email: String, password: String): AuthResponseDto {
        val auth = ApiErrors.safe { api.login(LoginRequest(email, password)) }
            .requireData<AuthResponseDto>()
        val token = auth.token ?: throw FreyaException("Login succeeded but no token was returned")
        val user = auth.user
        tokenStore.save(token, StoredUser(user?.userId ?: "", user?.email, user?.displayName))
        return auth
    }

    suspend fun register(email: String, password: String, displayName: String): AuthResponseDto {
        val auth = ApiErrors.safe { api.register(RegisterRequest(email, password, displayName)) }
            .requireData<AuthResponseDto>()
        val token = auth.token ?: throw FreyaException("Registration succeeded but no token was returned")
        val user = auth.user
        tokenStore.save(token, StoredUser(user?.userId ?: "", user?.email, user?.displayName))
        return auth
    }

    suspend fun me(): UserDto {
        val me = ApiErrors.safe { api.me() }.requireData<MeResponseDto>()
        return me.user ?: throw FreyaException("No user returned")
    }

    suspend fun logout() {
        runCatching { ApiErrors.safe { api.logout() } }
        tokenStore.clear()
    }
}
