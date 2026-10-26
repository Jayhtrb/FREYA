package ai.freya.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * One-shot (push-to-talk) speech recognition backed by the Android SpeechRecognizer.
 * This is not continuous or duplex listening; recognition stops on end-of-speech,
 * on [stopListening], or on error. Must be called on the main thread; the platform
 * delivers listener callbacks on the main thread.
 *
 * Privacy note: the system recognizer may process audio on-device or send it to the
 * recognition service chosen in Android settings (often Google). FREYA itself never
 * records or uploads raw audio; only the final transcript is sent as a chat message.
 */
class AndroidSpeechRecognizerProvider(context: Context) : SpeechToTextProvider {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    // Fixed: previously this also required `recognizer != null`, which is only true after the
    // first listen, so the mic control was hidden on a fresh launch.
    override val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    override fun startListening(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onReady: () -> Unit,
    ) {
        if (!isAvailable) {
            onError("Speech recognition is not available on this device")
            return
        }
        shutdown()
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = speechRecognizer
        val partialCb = onPartial
        val resultCb = onResult
        val errorCb = onError
        val levelCb = onLevel
        val readyCb = onReady

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = readyCb()
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = levelCb(normaliseRms(rmsdB))
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = levelCb(0f)
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) partialCb(text)
            }

            override fun onResults(results: Bundle?) {
                levelCb(0f)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) resultCb(text) else errorCb(NO_SPEECH)
            }

            override fun onError(error: Int) {
                levelCb(0f)
                errorCb(errorMessage(error))
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure { onError("Could not start listening: ${it.message}") }
    }

    override fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    override fun cancel() {
        runCatching { recognizer?.cancel() }
    }

    override fun shutdown() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "A recording error occurred."
        SpeechRecognizer.ERROR_CLIENT -> "Listening was cancelled."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK -> "The speech service had a network error."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> NO_SPEECH
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recognizer is busy."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> NO_SPEECH
        else -> "Speech recognition failed."
    }

    companion object {
        /** A quiet listen is not a failure; the controller treats this as a notice, not an error. */
        const val NO_SPEECH = "I didn't hear anything — tap the mic to try again."

        /** Android reports roughly -2..10 dB; map to 0..1 with a little headroom. */
        fun normaliseRms(rmsdB: Float): Float = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
    }
}
