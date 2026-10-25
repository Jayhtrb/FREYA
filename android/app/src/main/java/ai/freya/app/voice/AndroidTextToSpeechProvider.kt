package ai.freya.app.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * System text-to-speech. One-shot speech only; there is no neural or duplex claim here.
 *
 * UtteranceProgressListener callbacks arrive on a binder thread, so every callback is
 * re-posted to the main thread (the previous version restarted the SpeechRecognizer from
 * that background thread in hands-free mode, which the platform does not allow).
 */
class AndroidTextToSpeechProvider(context: Context) : TextToSpeechProvider {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private var rate = 1.0f
    private var activeUtterance: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.setSpeechRate(rate)
        }
    }

    override val isAvailable: Boolean get() = ready && tts != null

    override fun setSpeechRate(rate: Float) {
        this.rate = rate.coerceIn(0.5f, 2.0f)
        if (ready) tts?.setSpeechRate(this.rate)
    }

    override fun speak(
        text: String,
        onDone: () -> Unit,
        onError: (String) -> Unit,
        onStart: () -> Unit,
        onWord: (Int, Int) -> Unit,
    ) {
        val engine = tts
        if (engine == null || !ready) {
            onError("Text-to-speech is not available")
            return
        }
        val utteranceId = "freya_${System.nanoTime()}"
        activeUtterance = utteranceId
        val startCb = onStart
        val doneCb = onDone
        val errorCb = onError
        val wordCb = onWord

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            private fun mine(id: String?) = id == activeUtterance

            override fun onStart(id: String?) {
                if (mine(id)) main.post { startCb() }
            }

            override fun onDone(id: String?) {
                if (mine(id)) main.post { doneCb() }
            }

            override fun onStop(id: String?, interrupted: Boolean) {
                if (mine(id)) main.post { doneCb() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (mine(id)) main.post { errorCb("Speech playback failed") }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (mine(id)) main.post { errorCb("Speech playback failed ($errorCode)") }
            }

            // API 26+: reported by engines that support word timing (e.g. Google TTS).
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                if (mine(id)) main.post { wordCb(start, end) }
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) onError("Speech playback failed")
    }

    override fun stop() {
        activeUtterance = null
        runCatching { tts?.stop() }
    }

    override fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }
}
