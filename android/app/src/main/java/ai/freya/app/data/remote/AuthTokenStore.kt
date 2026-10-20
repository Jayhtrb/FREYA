package ai.freya.app.data.remote

import ai.freya.app.data.local.datastore.FreyaDataStore
import ai.freya.app.data.local.datastore.StoredUser

/**
 * Keeps the JWT in a volatile in-memory field so the OkHttp [AuthInterceptor]
 * can read it synchronously, while persisting it to DataStore for relaunch.
 */
class AuthTokenStore(private val dataStore: FreyaDataStore) {

    @Volatile
    var token: String? = null
        private set

    fun currentToken(): String? = token

    suspend fun loadInitial() {
        token = dataStore.currentToken()
    }

    suspend fun save(token: String, user: StoredUser) {
        this.token = token
        dataStore.setAuth(token, user)
    }

    suspend fun clear() {
        token = null
        dataStore.clearAuth()
    }
}
