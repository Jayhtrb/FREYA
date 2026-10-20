package ai.freya.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * One-shot (push-to-talk) speech recognition backed by the Android SpeechRecognizer.
 * This is not continuous or duplex listening; recognition stops on end-of-speech or on error.
 */
class AndroidSpeechRecognizerProvider(context: Context) : SpeechToTextProvider {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var available = SpeechRecognizer.isRecognitionAvailable(appContext)

    override val isAvailable: Boolean get() = available && recognizer != null

    override fun startListening(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!available) {
            onError("Speech recognition is not available on this device")
            return
        }
        shutdown()
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = speechRecognizer

        val onPartialResult: (String) -> Unit = onPartial
        val onFinalResult: (String) -> Unit = onResult
        val onFailure: (String) -> Unit = onError

        speechRecognizer.setRecognitionListener(object : RecognitionListener() {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onPartialResult(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onFinalResult(text) else onFailure("I didn't catch that. Please try again.")
            }

            override fun onError(error: Int) {
                onFailure(errorMessage(error))
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure { onFailure("Could not start listening: ${it.message}") }
    }

    override fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    override fun shutdown() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "A recording error occurred."
        SpeechRecognizer.ERROR_CLIENT -> "A client error occurred."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK -> "A network error occurred."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recognizer is busy."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        else -> "Speech recognition failed."
    }
}
