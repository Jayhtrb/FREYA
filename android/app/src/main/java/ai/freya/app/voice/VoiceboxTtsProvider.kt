package ai.freya.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.Executors

/**
 * FREYA's Voicebox voice, through the FREYA backend (`POST /api/freya/tts`).
 *
 * Each reply sentence is synthesized by Voicebox on the user's computer and returned as WAV.
 * The WAV's RMS envelope is computed here, the clip is played with MediaPlayer, and the
 * envelope at the real playback position is published as the measured output level, exactly
 * like the system-voice path, so the FREYA Core moves with the Voicebox voice.
 * Sentence N+1 is fetched while sentence N plays.
 *
 * If Voicebox can't produce a sentence (not configured, unreachable, error), the rest of the
 * reply is handed to [fallback] (the phone's voice) once the current clip finishes, so
 * FREYA never goes silent and never overlaps two voices.
 */
class VoiceboxTtsProvider(
    context: Context,
    httpClient: OkHttpClient,
    private val baseUrl: String,
    private val fallback: TextToSpeechProvider,
) : TextToSpeechProvider {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "freya-voicebox").apply { isDaemon = true } }
    private val cacheDir = File(context.applicationContext.cacheDir, "voicebox").apply { mkdirs() }
    private val client = httpClient.newBuilder()
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var rate = 1f
    private var generation = 0
    private var job: Job? = null

    /** Voicebox availability is decided by the backend; the switch only routes here when chosen. */
    override val isAvailable: Boolean get() = true

    override fun setSpeechRate(rate: Float) {
        this.rate = rate.coerceIn(0.5f, 2f)
        fallback.setSpeechRate(rate)
    }

    private class Clip(val file: File, val envelope: FloatArray, val bands: FloatArray)

    private inner class Job(
        val gen: Int,
        val chunks: List<String>,
        val onDone: () -> Unit,
        val onError: (String) -> Unit,
        val onStart: () -> Unit,
        val onWord: (Int, Int) -> Unit,
        val onLevel: (Float) -> Unit,
        val onMeasured: (Boolean) -> Unit,
        val onBands: (FloatArray) -> Unit,
    ) {
        val clips = HashMap<Int, Clip>()
        val bandBuf = FloatArray(AudioEnvelope.BANDS)
        var nextToPlay = 0
        var player: MediaPlayer? = null
        var started = false
        var fallbackFrom = -1
        var handedOff = false
    }

    override fun speak(
        text: String,
        onDone: () -> Unit,
        onError: (String) -> Unit,
        onStart: () -> Unit,
        onWord: (Int, Int) -> Unit,
        onLevel: (Float) -> Unit,
        onMeasured: (Boolean) -> Unit,
        onBands: (FloatArray) -> Unit,
    ) {
        stop()
        val chunks = AudioEnvelope.chunks(text)
        if (chunks.isEmpty()) {
            onDone()
            return
        }
        val j = Job(++generation, chunks, onDone, onError, onStart, onWord, onLevel, onMeasured, onBands)
        job = j
        fetch(j, 0)
    }

    private fun fetch(j: Job, index: Int) {
        if (index >= j.chunks.size) return
        val text = j.chunks[index]
        val speed = rate
        io.execute {
            val clip: Clip? = runCatching {
                val body = buildJsonObject {
                    put("text", text)
                    put("speed", speed)
                }.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url("${baseUrl.trimEnd('/')}/api/freya/tts").post(body).build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val bytes = resp.body?.bytes() ?: return@runCatching null
                    val info = AudioEnvelope.parseWav(bytes) ?: return@runCatching null
                    val file = File(cacheDir, "v_${j.gen}_$index.wav").apply { writeBytes(bytes) }
                    Clip(file, AudioEnvelope.envelope(bytes, info), AudioEnvelope.bands(bytes, info))
                }
            }.getOrNull()
            main.post {
                val current = job?.takeIf { it.gen == j.gen } ?: run { clip?.file?.delete(); return@post }
                if (clip == null) {
                    handOff(current, index)
                    return@post
                }
                current.clips[index] = clip
                if (current.player == null && current.nextToPlay == index) play(current, index)
                fetch(current, index + 1)
            }
        }
    }

    private fun play(j: Job, index: Int) {
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
            mp.setOnCompletionListener { finished(j.gen, index, clip.file) }
            mp.setOnErrorListener { _, _, _ -> finished(j.gen, index, clip.file); true }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            mp.release()
            clip.file.delete()
            handOff(j, index)
            return
        }
        j.player = mp
        if (!j.started) {
            j.started = true
            j.onMeasured(true)
            j.onStart()
        }
        main.postDelayed(object : Runnable {
            override fun run() {
                val cur = job?.takeIf { it.gen == j.gen && it.player === mp } ?: return
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(-1L)
                cur.onLevel(AudioEnvelope.levelAt(clip.envelope, pos))
                AudioEnvelope.bandsAt(clip.bands, pos, cur.bandBuf)
                cur.onBands(cur.bandBuf)
                main.postDelayed(this, 30)
            }
        }, 30)
    }

    private fun finished(gen: Int, index: Int, file: File) {
        file.delete()
        val j = job?.takeIf { it.gen == gen } ?: return
        j.player?.release()
        j.player = null
        j.onLevel(0f)
        j.nextToPlay = index + 1
        when {
            j.nextToPlay >= j.chunks.size -> done(j)
            j.clips.containsKey(j.nextToPlay) -> play(j, j.nextToPlay)
            j.fallbackFrom in 0..j.nextToPlay -> startFallback(j)
            else -> Unit // still fetching
        }
    }

    /** Voicebox couldn't produce sentence [index]: the phone's voice takes over from there. */
    private fun handOff(j: Job, index: Int) {
        if (j.fallbackFrom >= 0) return
        j.fallbackFrom = index
        if (j.player == null && j.nextToPlay >= index) startFallback(j)
    }

    private fun startFallback(j: Job) {
        if (j.handedOff) return
        j.handedOff = true
        val rest = j.chunks.drop(j.fallbackFrom).joinToString(" ")
        fallback.speak(
            text = rest,
            onDone = { done(j) },
            onError = { msg -> if (job === j) { job = null; j.onError(msg) } },
            onStart = { if (!j.started) { j.started = true; j.onStart() } },
            onWord = j.onWord,
            onLevel = j.onLevel,
            onMeasured = j.onMeasured,
            onBands = j.onBands,
        )
    }

    private fun done(j: Job) {
        if (job !== j) return
        job = null
        j.onLevel(0f)
        j.onDone()
    }

    override fun stop() {
        generation++
        val j = job
        job = null
        j?.player?.let { runCatching { it.stop() }; it.release() }
        j?.clips?.values?.forEach { it.file.delete() }
        j?.onLevel(0f)
        fallback.stop()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    override fun shutdown() {
        stop()
        io.shutdown()
    }
}

/**
 * Routes speech to the engine chosen in Settings: the phone's TTS or Voicebox.
 * Both paths report measured output levels; Voicebox falls back to the phone voice itself.
 */
class SwitchingTtsProvider(
    private val system: TextToSpeechProvider,
    private val voicebox: TextToSpeechProvider,
) : TextToSpeechProvider {

    @Volatile var useVoicebox: Boolean = false

    private val active: TextToSpeechProvider get() = if (useVoicebox) voicebox else system

    override val isAvailable: Boolean get() = if (useVoicebox) true else system.isAvailable

    override fun speak(
        text: String,
        onDone: () -> Unit,
        onError: (String) -> Unit,
        onStart: () -> Unit,
        onWord: (Int, Int) -> Unit,
        onLevel: (Float) -> Unit,
        onMeasured: (Boolean) -> Unit,
        onBands: (FloatArray) -> Unit,
    ) = active.speak(text, onDone, onError, onStart, onWord, onLevel, onMeasured, onBands)

    override fun setSpeechRate(rate: Float) {
        system.setSpeechRate(rate)
        voicebox.setSpeechRate(rate)
    }

    override fun stop() {
        voicebox.stop()
        system.stop()
    }

    override fun shutdown() {
        voicebox.shutdown()
        system.shutdown()
    }
}
