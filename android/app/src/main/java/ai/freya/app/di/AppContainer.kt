package ai.freya.app.di

import ai.freya.app.data.local.datastore.FreyaDataStore
import ai.freya.app.data.local.db.FreyaDatabase
import ai.freya.app.data.remote.AuthTokenStore
import ai.freya.app.data.remote.ChatSseClient
import ai.freya.app.data.remote.FreyaApi
import ai.freya.app.data.remote.NetworkModule
import ai.freya.app.data.repository.AuthRepository
import ai.freya.app.data.repository.ChatRepository
import ai.freya.app.data.repository.KeysRepository
import ai.freya.app.data.repository.ToolsRepository
import ai.freya.app.session.FreyaSession
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
import ai.freya.app.voice.VoiceboxTtsProvider
import ai.freya.app.voice.SwitchingTtsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    val okHttpClient =
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
    val keysRepository = KeysRepository(api)
    val toolsRepository = ToolsRepository(api)

    // ---- Voice (system STT/TTS; they self-report availability and degrade to text-only) ----
    val sttProvider: SpeechToTextProvider = AndroidSpeechRecognizerProvider(appContext)
    private val systemTts = AndroidTextToSpeechProvider(appContext)
    private val voiceboxTts = VoiceboxTtsProvider(appContext, okHttpClient, NetworkModule.defaultBaseUrl, fallback = systemTts)
    val ttsSwitch = SwitchingTtsProvider(systemTts, voiceboxTts)
    val ttsProvider: TextToSpeechProvider = ttsSwitch
    val voiceController = VoiceController(sttProvider, ttsProvider)

    init {
        // Route spoken replies to the engine chosen in Settings (phone voice or Voicebox).
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            dataStore.voiceEngine.collect { ttsSwitch.useVoicebox = it == "voicebox" }
        }
    }

    // ---- Session (conversation, tasks, approvals; drives the FREYA Core on every screen) ----
    val session: FreyaSession by lazy {
        FreyaSession(chatRepository, toolsRepository, keysRepository, settingsRepository, dataStore, voiceController)
    }
}
