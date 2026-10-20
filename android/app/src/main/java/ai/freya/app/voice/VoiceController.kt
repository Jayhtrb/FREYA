package ai.freya.app.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates STT -> (caller runs the chat request) -> TTS and exposes the
 * current [VoiceState]. Voice operations are expected to run on the main thread.
 */
class VoiceController(
    private val sttProvider: SpeechToTextProvider,
    private val ttsProvider: TextToSpeechProvider,
) {

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _transcript = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val transcript: SharedFlow<String> = _transcript.asSharedFlow()

    val sttAvailable: Boolean get() = sttProvider.isAvailable
    val ttsAvailable: Boolean get() = ttsProvider.isAvailable

    fun startListening() {
        _state.value = VoiceState.LISTENING
        sttProvider.startListening(
            onPartial = {},
            onResult = { text ->
                _state.value = VoiceState.IDLE
                _transcript.tryEmit(text)
            },
            onError = { _state.value = VoiceState.IDLE },
        )
    }

    fun stopListening() {
        sttProvider.stopListening()
        _state.value = VoiceState.IDLE
    }

    fun indicateThinking() {
        _state.value = VoiceState.THINKING
    }

    fun indicateTaskExecution() {
        _state.value = VoiceState.TASK_EXECUTION
    }

    fun indicateIdle() {
        _state.value = VoiceState.IDLE
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        _state.value = VoiceState.SPEAKING
        ttsProvider.speak(
            text = text,
            onDone = {
                _state.value = VoiceState.IDLE
                onDone()
            },
            onError = { _state.value = VoiceState.IDLE },
        )
    }

    fun interrupt() {
        ttsProvider.stop()
        sttProvider.stopListening()
        _state.value = VoiceState.IDLE
    }

    fun shutdown() {
        sttProvider.shutdown()
        ttsProvider.shutdown()
    }
}
