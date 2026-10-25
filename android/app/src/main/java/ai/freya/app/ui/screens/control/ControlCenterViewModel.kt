package ai.freya.app.ui.screens.control

import ai.freya.app.FreyaApplication
import ai.freya.app.data.local.datastore.LocalUsage
import ai.freya.app.data.remote.NetworkModule
import ai.freya.app.data.remote.dto.KeysStatusDto
import ai.freya.app.data.remote.dto.ToolDto
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

enum class CheckStatus { UNKNOWN, RUNNING, OK, WARN, FAIL }

data class HealthCheck(val id: String, val title: String, val method: String, val status: CheckStatus, val detail: String)

/** A field rendered for a tool's input form, derived from its JSON-Schema. */
data class ToolField(val name: String, val type: String, val required: Boolean)

class ControlCenterViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as FreyaApplication).container
    val session = container.session
    private val keysRepo = container.keysRepository
    private val settingsRepo = container.settingsRepository
    private val toolsRepo = container.toolsRepository
    private val memoryRepo = container.memoryRepository
    private val authRepo = container.authRepository
    private val dataStore = container.dataStore
    private val voice = container.voiceController

    val baseUrl: String = NetworkModule.defaultBaseUrl

    private val _keys = MutableStateFlow<KeysStatusDto?>(null)
    val keys: StateFlow<KeysStatusDto?> = _keys.asStateFlow()

    private val _tts = MutableStateFlow<ai.freya.app.data.remote.dto.TtsStatusDto?>(null)
    val tts: StateFlow<ai.freya.app.data.remote.dto.TtsStatusDto?> = _tts.asStateFlow()

    private val _model = MutableStateFlow<String?>(null)
    val model: StateFlow<String?> = _model.asStateFlow()

    private val _tools = MutableStateFlow<List<ToolDto>>(emptyList())
    val tools: StateFlow<List<ToolDto>> = _tools.asStateFlow()

    private val _toolsError = MutableStateFlow<String?>(null)
    val toolsError: StateFlow<String?> = _toolsError.asStateFlow()

    private val _memoryCount = MutableStateFlow<Int?>(null)
    val memoryCount: StateFlow<Int?> = _memoryCount.asStateFlow()

    private val _checks = MutableStateFlow(initialChecks())
    val checks: StateFlow<List<HealthCheck>> = _checks.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val usage: StateFlow<LocalUsage> = dataStore.usage.stateIn(viewModelScope, SharingStarted.Eagerly, LocalUsage())
    val toolRuns = session.toolRuns
    val coreState = session.coreState
    val activeTool = session.activeTool

    val sttAvailable get() = voice.sttAvailable
    val ttsAvailable get() = voice.ttsAvailable

    init {
        refresh()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun refresh() {
        viewModelScope.launch { runCatching { keysRepo.status() }.onSuccess { _keys.value = it } }
        viewModelScope.launch { runCatching { keysRepo.ttsStatus() }.onSuccess { _tts.value = it } }
        viewModelScope.launch {
            runCatching { settingsRepo.settings() }
                .onSuccess { _model.value = it.model }
                .onFailure { _model.value = dataStore.model.first() }
        }
        viewModelScope.launch {
            runCatching { toolsRepo.list() }
                .onSuccess { _tools.value = it; _toolsError.value = null }
                .onFailure { _toolsError.value = it.message }
        }
        viewModelScope.launch { runCatching { memoryRepo.preferences() }.onSuccess { _memoryCount.value = it.size } }
        session.refreshProviderStatus()
    }

    fun saveModel(value: String) {
        val v = value.trim()
        if (v.isEmpty()) return
        viewModelScope.launch {
            dataStore.setModel(v)
            runCatching { settingsRepo.update(personality = null, model = v, voice = null) }
                .onSuccess { _model.value = it.model ?: v; _message.value = "Model preference saved on the backend" }
                .onFailure { _message.value = "Saved on this phone; backend update failed: ${it.message}" }
        }
    }

    fun resetUsage() {
        viewModelScope.launch {
            dataStore.resetUsage()
            _message.value = "Local usage counters reset"
        }
    }

    /** Runs each integration check for real and reports what it measured. */
    fun runChecks(micGranted: Boolean) {
        _checks.value = _checks.value.map { it.copy(status = CheckStatus.RUNNING, detail = "Checking…") }
        viewModelScope.launch {
            timed("backend") { keysRepo.status().let { "Reachable at $baseUrl" } }
            timed("auth") { authRepo.me().let { "Signed in as ${it.email ?: it.userId}" } }
            timed("tools") { toolsRepo.list().let { "${it.size} tools registered" } }
            timed("memory") { memoryRepo.preferences().let { "${it.size} saved preferences" } }
            set("stt", if (sttAvailable) CheckStatus.OK else CheckStatus.FAIL, if (sttAvailable) "System recognizer available" else "No recognition service on this device")
            set("tts", if (ttsAvailable) CheckStatus.OK else CheckStatus.WARN, if (ttsAvailable) "System TTS ready" else "TTS engine not ready")
            set("mic", if (micGranted) CheckStatus.OK else CheckStatus.WARN, if (micGranted) "Permission granted" else "Not granted (asked on first talk)")
        }
    }

    private suspend fun timed(id: String, block: suspend () -> String) {
        val start = System.currentTimeMillis()
        runCatching { block() }
            .onSuccess { set(id, CheckStatus.OK, "$it · ${System.currentTimeMillis() - start} ms") }
            .onFailure { set(id, CheckStatus.FAIL, it.message ?: "Failed") }
    }

    private fun set(id: String, status: CheckStatus, detail: String) {
        _checks.value = _checks.value.map { if (it.id == id) it.copy(status = status, detail = detail) else it }
    }

    private fun initialChecks() = listOf(
        HealthCheck("backend", "FREYA backend", "HTTPS · GET /api/freya/keys", CheckStatus.UNKNOWN, "Not checked yet"),
        HealthCheck("auth", "Account session", "GET /api/freya/auth/me", CheckStatus.UNKNOWN, "Not checked yet"),
        HealthCheck("tools", "Tool registry", "GET /api/freya/tools", CheckStatus.UNKNOWN, "Not checked yet"),
        HealthCheck("memory", "Memory store", "GET /api/freya/memory", CheckStatus.UNKNOWN, "Not checked yet"),
        HealthCheck("stt", "Speech recognition", "Android SpeechRecognizer", CheckStatus.UNKNOWN, "Not checked yet"),
        HealthCheck("tts", "Spoken responses", "Android TextToSpeech", CheckStatus.UNKNOWN, "Not checked yet"),
        HealthCheck("mic", "Microphone", "Android runtime permission", CheckStatus.UNKNOWN, "Not checked yet"),
    )

    // ---------------------------------------------------------------- tool forms

    fun fieldsFor(tool: ToolDto): List<ToolField> = schemaFields(tool.inputSchema, requiredOf(tool))

    /** Builds validated arguments, or returns an error message. */
    fun buildArgs(fields: List<ToolField>, values: Map<String, String>): Result<JsonObject> =
        ToolArgs.build(fields, values)

    fun runTool(name: String, args: JsonObject) = session.requestTool(name, args)

    private fun requiredOf(tool: ToolDto): Set<String> {
        val schema = tool.inputSchema as? JsonObject ?: return emptySet()
        return (schema["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet()
            ?: requiredFallback[tool.name.orEmpty()]
            ?: emptySet()
    }

    private fun schemaFields(schema: JsonElement?, required: Set<String>): List<ToolField> {
        val props = (schema as? JsonObject)?.get("properties") as? JsonObject ?: return emptyList()
        // The backend serialises properties alphabetically; show required fields first.
        return props.entries.map { (name, def) ->
            val type = (def.jsonObject["type"] as? JsonPrimitive)?.contentOrNull ?: "string"
            ToolField(name, type, name in required)
        }.sortedBy { if (it.required) 0 else 1 }
    }

    companion object {
        /** The backend's public tool view omits `required`; mirrored from mirofish_tools.py. */
        private val requiredFallback = mapOf(
            "run_simulation" to setOf("project_id", "simulation_requirement"),
            "inspect_graph" to setOf("graph_id"),
            "explain_relationships" to setOf("graph_id"),
            "interview_agents" to setOf("simulation_id", "prompts"),
            "generate_report" to setOf("simulation_id"),
            "compare_scenarios" to setOf("simulation_ids"),
            "summarize_findings" to setOf("report_id"),
        )
    }
}
