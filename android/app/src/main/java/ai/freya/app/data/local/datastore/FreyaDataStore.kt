package ai.freya.app.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
data class LocalUsage(
    val requests: Long = 0,
    val charsSent: Long = 0,
    val charsReceived: Long = 0,
    val failures: Long = 0,
    val feedbackUp: Long = 0,
    val feedbackDown: Long = 0,
) {
    /** Rough token estimate (~4 characters per token for English). Clearly an estimate. */
    val estimatedTokens: Long get() = (charsSent + charsReceived) / 4
}

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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRESENCE = stringPreferencesKey("presence")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val VOICE_ENGINE = stringPreferencesKey("voice_engine")
        val USAGE_REQUESTS = longPreferencesKey("usage_requests")
        val USAGE_CHARS_OUT = longPreferencesKey("usage_chars_out")
        val USAGE_CHARS_IN = longPreferencesKey("usage_chars_in")
        val USAGE_FAILURES = longPreferencesKey("usage_failures")
        val FEEDBACK_UP = longPreferencesKey("feedback_up")
        val FEEDBACK_DOWN = longPreferencesKey("feedback_down")
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

    /** "dark" (default) | "light" | "system". */
    val themeMode: Flow<String> = context.freyaDataStore.data.map { it[Keys.THEME_MODE] ?: "dark" }

    /** "avatar" (default) | "core" (abstract, non-humanlike). */
    val presence: Flow<String> = context.freyaDataStore.data.map { it[Keys.PRESENCE] ?: "avatar" }

    val reduceMotion: Flow<Boolean> = context.freyaDataStore.data.map { it[Keys.REDUCE_MOTION] ?: false }
    val haptics: Flow<Boolean> = context.freyaDataStore.data.map { it[Keys.HAPTICS] ?: true }
    val speechRate: Flow<Float> = context.freyaDataStore.data.map { it[Keys.SPEECH_RATE] ?: 1.0f }

    /** "system" (phone TTS, default) | "voicebox" (Voicebox via the FREYA backend). */
    val voiceEngine: Flow<String> = context.freyaDataStore.data.map { it[Keys.VOICE_ENGINE] ?: "system" }

    /** Usage measured by this app on this device (the backend does not report usage yet). */
    val usage: Flow<LocalUsage> = context.freyaDataStore.data.map {
        LocalUsage(
            requests = it[Keys.USAGE_REQUESTS] ?: 0L,
            charsSent = it[Keys.USAGE_CHARS_IN] ?: 0L,
            charsReceived = it[Keys.USAGE_CHARS_OUT] ?: 0L,
            failures = it[Keys.USAGE_FAILURES] ?: 0L,
            feedbackUp = it[Keys.FEEDBACK_UP] ?: 0L,
            feedbackDown = it[Keys.FEEDBACK_DOWN] ?: 0L,
        )
    }

    suspend fun setThemeMode(value: String) = context.freyaDataStore.edit { it[Keys.THEME_MODE] = value }
    suspend fun setPresence(value: String) = context.freyaDataStore.edit { it[Keys.PRESENCE] = value }
    suspend fun setReduceMotion(value: Boolean) = context.freyaDataStore.edit { it[Keys.REDUCE_MOTION] = value }
    suspend fun setHaptics(value: Boolean) = context.freyaDataStore.edit { it[Keys.HAPTICS] = value }
    suspend fun setVoiceEngine(value: String) = context.freyaDataStore.edit { it[Keys.VOICE_ENGINE] = value }
    suspend fun setSpeechRate(value: Float) = context.freyaDataStore.edit { it[Keys.SPEECH_RATE] = value.coerceIn(0.5f, 2.0f) }

    suspend fun recordRequest(charsSent: Int, charsReceived: Int, failed: Boolean) =
        context.freyaDataStore.edit { p ->
            p[Keys.USAGE_REQUESTS] = (p[Keys.USAGE_REQUESTS] ?: 0L) + 1
            p[Keys.USAGE_CHARS_IN] = (p[Keys.USAGE_CHARS_IN] ?: 0L) + charsSent
            p[Keys.USAGE_CHARS_OUT] = (p[Keys.USAGE_CHARS_OUT] ?: 0L) + charsReceived
            if (failed) p[Keys.USAGE_FAILURES] = (p[Keys.USAGE_FAILURES] ?: 0L) + 1
        }

    suspend fun recordFeedback(positive: Boolean) = context.freyaDataStore.edit { p ->
        val key = if (positive) Keys.FEEDBACK_UP else Keys.FEEDBACK_DOWN
        p[key] = (p[key] ?: 0L) + 1
    }

    suspend fun resetUsage() = context.freyaDataStore.edit { p ->
        listOf(Keys.USAGE_REQUESTS, Keys.USAGE_CHARS_IN, Keys.USAGE_CHARS_OUT, Keys.USAGE_FAILURES, Keys.FEEDBACK_UP, Keys.FEEDBACK_DOWN)
            .forEach { p.remove(it) }
    }

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
