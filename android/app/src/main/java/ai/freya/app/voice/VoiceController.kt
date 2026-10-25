package ai.freya.app.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates STT -> (caller runs the chat request) -> TTS and exposes live voice
 * signals for the UI. Every value here comes from the platform:
 *  - [inputLevel]  : microphone level from SpeechRecognizer.onRmsChanged (0..1)
 *  - [partial]     : live partial transcript from onPartialResults
 *  - [wordTick]    : increments each time the TTS engine reports a spoken word (onRangeStart)
 *  - [speakingText]: the utterance currently being spoken, plus [spokenUpTo] offset
 * Must be driven from the main thread.
 */
class VoiceController(
    private val sttProvider: SpeechToTextProvider,
    private val ttsProvider: TextToSpeechProvider,
) {

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _transcript = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Final transcripts, emitted once per completed listen. */
    val transcript: SharedFlow<String> = _transcript.asSharedFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _inputLevel = MutableStateFlow(0f)
    val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    private val _wordTick = MutableStateFlow(0L)
    val wordTick: StateFlow<Long> = _wordTick.asStateFlow()

    /** True once the current utterance has reported real word timing. */
    private val _wordSynced = MutableStateFlow(false)
    val wordSynced: StateFlow<Boolean> = _wordSynced.asStateFlow()

    /** True between the engine's real onStart and onDone (audio is actually playing). */
    private val _audible = MutableStateFlow(false)
    val audible: StateFlow<Boolean> = _audible.asStateFlow()

    private val _speakingText = MutableStateFlow<String?>(null)
    val speakingText: StateFlow<String?> = _speakingText.asStateFlow()

    private val _spokenUpTo = MutableStateFlow(0)
    val spokenUpTo: StateFlow<Int> = _spokenUpTo.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val sttAvailable: Boolean get() = sttProvider.isAvailable
    val ttsAvailable: Boolean get() = ttsProvider.isAvailable

    fun setSpeechRate(rate: Float) = ttsProvider.setSpeechRate(rate)

    fun clearError() {
        _error.value = null
    }

    fun startListening() {
        if (_state.value == VoiceState.SPEAKING) stopSpeaking()
        _error.value = null
        _partial.value = ""
        _state.value = VoiceState.LISTENING
        sttProvider.startListening(
            onPartial = { _partial.value = it },
            onResult = { text ->
                _inputLevel.value = 0f
                _partial.value = text
                _state.value = VoiceState.IDLE
                _transcript.tryEmit(text)
            },
            onError = { message ->
                _inputLevel.value = 0f
                // A user-initiated cancel is not an error worth surfacing.
                if (_state.value == VoiceState.LISTENING) _error.value = message
                _state.value = VoiceState.IDLE
            },
            onLevel = { _inputLevel.value = it },
        )
    }

    /** Ends capture and lets the recognizer deliver its final result (push-to-talk release). */
    fun stopListening() {
        sttProvider.stopListening()
    }

    /** Abandons the current capture without sending anything. */
    fun cancelListening() {
        _state.value = VoiceState.IDLE
        sttProvider.cancel()
        _inputLevel.value = 0f
        _partial.value = ""
    }

    fun indicateThinking() {
        _state.value = VoiceState.THINKING
    }

    fun indicateTaskExecution() {
        _state.value = VoiceState.TASK_EXECUTION
    }

    fun indicateIdle() {
        if (_state.value != VoiceState.LISTENING && _state.value != VoiceState.SPEAKING) {
            _state.value = VoiceState.IDLE
        }
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        _error.value = null
        _state.value = VoiceState.SPEAKING
        _speakingText.value = text
        _spokenUpTo.value = 0
        _wordSynced.value = false
        _audible.value = false
        ttsProvider.speak(
            text = text,
            onStart = { _audible.value = true },
            onWord = { _, end ->
                _wordSynced.value = true
                _spokenUpTo.value = end
                _wordTick.value = _wordTick.value + 1
            },
            onDone = {
                finishSpeaking()
                onDone()
            },
            onError = { message ->
                finishSpeaking()
                _error.value = message
            },
        )
    }

    private fun finishSpeaking() {
        _audible.value = false
        _speakingText.value = null
        if (_state.value == VoiceState.SPEAKING) _state.value = VoiceState.IDLE
    }

    fun stopSpeaking() {
        ttsProvider.stop()
        finishSpeaking()
    }

    /** Stops everything: playback and capture. */
    fun interrupt() {
        ttsProvider.stop()
        sttProvider.cancel()
        _audible.value = false
        _speakingText.value = null
        _inputLevel.value = 0f
        _state.value = VoiceState.IDLE
    }

    fun shutdown() {
        sttProvider.shutdown()
        ttsProvider.shutdown()
    }
}
