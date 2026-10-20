package ai.freya.app.di

import ai.freya.app.data.local.datastore.FreyaDataStore
import ai.freya.app.data.local.db.FreyaDatabase
import ai.freya.app.data.remote.AuthTokenStore
import ai.freya.app.data.remote.ChatSseClient
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.NetworkModule
import ai.freya.app.data.repository.AuthRepository
import ai.freya.app.data.repository.ChatRepository
import ai.freya.app.data.repository.MemoryRepository
import ai.freya.app.data.repository.ProjectsRepository
import ai.freya.app.data.repository.ReportRepository
import ai.freya.app.data.repository.SearchRepository
import ai.freya.app.data.repository.SettingsRepository
import ai.freya.app.data.repository.SimulationRepository
import ai.freya.app.voice.AndroidSpeechRecognizerProvider
import ai.freya.app.voice.AndroidTextToSpeechProvider
import ai.freya.app.voice.SpeechToTextProvider
import ai.freya.app.voice.TextToSpeechProvider
import ai.freya.app.voice.VoiceController
import android.content.Context
import androidx.room.Room

/**
 * Manual dependency container owned by [ai.freya.app.FreyaApplication]. Kept
 * dependency-injection-free to avoid KSP/Hilt wiring risk on CI.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    // ---- Local ----
    val dataStore = FreyaDataStore(appContext)
    val database: FreyaDatabase =
        Room.databaseBuilder(appContext, FreyaDatabase::class.java, "freya.db").build()

    // ---- Network ----
    val tokenStore = AuthTokenStore(dataStore)
    private val okHttpClient =
        NetworkModule.buildOkHttpClient { tokenStore.currentToken() }
    private val sseOkHttpClient =
        NetworkModule.buildSseOkHttpClient { tokenStore.currentToken() }
    private val retrofit = NetworkModule.buildRetrofit(okHttpClient, NetworkModule.defaultBaseUrl)
    val api: FreyaApi = NetworkModule.buildApi(retrofit)
    val sseClient = ChatSseClient(sseOkHttpClient, NetworkModule.defaultBaseUrl)

    // ---- Repositories ----
    val authRepository = AuthRepository(api, tokenStore, dataStore)
    val chatRepository = ChatRepository(api, sseClient, database.conversationDao())
    val projectsRepository = ProjectsRepository(api, database.projectDao())
    val simulationRepository = SimulationRepository(api)
    val reportRepository = ReportRepository(api)
    val searchRepository = SearchRepository(api)
    val memoryRepository = MemoryRepository(api)
    val settingsRepository = SettingsRepository(api)

    // ---- Voice (system STT/TTS; they self-report availability and degrade to text-only) ----
    val sttProvider: SpeechToTextProvider = AndroidSpeechRecognizerProvider(appContext)
    val ttsProvider: TextToSpeechProvider = AndroidTextToSpeechProvider(appContext)
    val voiceController = VoiceController(sttProvider, ttsProvider)
}
