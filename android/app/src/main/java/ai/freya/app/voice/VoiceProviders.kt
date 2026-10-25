package ai.freya.app.voice

/** Speech-to-text abstraction. Implementations must never claim neural/continuous/duplex voice. */
interface SpeechToTextProvider {
    /** True when the device has a recognition service. Does not imply mic permission. */
    val isAvailable: Boolean

    /**
     * One-shot recognition. Callbacks are delivered on the main thread.
     * [onLevel] receives the live input level normalised to 0..1 (from RMS dB).
     */
    fun startListening(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onLevel: (Float) -> Unit = {},
        onReady: () -> Unit = {},
    )

    fun stopListening()
    fun cancel() = stopListening()
    fun shutdown()
}

/** Text-to-speech abstraction. Callbacks are delivered on the main thread. */
interface TextToSpeechProvider {
    val isAvailable: Boolean

    /**
     * [onWord] fires with (start, end) character offsets as each word is actually spoken
     * (Android `onRangeStart`). Engines that do not report ranges never call it.
     */
    fun speak(
        text: String,
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {},
        onStart: () -> Unit = {},
        onWord: (Int, Int) -> Unit = { _, _ -> },
    )

    fun setSpeechRate(rate: Float) = Unit
    fun stop()
    fun shutdown()
}
