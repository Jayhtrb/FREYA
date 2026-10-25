package ai.freya.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.concurrent.Executors

/**
 * System text-to-speech with a measured amplitude path.
 *
 * Primary path (measured): each sentence is synthesized to a WAV file with
 * `synthesizeToFile`, its RMS envelope is computed on a worker thread, and the file is played
 * with MediaPlayer. While it plays, the envelope value at the *current playback position* is
 * published through `onLevel`, so the FREYA Core moves with the audio actually leaving the
 * speaker. Sentence N+1 is synthesized while sentence N plays.
 *
 * Fallback (not measured): if the engine can't synthesize to a readable 16-bit PCM WAV, the
 * remaining text is spoken directly with `speak()`; only word timing (`onRangeStart`) is
 * reported and `onMeasured(false)` tells the UI not to present it as measured audio.
 *
 * All callbacks are delivered on the main thread.
 */
class AndroidTextToSpeechProvider(context: Context) : TextToSpeechProvider {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "freya-tts-envelope").apply { isDaemon = true } }
    private val cacheDir = File(appContext.cacheDir, "tts").apply { mkdirs() }

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private var rate = 1.0f

    /** Incremented on every speak/stop; stale callbacks compare against it and drop out. */
    private var generation = 0
    private var job: SpeechJob? = null

    init {
        tts = TextToSpeech(appContext) { status ->
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
        onLevel: (Float) -> Unit,
        onMeasured: (Boolean) -> Unit,
    ) {
        val engine = tts
        if (engine == null || !ready) {
            onError("Text-to-speech is not available")
            return
        }
        stop()
        val chunks = AudioEnvelope.chunks(text)
        if (chunks.isEmpty()) {
            onDone()
            return
        }
        val gen = ++generation
        val j = SpeechJob(gen, chunks, onDone, onError, onStart, onWord, onLevel, onMeasured)
        job = j
        engine.setOnUtteranceProgressListener(listener)
        synthesize(j, 0)
    }

    // ------------------------------------------------------------------ measured path

    private class Clip(val file: File, val envelope: FloatArray)

    private inner class SpeechJob(
        val gen: Int,
        val chunks: List<String>,
        val onDone: () -> Unit,
        val onError: (String) -> Unit,
        val onStart: () -> Unit,
        val onWord: (Int, Int) -> Unit,
        val onLevel: (Float) -> Unit,
        val onMeasured: (Boolean) -> Unit,
    ) {
        val clips = HashMap<Int, Clip>()
        var nextToPlay = 0
        var player: MediaPlayer? = null
        var started = false
        var fallback = false
        var fallbackFrom = 0
        var fallbackQueued = false
    }

    private fun utteranceId(gen: Int, index: Int, kind: String) = "freya:$gen:$index:$kind"

    private fun synthesize(j: SpeechJob, index: Int) {
        if (index >= j.chunks.size) return
        val file = File(cacheDir, "u_${j.gen}_$index.wav")
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId(j.gen, index, "file")) }
        val result = tts?.synthesizeToFile(j.chunks[index], params, file, utteranceId(j.gen, index, "file"))
        if (result != TextToSpeech.SUCCESS) startFallback(j, index)
    }

    private fun onSynthesized(gen: Int, index: Int) {
        if (job?.gen != gen) return
        val file = File(cacheDir, "u_${gen}_$index.wav")
        worker.execute {
            val envelope = runCatching {
                val bytes = file.readBytes()
                AudioEnvelope.parseWav(bytes)?.let { AudioEnvelope.envelope(bytes, it) }
            }.getOrNull()
            main.post {
                val current = job?.takeIf { it.gen == gen } ?: run { file.delete(); return@post }
                if (envelope == null) {
                    file.delete()
                    startFallback(current, index)
                    return@post
                }
                current.clips[index] = Clip(file, envelope)
                if (current.player == null && current.nextToPlay == index) play(current, index)
                synthesize(current, index + 1)
            }
        }
    }

    private fun play(j: SpeechJob, index: Int) {
        val clip = j.clips.remove(index) ?: return
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(clip.file.absolutePath)
            mp.setOnCompletionListener { onClipFinished(j.gen, index, clip.file) }
            mp.setOnErrorListener { _, _, _ -> onClipFinished(j.gen, index, clip.file); true }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            mp.release()
            clip.file.delete()
            startFallback(j, index)
            return
        }
        j.player = mp
        if (!j.started) {
            j.started = true
            j.onMeasured(true)
            j.onStart()
        }
        pollLevel(j.gen, mp, clip.envelope)
    }

    /** Publishes the envelope at the real playback position every 30 ms while this clip plays. */
    private fun pollLevel(gen: Int, mp: MediaPlayer, envelope: FloatArray) {
        main.postDelayed(object : Runnable {
            override fun run() {
                val j = job?.takeIf { it.gen == gen && it.player === mp } ?: return
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(-1L)
                j.onLevel(AudioEnvelope.levelAt(envelope, pos))
                main.postDelayed(this, 30)
            }
        }, 30)
    }

    private fun onClipFinished(gen: Int, index: Int, file: File) {
        file.delete()
        val j = job?.takeIf { it.gen == gen } ?: return
        j.player?.release()
        j.player = null
        j.onLevel(0f)
        j.nextToPlay = index + 1
        when {
            j.nextToPlay >= j.chunks.size -> finish(j)
            j.clips.containsKey(j.nextToPlay) -> play(j, j.nextToPlay)
            j.fallback && j.nextToPlay >= j.fallbackFrom -> enqueueDirectSpeech(j)
            else -> Unit // still synthesizing; onSynthesized will start it
        }
    }

    // ------------------------------------------------------------------ fallback path

    private fun startFallback(j: SpeechJob, fromIndex: Int) {
        if (j.fallback) return
        j.fallback = true
        j.fallbackFrom = fromIndex
        j.onMeasured(false)
        // Never overlap: if a measured clip is still playing (or ready to play before this
        // index), direct speech starts from onClipFinished once those have played.
        if (j.player == null && j.nextToPlay >= fromIndex) enqueueDirectSpeech(j)
    }

    private fun enqueueDirectSpeech(j: SpeechJob) {
        if (j.fallbackQueued) return
        j.fallbackQueued = true
        val fromIndex = j.fallbackFrom
        val rest = j.chunks.drop(fromIndex)
        rest.forEachIndexed { i, chunk ->
            val id = utteranceId(j.gen, fromIndex + i, "speak")
            val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id) }
            tts?.speak(chunk, TextToSpeech.QUEUE_ADD, params, id)
        }
    }

    // ------------------------------------------------------------------ engine callbacks

    private val listener = object : UtteranceProgressListener() {
        private fun parse(id: String?): Triple<Int, Int, String>? {
            val p = id?.split(':') ?: return null
            if (p.size != 4 || p[0] != "freya") return null
            return Triple(p[1].toIntOrNull() ?: return null, p[2].toIntOrNull() ?: return null, p[3])
        }

        override fun onStart(id: String?) {
            val (gen, _, kind) = parse(id) ?: return
            if (kind != "speak") return
            main.post {
                val j = job?.takeIf { it.gen == gen } ?: return@post
                if (!j.started) { j.started = true; j.onStart() }
            }
        }

        override fun onDone(id: String?) {
            val (gen, index, kind) = parse(id) ?: return
            main.post {
                if (kind == "file") {
                    onSynthesized(gen, index)
                } else {
                    val j = job?.takeIf { it.gen == gen } ?: return@post
                    if (index == j.chunks.lastIndex) finish(j)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(id: String?) = onError(id, -1)

        override fun onError(id: String?, errorCode: Int) {
            val (gen, index, kind) = parse(id) ?: return
            main.post {
                val j = job?.takeIf { it.gen == gen } ?: return@post
                if (kind == "file") startFallback(j, index)
                else {
                    job = null
                    j.onLevel(0f)
                    j.onError("Speech playback failed ($errorCode)")
                }
            }
        }

        override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
            val (gen, _, kind) = parse(id) ?: return
            if (kind != "speak") return
            main.post { job?.takeIf { it.gen == gen }?.onWord?.invoke(start, end) }
        }
    }

    private fun finish(j: SpeechJob) {
        if (job !== j) return
        job = null
        j.onLevel(0f)
        j.onDone()
    }

    override fun stop() {
        generation++
        val j = job
        job = null
        runCatching { tts?.stop() }
        j?.player?.let { runCatching { it.stop() }; it.release() }
        j?.clips?.values?.forEach { it.file.delete() }
        j?.onLevel(0f)
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    override fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        worker.shutdown()
    }
}
