package ai.freya.app.voice

/**
 * Text-only fallbacks used when the device lacks speech recognition or TTS,
 * or when the user has not granted microphone permission.
 */
class SilentSpeechToTextProvider : SpeechToTextProvider {
    override val isAvailable: Boolean get() = false
    override fun startListening(onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit) {
        onError("Voice input is not available on this device.")
    }
    override fun stopListening() = Unit
    override fun shutdown() = Unit
}

class SilentTextToSpeechProvider : TextToSpeechProvider {
    override val isAvailable: Boolean get() = false
    override fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit) {
        onError("Voice output is not available on this device.")
    }
    override fun stop() = Unit
    override fun shutdown() = Unit
}
