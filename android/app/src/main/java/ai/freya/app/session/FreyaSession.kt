package ai.freya.app.session

import ai.freya.app.data.local.datastore.FreyaDataStore
import ai.freya.app.data.remote.ChatStreamEvent
import ai.freya.app.data.remote.FreyaException
import ai.freya.app.data.repository.ChatRepository
import ai.freya.app.data.repository.KeysRepository
import ai.freya.app.data.repository.SettingsRepository
import ai.freya.app.data.repository.ToolsRepository
import ai.freya.app.data.repository.replyText
import ai.freya.app.domain.CoreInputs
import ai.freya.app.domain.CoreState
import ai.freya.app.domain.ToolInfo
import ai.freya.app.domain.ToolPolicy
import ai.freya.app.domain.deriveCoreState
import ai.freya.app.domain.model.ChatMessage
import ai.freya.app.domain.model.MessageKind
import ai.freya.app.domain.model.MessageStatus
import ai.freya.app.domain.model.TurnMeta
import ai.freya.app.voice.VoiceController
import ai.freya.app.voice.VoiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class PendingApproval(val tool: String, val args: JsonObject, val info: ToolInfo)

enum class ToolRunStatus { RUNNING, SUCCEEDED, FAILED, DENIED }

/** Local audit record of a tool run requested from this device. */
data class ToolRun(
    val id: Long,
    val tool: String,
    val args: String,
    val status: ToolRunStatus,
    val startedAt: Long,
    val durationMs: Long? = null,
    val error: String? = null,
)

/**
 * Application-scoped conversation + task session. Owns everything the FREYA Core reflects,
 * so the Core shows the same truth on every screen and survives navigation.
 * All public functions must be called from the main thread.
 */
class FreyaSession(
    private val chatRepo: ChatRepository,
    private val toolsRepo: ToolsRepository,
    private val keysRepo: KeysRepository,
    private val settingsRepo: SettingsRepository,
    private val dataStore: FreyaDataStore,
    val voice: VoiceController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pretty = Json { prettyPrint = true }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _inFlight = MutableStateFlow(false)
    val inFlight: StateFlow<Boolean> = _inFlight.asStateFlow()

    /** Characters received in the current stream; drives the PROCESSING pattern. */
    private val _receivedChars = MutableStateFlow(0)
    val receivedChars: StateFlow<Int> = _receivedChars.asStateFlow()

    private val _activeTool = MutableStateFlow<String?>(null)
    val activeTool: StateFlow<String?> = _activeTool.asStateFlow()

    private val _pendingApproval = MutableStateFlow<PendingApproval?>(null)
    val pendingApproval: StateFlow<PendingApproval?> = _pendingApproval.asStateFlow()

    private val _toolRuns = MutableStateFlow<List<ToolRun>>(emptyList())
    val toolRuns: StateFlow<List<ToolRun>> = _toolRuns.asStateFlow()

    private val _sessionError = MutableStateFlow<String?>(null)

    /** Increments when a task completes successfully (avatar "acknowledge" expression). */
    private val _acknowledgeTick = MutableStateFlow(0L)
    val acknowledgeTick: StateFlow<Long> = _acknowledgeTick.asStateFlow()

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    /** null = unknown / backend unreachable; true/false = BYOK status from the backend. */
    private val _providerConfigured = MutableStateFlow<Boolean?>(null)
    val providerConfigured: StateFlow<Boolean?> = _providerConfigured.asStateFlow()

    /** Model reported by the backend on the last reply, else the saved model setting. */
    private val _activeModel = MutableStateFlow<String?>(null)
    val activeModel: StateFlow<String?> = _activeModel.asStateFlow()

    private val _backendReachable = MutableStateFlow<Boolean?>(null)
    val backendReachable: StateFlow<Boolean?> = _backendReachable.asStateFlow()

    val error: StateFlow<String?> = combine(_sessionError, voice.error) { s, v -> s ?: v }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val coreState: StateFlow<CoreState> = combine(
        voice.state, _inFlight, _activeTool, _pendingApproval, error,
    ) { v, busy, tool, approval, err ->
        deriveCoreState(CoreInputs(v, busy, tool, approval != null, err))
    }.stateIn(scope, SharingStarted.Eagerly, CoreState.IDLE)

    private var voiceEnabled = false
    private var handsFree = false
    private var sendJob: Job? = null
    private var toolJob: Job? = null

    init {
        scope.launch { dataStore.voiceEnabled.collect { voiceEnabled = it } }
        scope.launch { dataStore.handsFreeEnabled.collect { handsFree = it } }
        scope.launch { dataStore.speechRate.collect { voice.setSpeechRate(it) } }
        scope.launch { voice.transcript.collect { if (it.isNotBlank()) send(it) } }
        refreshProviderStatus()
    }

    // ---------------------------------------------------------------- conversation

    fun send(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || _inFlight.value) return
        dismissError()
        if (voice.state.value == VoiceState.SPEAKING) voice.stopSpeaking()
        val placeholder = ChatMessage("assistant", "", status = MessageStatus.STREAMING)
        _messages.value = _messages.value + ChatMessage("user", text) + placeholder
        _inFlight.value = true
        _receivedChars.value = 0
        voice.indicateThinking()

        val conversationId = _conversationId.value
        val started = System.currentTimeMillis()
        sendJob = scope.launch {
            val reply = StringBuilder()
            var firstTokenAt: Long? = null
            val toolEvents = mutableListOf<String>()
            var model: String? = null
            var failed = false
            try {
                chatRepo.streamChat(text, conversationId).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Delta -> {
                            if (firstTokenAt == null) firstTokenAt = System.currentTimeMillis()
                            reply.append(event.text)
                            _receivedChars.value = reply.length
                            update(placeholder.id) { it.copy(content = reply.toString()) }
                        }
                        is ChatStreamEvent.ToolStarted -> {
                            _activeTool.value = event.name
                            toolEvents += "▶ ${event.name}"
                        }
                        is ChatStreamEvent.ToolFinished -> {
                            _activeTool.value = null
                            toolEvents += if (event.ok) "✓ ${event.name}" else "✕ ${event.name}"
                            if (event.ok) _acknowledgeTick.value++
                        }
                        is ChatStreamEvent.Done -> {
                            event.conversationId?.takeIf { it.isNotBlank() }?.let { _conversationId.value = it }
                            model = event.model
                            event.model?.let { _activeModel.value = it }
                        }
                        is ChatStreamEvent.Error -> throw FreyaException(event.message)
                    }
                }
                finish(placeholder.id, reply.toString(), TurnMeta(
                    model = model, conversationId = _conversationId.value, transport = "stream",
                    firstTokenMs = firstTokenAt?.minus(started), totalMs = System.currentTimeMillis() - started,
                    chars = reply.length, toolEvents = toolEvents,
                ))
            } catch (e: CancellationException) {
                update(placeholder.id) {
                    it.copy(status = MessageStatus.STOPPED, content = reply.toString(),
                        meta = TurnMeta(model = model, transport = "stream", chars = reply.length,
                            totalMs = System.currentTimeMillis() - started, toolEvents = toolEvents))
                }
                throw e
            } catch (e: Exception) {
                if (reply.isEmpty()) {
                    failed = !fallbackNonStreaming(text, conversationId, placeholder.id, started, e)
                } else {
                    finish(placeholder.id, reply.toString(), TurnMeta(model = model, transport = "stream (interrupted)",
                        firstTokenMs = firstTokenAt?.minus(started), totalMs = System.currentTimeMillis() - started,
                        chars = reply.length, toolEvents = toolEvents))
                    _sessionError.value = e.message
                }
            } finally {
                _inFlight.value = false
                _activeTool.value = null
                voice.indicateIdle()
                scope.launch { dataStore.recordRequest(text.length, reply.length, failed) }
            }
        }
    }

    /** Returns true when the fallback produced a reply. */
    private suspend fun fallbackNonStreaming(
        message: String, conversationId: String?, placeholderId: Long, started: Long, streamError: Exception,
    ): Boolean = try {
        val response = chatRepo.sendChat(message, conversationId)
        response.conversationId?.let { _conversationId.value = it }
        response.model?.let { _activeModel.value = it }
        val text = response.replyText().orEmpty()
        finish(placeholderId, text, TurnMeta(
            model = response.model, conversationId = _conversationId.value, transport = "request (stream unavailable)",
            totalMs = System.currentTimeMillis() - started, chars = text.length,
        ))
        true
    } catch (e: Exception) {
        val reason = e.message ?: streamError.message ?: "Request failed"
        update(placeholderId) { it.copy(status = MessageStatus.FAILED, error = reason) }
        _sessionError.value = reason
        false
    }

    private fun finish(id: Long, text: String, meta: TurnMeta) {
        update(id) { it.copy(content = text, status = MessageStatus.DONE, meta = meta) }
        if (voiceEnabled && voice.ttsAvailable && text.isNotBlank()) speakText(text)
    }

    private fun speakText(text: String) {
        voice.speak(text) {
            if (handsFree && voice.sttAvailable) voice.startListening()
        }
    }

    /** Stops the request in flight and any speech. */
    fun stop() {
        sendJob?.cancel()
        voice.interrupt()
    }

    /** Re-sends the user message that produced [assistantId], replacing the old answer. */
    fun retry(assistantId: Long) {
        if (_inFlight.value) return
        val list = _messages.value
        val index = list.indexOfFirst { it.id == assistantId }
        if (index <= 0) return
        val prompt = list.subList(0, index).lastOrNull { it.isUser } ?: return
        _messages.value = list.filterNot { it.id == assistantId || it.id == prompt.id }
        send(prompt.content)
    }

    fun readAloud(id: Long) {
        val message = _messages.value.firstOrNull { it.id == id } ?: return
        if (!voice.ttsAvailable) {
            _sessionError.value = "Text-to-speech isn't available on this device."
            return
        }
        voice.speak(message.content)
    }

    fun feedback(id: Long, value: Int) {
        val current = _messages.value.firstOrNull { it.id == id } ?: return
        val next = if (current.feedback == value) 0 else value
        update(id) { it.copy(feedback = next) }
        if (next != 0) scope.launch { dataStore.recordFeedback(next > 0) }
    }

    fun newConversation() {
        stop()
        _messages.value = emptyList()
        _conversationId.value = null
        dismissError()
    }

    fun dismissError() {
        _sessionError.value = null
        voice.clearError()
    }

    fun refreshProviderStatus() {
        scope.launch {
            runCatching { keysRepo.status() }
                .onSuccess { _providerConfigured.value = it.anySet; _backendReachable.value = true }
                .onFailure {
                    _backendReachable.value = (it as? FreyaException)?.code != null
                    _providerConfigured.value = null
                }
            // The saved model setting only means something when a provider is configured.
            if (_activeModel.value == null && _providerConfigured.value == true) {
                runCatching { settingsRepo.settings().model }.getOrNull()?.let { _activeModel.value = it }
            }
        }
    }

    // ---------------------------------------------------------------- tools

    /** Entry point for every tool run. Sensitive tools wait for [approve]. */
    fun requestTool(name: String, args: JsonObject) {
        if (_activeTool.value != null || _pendingApproval.value != null) return
        dismissError()
        if (ToolPolicy.requiresApproval(name)) {
            _pendingApproval.value = PendingApproval(name, args, ToolPolicy.info(name))
        } else {
            runTool(name, args)
        }
    }

    fun approve() {
        val pending = _pendingApproval.value ?: return
        _pendingApproval.value = null
        runTool(pending.tool, pending.args)
    }

    fun deny() {
        val pending = _pendingApproval.value ?: return
        _pendingApproval.value = null
        _toolRuns.value = listOf(
            ToolRun(System.nanoTime(), pending.tool, pending.args.toString(), ToolRunStatus.DENIED, System.currentTimeMillis())
        ) + _toolRuns.value
    }

    fun cancelTool() {
        toolJob?.cancel()
    }

    private fun runTool(name: String, args: JsonObject) {
        val runId = System.nanoTime()
        val started = System.currentTimeMillis()
        _activeTool.value = name
        voice.indicateTaskExecution()
        _toolRuns.value = listOf(ToolRun(runId, name, args.toString(), ToolRunStatus.RUNNING, started)) + _toolRuns.value
        toolJob = scope.launch {
            try {
                val result: JsonElement = toolsRepo.execute(name, args)
                val body = pretty.encodeToString(JsonElement.serializer(), result).take(6000)
                _messages.value = _messages.value + ChatMessage(
                    role = "assistant", content = body, kind = MessageKind.TOOL_RESULT, toolName = name,
                    meta = TurnMeta(transport = "POST /api/freya/tools", totalMs = System.currentTimeMillis() - started, chars = body.length),
                )
                updateRun(runId) { it.copy(status = ToolRunStatus.SUCCEEDED, durationMs = System.currentTimeMillis() - started) }
                _acknowledgeTick.value++
            } catch (e: CancellationException) {
                updateRun(runId) { it.copy(status = ToolRunStatus.FAILED, error = "Cancelled", durationMs = System.currentTimeMillis() - started) }
                throw e
            } catch (e: Exception) {
                val reason = e.message ?: "Task failed"
                _messages.value = _messages.value + ChatMessage(
                    role = "assistant", content = "", kind = MessageKind.TOOL_RESULT, toolName = name,
                    status = MessageStatus.FAILED, error = reason,
                )
                updateRun(runId) { it.copy(status = ToolRunStatus.FAILED, error = reason, durationMs = System.currentTimeMillis() - started) }
                _sessionError.value = "${ToolPolicy.info(name).title} failed: $reason"
            } finally {
                _activeTool.value = null
                voice.indicateIdle()
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun update(id: Long, transform: (ChatMessage) -> ChatMessage) {
        _messages.value = _messages.value.map { if (it.id == id) transform(it) else it }
    }

    private fun updateRun(id: Long, transform: (ToolRun) -> ToolRun) {
        _toolRuns.value = _toolRuns.value.map { if (it.id == id) transform(it) else it }
    }
}
