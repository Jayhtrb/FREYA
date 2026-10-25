package ai.freya.app.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Pure audio helpers (unit-tested). Nothing here touches Android APIs, so the rendering
 * layer only ever receives plain numbers.
 */
object AudioEnvelope {

    /** Envelope frame length. 20 ms ≈ the resolution of visible lip/energy motion. */
    const val FRAME_MS = 20

    data class WavInfo(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val dataOffset: Int, val dataSize: Int)

    /** Parses a RIFF/WAVE header. Returns null for anything that isn't uncompressed 16-bit PCM. */
    fun parseWav(bytes: ByteArray): WavInfo? {
        if (bytes.size < 44) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var pos = 12
        var format = -1
        var channels = 0
        var rate = 0
        var bits = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            var size = b.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    if (body + 16 > bytes.size) return null
                    format = b.getShort(body).toInt() and 0xFFFF
                    channels = b.getShort(body + 2).toInt()
                    rate = b.getInt(body + 4)
                    bits = b.getShort(body + 14).toInt()
                }
                "data" -> {
                    // Some encoders write 0 or 0xFFFFFFFF while streaming; clamp to what exists.
                    if (size <= 0 || body + size > bytes.size) size = bytes.size - body
                    if (format != 1 || bits != 16 || channels <= 0 || rate <= 0) return null
                    return WavInfo(rate, channels, bits, body, size)
                }
            }
            if (size < 0) return null
            pos = body + size + (size and 1) // chunks are word-aligned
        }
        return null
    }

    /**
     * RMS loudness per [FRAME_MS] window, mapped from dBFS to 0..1
     * (-50 dBFS → 0, -5 dBFS → 1). Channels are mixed down.
     */
    fun envelope(bytes: ByteArray, info: WavInfo): FloatArray {
        val frameSamples = (info.sampleRate * FRAME_MS / 1000).coerceAtLeast(1)
        val bytesPerSample = 2 * info.channels
        val totalSamples = info.dataSize / bytesPerSample
        val frames = (totalSamples + frameSamples - 1) / frameSamples
        val out = FloatArray(frames)
        val b = ByteBuffer.wrap(bytes, info.dataOffset, info.dataSize).order(ByteOrder.LITTLE_ENDIAN)
        var s = 0
        for (f in 0 until frames) {
            var sum = 0.0
            var n = 0
            val end = minOf(totalSamples, s + frameSamples)
            while (s < end) {
                var mix = 0
                for (ch in 0 until info.channels) mix += b.getShort(info.dataOffset + (s * info.channels + ch) * 2).toInt()
                val v = mix.toDouble() / info.channels
                sum += v * v
                n++
                s++
            }
            out[f] = if (n == 0) 0f else dbToLevel(sqrt(sum / n) / 32768.0)
        }
        return out
    }

    fun dbToLevel(rms: Double): Float {
        if (rms <= 1e-6) return 0f
        val db = 20 * log10(rms)
        return ((db + 50.0) / 45.0).toFloat().coerceIn(0f, 1f)
    }

    /** Duration in ms of the PCM payload. */
    fun durationMs(info: WavInfo): Long = info.dataSize.toLong() * 1000 / (info.sampleRate.toLong() * 2 * info.channels)

    /** Envelope value at a playback position. */
    fun levelAt(envelope: FloatArray, positionMs: Long): Float {
        if (envelope.isEmpty() || positionMs < 0) return 0f
        val i = (positionMs / FRAME_MS).toInt()
        return if (i >= envelope.size) 0f else envelope[i]
    }

    /**
     * Splits a reply into speakable chunks at sentence boundaries, each at most [maxChars],
     * so the first sentence can play while later ones are still being synthesized.
     */
    fun chunks(text: String, maxChars: Int = 220): List<String> {
        val cleaned = text.replace(Regex("```[\\s\\S]*?```"), " (code omitted) ")
            .replace(Regex("[*_#`>]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return emptyList()
        val sentences = Regex("(?<=[.!?…])\\s+").split(cleaned)
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (sentence in sentences) {
            if (sentence.length > maxChars) {
                if (sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
                sentence.chunked(maxChars).forEach { out += it }
                continue
            }
            if (sb.length + sentence.length + 1 > maxChars && sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(sentence)
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }
}

/**
 * Attack/release envelope follower: rises fast with the voice, decays smoothly, and is
 * frame-rate independent (rates are per second). Removes jitter without adding lag to onsets.
 */
class EnvelopeFollower(private val attackPerSec: Float = 28f, private val releasePerSec: Float = 7f) {
    var value = 0f
        private set

    fun update(target: Float, dtSeconds: Float): Float {
        val t = target.coerceIn(0f, 1f)
        val rate = if (t > value) attackPerSec else releasePerSec
        val k = 1f - exp(-rate * dtSeconds.coerceIn(0f, 0.25f))
        value += (t - value) * k
        return value
    }

    fun reset() {
        value = 0f
    }
}
