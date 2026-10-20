package ai.freya.app.voice

/** Speech-to-text abstraction. Implementations must never claim neural/continuous/duplex voice. */
interface SpeechToTextProvider {
    val isAvailable: Boolean

    fun startListening(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    )

    fun stopListening()
    fun shutdown()
}

/** Text-to-speech abstraction. */
interface TextToSpeechProvider {
    val isAvailable: Boolean

    fun speak(
        text: String,
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {},
    )

    fun stop()
    fun shutdown()
}
