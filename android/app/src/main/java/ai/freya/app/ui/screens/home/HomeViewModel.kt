package ai.freya.app.ui.screens.home

import ai.freya.app.FreyaApplication
import ai.freya.app.data.remote.ChatStreamEvent
import ai.freya.app.data.remote.FreyaException
import ai.freya.app.data.repository.replyText
import ai.freya.app.domain.Persona
import ai.freya.app.domain.SessionGreeting
import ai.freya.app.domain.model.ChatMessage
import ai.freya.app.voice.VoiceState
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as FreyaApplication).container
    private val chatRepo = container.chatRepository
    private val keysRepo = container.keysRepository
    val voiceController = container.voiceController

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _coreOnline = MutableStateFlow(false)
    val coreOnline: StateFlow<Boolean> = _coreOnline.asStateFlow()

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceController.state
    val sttAvailable: Boolean get() = voiceController.sttAvailable
    val ttsAvailable: Boolean get() = voiceController.ttsAvailable

    val greeting: String = Persona.greetingBody()
    val showGreeting: Boolean get() = !SessionGreeting.shown

    private var voiceEnabled = false
    private var handsFree = false
    private var sendJob: Job? = null

    init {
        viewModelScope.launch { container.dataStore.voiceEnabled.collect { voiceEnabled = it } }
        viewModelScope.launch { container.dataStore.handsFreeEnabled.collect { handsFree = it } }
        viewModelScope.launch {
            voiceController.transcript.collect { text ->
                if (text.isNotBlank()) send(text)
            }
        }
        viewModelScope.launch {
            runCatching { keysRepo.status() }.getOrNull()?.let { _coreOnline.value = it.anySet }
        }
    }

    fun markGreetingShown() {
        SessionGreeting.shown = true
    }

    fun clearError() {
        _error.value = null
    }

    fun send(rawText: String) {
        val message = rawText.trim()
        if (message.isEmpty() || _streaming.value) return
        _error.value = null
        _messages.value = _messages.value + ChatMessage("user", message)
        _streaming.value = true
        voiceController.indicateThinking()

        val conversationId = _conversationId.value
        sendJob = viewModelScope.launch {
            val reply = StringBuilder()
            try {
                chatRepo.streamChat(message, conversationId).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Delta -> {
                            reply.append(event.text)
                            updateAssistant(reply.toString())
                        }
                        is ChatStreamEvent.Done -> {
                            if (!event.conversationId.isNullOrBlank()) {
                                _conversationId.value = event.conversationId
                            }
                            finishReply(reply.toString())
                        }
                        is ChatStreamEvent.Error -> throw FreyaException(event.message)
                    }
                }
            } catch (e: Exception) {
                if (reply.isEmpty()) {
                    fallbackNonStreaming(message, conversationId)
                } else {
                    finishReply(reply.toString())
                    _error.value = e.message
                }
            } finally {
                _streaming.value = false
            }
        }
    }

    private suspend fun fallbackNonStreaming(message: String, conversationId: String?) {
        try {
            val response = chatRepo.sendChat(message, conversationId)
            response.conversationId?.let { _conversationId.value = it }
            updateAssistant(response.replyText().orEmpty())
        } catch (e: Exception) {
            _error.value = e.message
        }
    }

    private fun updateAssistant(text: String) {
        val base = _messages.value
        val last = base.lastOrNull()
        _messages.value = if (last != null && last.role == "assistant") {
            base.dropLast(1) + ChatMessage("assistant", text)
        } else {
            base + ChatMessage("assistant", text)
        }
    }

    private fun finishReply(text: String) {
        updateAssistant(text)
        _streaming.value = false
        if (voiceEnabled && voiceController.ttsAvailable && text.isNotBlank()) {
            speakReply(text)
        } else {
            voiceController.indicateIdle()
        }
    }

    private fun speakReply(text: String) {
        voiceController.speak(text) {
            if (handsFree && voiceController.sttAvailable) {
                voiceController.startListening()
            }
        }
    }

    fun toggleListening() {
        if (voiceController.state.value == VoiceState.LISTENING) {
            voiceController.stopListening()
        } else {
            voiceController.startListening()
        }
    }

    fun interrupt() {
        sendJob?.cancel()
        voiceController.interrupt()
        _streaming.value = false
    }

    override fun onCleared() {
        voiceController.interrupt()
        super.onCleared()
    }
}
