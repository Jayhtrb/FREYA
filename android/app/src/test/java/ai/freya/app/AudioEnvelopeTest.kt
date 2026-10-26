package ai.freya.app

import ai.freya.app.voice.AudioEnvelope
import ai.freya.app.voice.EnvelopeFollower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class AudioEnvelopeTest {

    /** 16-bit mono PCM WAV: [loudMs] of a 440 Hz tone at [amp], then [silentMs] of silence. */
    private fun wav(rate: Int = 24000, loudMs: Int = 200, silentMs: Int = 200, amp: Double = 0.5, extraChunk: Boolean = false, freq: Double = 440.0): ByteArray {
        val samples = rate * (loudMs + silentMs) / 1000
        val pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            val loud = i < rate * loudMs / 1000
            val v = if (loud) (sin(2 * PI * freq * i / rate) * amp * 32767).toInt() else 0
            pcm.putShort(v.toShort())
        }
        val out = ByteArrayOutputStream()
        fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
        fun le16(v: Int) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
        val list = if (extraChunk) "LIST".toByteArray() + le32(4) + "INFO".toByteArray() else ByteArray(0)
        out.write("RIFF".toByteArray()); out.write(le32(36 + list.size + samples * 2)); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); out.write(le32(16)); out.write(le16(1)); out.write(le16(1))
        out.write(le32(rate)); out.write(le32(rate * 2)); out.write(le16(2)); out.write(le16(16))
        out.write(list)
        out.write("data".toByteArray()); out.write(le32(samples * 2)); out.write(pcm.array())
        return out.toByteArray()
    }

    @Test fun parsesPcmHeaderAndSkipsUnknownChunks() {
        val info = AudioEnvelope.parseWav(wav(extraChunk = true))
        assertNotNull(info)
        assertEquals(24000, info!!.sampleRate)
        assertEquals(1, info.channels)
        assertEquals(400L, AudioEnvelope.durationMs(info))
    }

    @Test fun rejectsNonWav() {
        assertNull(AudioEnvelope.parseWav(ByteArray(100)))
        assertNull(AudioEnvelope.parseWav("RIFF".toByteArray()))
    }

    @Test fun envelopeFollowsLoudThenSilent() {
        val bytes = wav()
        val env = AudioEnvelope.envelope(bytes, AudioEnvelope.parseWav(bytes)!!)
        assertEquals(20, env.size) // 400 ms / 20 ms
        assertTrue("tone should be loud: ${env[3]}", env[3] > 0.6f)
        assertTrue("silence should be ~0: ${env[15]}", env[15] < 0.01f)
        assertTrue(AudioEnvelope.levelAt(env, 50) > 0.6f)
        assertEquals(0f, AudioEnvelope.levelAt(env, 5000))
        assertEquals(0f, AudioEnvelope.levelAt(env, -1))
    }

    @Test fun quieterToneGivesLowerLevel() {
        val loud = wav(amp = 0.8).let { AudioEnvelope.envelope(it, AudioEnvelope.parseWav(it)!!)[2] }
        val quiet = wav(amp = 0.05).let { AudioEnvelope.envelope(it, AudioEnvelope.parseWav(it)!!)[2] }
        assertTrue("$loud > $quiet", loud > quiet + 0.2f)
    }

    @Test fun followerRisesFastAndDecaysSlowly() {
        val f = EnvelopeFollower(attackPerSec = 30f, releasePerSec = 6f)
        repeat(3) { f.update(1f, 1f / 60) }
        val afterAttack = f.value
        assertTrue("attack: $afterAttack", afterAttack > 0.7f)
        repeat(3) { f.update(0f, 1f / 60) }
        assertTrue("release keeps energy: ${f.value}", f.value > 0.5f)
        repeat(200) { f.update(0f, 1f / 60) }
        assertTrue(f.value < 0.01f)
    }

    @Test fun followerIsFrameRateIndependent() {
        val a = EnvelopeFollower(); repeat(60) { a.update(1f, 1f / 60) }
        val b = EnvelopeFollower(); repeat(30) { b.update(1f, 1f / 30) }
        assertEquals(a.value, b.value, 0.02f)
    }

    @Test fun chunksSplitAtSentencesAndDropCode() {
        val text = "First sentence. Second one! Here's code: ```val x = 1``` Done?"
        val chunks = AudioEnvelope.chunks(text, maxChars = 30)
        assertTrue(chunks.none { it.contains("val x") })
        assertTrue(chunks.all { it.length <= 30 })
        assertEquals("First sentence. Second one!", chunks.first())
        assertTrue(AudioEnvelope.chunks("   ").isEmpty())
    }

    private fun bandsOf(bytes: ByteArray): Pair<FloatArray, Int> {
        val info = AudioEnvelope.parseWav(bytes)!!
        return AudioEnvelope.bands(bytes, info) to (info.dataSize / 2 / (info.sampleRate * AudioEnvelope.FRAME_MS / 1000))
    }

    private fun dominantBand(bands: FloatArray, frame: Int): Int =
        (0 until AudioEnvelope.BANDS).maxBy { bands[frame * AudioEnvelope.BANDS + it] }

    @Test fun bandsLocateTheToneInTheSpectrum() {
        val (low, _) = bandsOf(wav(freq = 440.0))
        val (high, _) = bandsOf(wav(freq = 3100.0))
        assertEquals(2, dominantBand(low, 3))
        assertEquals(6, dominantBand(high, 3))
        // A loud tone registers strongly in its own band.
        assertTrue(low[3 * AudioEnvelope.BANDS + 2] > 0.6f)
    }

    @Test fun bandsAreSilentDuringSilence() {
        val (b, frames) = bandsOf(wav(loudMs = 100, silentMs = 300))
        val last = frames - 2
        for (k in 0 until AudioEnvelope.BANDS) assertEquals(0f, b[last * AudioEnvelope.BANDS + k], 0.001f)
    }

    @Test fun bandsAtReadsTheFrameAtThePlaybackPosition() {
        val (b, _) = bandsOf(wav(freq = 440.0, loudMs = 100, silentMs = 300))
        val dst = FloatArray(AudioEnvelope.BANDS)
        AudioEnvelope.bandsAt(b, 40, dst)
        assertTrue(dst[2] > 0.5f)
        AudioEnvelope.bandsAt(b, 350, dst)
        assertEquals(0f, dst[2], 0.001f)
        AudioEnvelope.bandsAt(b, 60_000, dst)
        assertTrue(dst.all { it == 0f })
    }
}
