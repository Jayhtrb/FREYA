package ai.freya.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * System text-to-speech. One-shot speech only; there is no neural or duplex claim here.
 */
class AndroidTextToSpeechProvider(context: Context) : TextToSpeechProvider {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
    }

    override val isAvailable: Boolean get() = ready && tts != null

    override fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit) {
        val engine = tts
        if (engine == null || !ready) {
            onError("Text-to-speech is not available")
            return
        }
        val onDoneCallback: () -> Unit = onDone
        val onErrorCallback: (String) -> Unit = onError
        val utteranceId = "freya_${System.currentTimeMillis()}"

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = onDoneCallback()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onErrorCallback("Speech playback failed")
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) onErrorCallback("Speech playback failed")
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    override fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }
}
