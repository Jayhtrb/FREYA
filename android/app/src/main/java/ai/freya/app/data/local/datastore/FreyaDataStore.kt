package ai.freya.app.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.freyaDataStore by preferencesDataStore(name = "freya_preferences")

data class StoredUser(
    val userId: String,
    val email: String?,
    val displayName: String?,
)

/**
 * Single Preferences DataStore holding both the auth token and user-scoped
 * preferences (personality/model/voice).
 */
class FreyaDataStore(private val context: Context) {

    private object Keys {
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PERSONALITY = stringPreferencesKey("personality")
        val MODEL = stringPreferencesKey("model")
        val VOICE = stringPreferencesKey("voice")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val HANDS_FREE = booleanPreferencesKey("hands_free")
    }

    val authToken: Flow<String?> = context.freyaDataStore.data.map { it[Keys.AUTH_TOKEN] }

    val storedUser: Flow<StoredUser?> = context.freyaDataStore.data.map { p ->
        val id = p[Keys.USER_ID] ?: return@map null
        StoredUser(id, p[Keys.EMAIL], p[Keys.DISPLAY_NAME])
    }

    val personality: Flow<String> =
        context.freyaDataStore.data.map { it[Keys.PERSONALITY] ?: "composed" }

    val model: Flow<String> =
        context.freyaDataStore.data.map { it[Keys.MODEL] ?: "deepseek-chat" }

    val voice: Flow<String> =
        context.freyaDataStore.data.map { it[Keys.VOICE] ?: "system_tts" }

    val voiceEnabled: Flow<Boolean> =
        context.freyaDataStore.data.map { it[Keys.VOICE_ENABLED] ?: false }

    val handsFreeEnabled: Flow<Boolean> =
        context.freyaDataStore.data.map { it[Keys.HANDS_FREE] ?: false }

    suspend fun currentToken(): String? = context.freyaDataStore.data.first()[Keys.AUTH_TOKEN]

    suspend fun setAuth(token: String, user: StoredUser) {
        context.freyaDataStore.edit { p ->
            p[Keys.AUTH_TOKEN] = token
            p[Keys.USER_ID] = user.userId
            p[Keys.EMAIL] = user.email ?: ""
            p[Keys.DISPLAY_NAME] = user.displayName ?: ""
        }
    }

    suspend fun clearAuth() {
        context.freyaDataStore.edit { p ->
            p.remove(Keys.AUTH_TOKEN)
            p.remove(Keys.USER_ID)
            p.remove(Keys.EMAIL)
            p.remove(Keys.DISPLAY_NAME)
        }
    }

    suspend fun setPersonality(value: String) =
        context.freyaDataStore.edit { it[Keys.PERSONALITY] = value }

    suspend fun setModel(value: String) =
        context.freyaDataStore.edit { it[Keys.MODEL] = value }

    suspend fun setVoice(value: String) =
        context.freyaDataStore.edit { it[Keys.VOICE] = value }

    suspend fun setVoiceEnabled(value: Boolean) =
        context.freyaDataStore.edit { it[Keys.VOICE_ENABLED] = value }

    suspend fun setHandsFree(value: Boolean) =
        context.freyaDataStore.edit { it[Keys.HANDS_FREE] = value }
}
