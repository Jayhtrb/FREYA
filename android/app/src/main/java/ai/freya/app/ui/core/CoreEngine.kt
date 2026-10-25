package ai.freya.app.ui.core

import ai.freya.app.domain.CoreState
import ai.freya.app.voice.EnvelopeFollower
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/** What the Core is told each frame. The renderer never owns audio, permissions or network. */
data class CoreInput(
    val state: CoreState,
    /** Measured envelope 0..1 for the active direction (mic while listening, voice while speaking). */
    val audioLevel: Float = 0f,
    /** false when the active level is not measured audio (timing-only fallback or none). */
    val audioMeasured: Boolean = false,
    /** Spoken-word counter from TTS timing; drives the clearly-labelled fallback only. */
    val wordTick: Long = 0,
    /** Characters streamed so far in the current reply (shifts the PROCESSING pattern). */
    val receivedChars: Int = 0,
    /** Increments when a task completes successfully. */
    val acknowledgeTick: Long = 0,
)

/**
 * Simulation state for the FREYA Core: eased state style and colours, smoothed audio,
 * particles, travelling pulses and onset ripples. Pure Kotlin (no drawing, no Android),
 * advanced once per frame by [step] and read by the renderer in the draw phase.
 */
class CoreEngine(
    private val appearance: CoreAppearance = CoreAppearance.Default,
    seed: Int = 7,
) {
    private val rnd = Random(seed)

    // ---- clocks ----
    /** Ambient time; advances faster with energy, frozen with reduce-motion. */
    var time = 0f; private set
    /** Real elapsed seconds (ripples, pulses). */
    var clock = 0f; private set

    // ---- eased style ----
    var state = CoreState.IDLE; private set
    var energy = 0.2f; private set
    var turbulence = 0.2f; private set
    var strandAmp = 0.3f; private set
    var audioGain = 0f; private set
    var spread = 0f; private set
    var travel = 0f; private set
    var glow = 0.6f; private set
    var lock = 0f; private set
    var fracture = 0f; private set
    var satellites = 0f; private set
    var primary: Color = appearance.palette.idle.primary; private set
    var secondary: Color = appearance.palette.idle.secondary; private set
    var accent: Color = appearance.palette.idle.accent; private set

    // ---- audio ----
    private val follower = EnvelopeFollower(attackPerSec = 30f, releasePerSec = 8f)
    private val slow = EnvelopeFollower(attackPerSec = 4f, releasePerSec = 3f)
    /** Smoothed audio level actually used for geometry (0 outside listening/speaking). */
    var level = 0f; private set
    /** True when [level] is measured audio rather than the timing fallback. */
    var levelMeasured = false; private set
    private var wordEnv = 0f
    private var lastWordTick = 0L
    private var lastRipple = -10f

    // ---- processing pattern ----
    var charsPhase = 0f; private set

    // ---- acknowledge ----
    var ack = 0f; private set
    private var lastAckTick = 0L

    // ---- particles (struct-of-arrays, no per-frame allocation) ----
    val particleCount = (150 * appearance.particleDensity).toInt().coerceIn(16, 320)
    val pAngle = FloatArray(particleCount) { rnd.nextFloat() * TAU }
    val pRadius = FloatArray(particleCount) { 0.25f + rnd.nextFloat() * 1.05f }
    val pDepth = FloatArray(particleCount) { rnd.nextFloat() }
    val pSpeed = FloatArray(particleCount) { (0.05f + rnd.nextFloat() * 0.25f) * (if (rnd.nextFloat() < 0.75f) 1f else -1f) }
    val pPhase = FloatArray(particleCount) { rnd.nextFloat() * TAU }

    // ---- travelling pulses along strands ----
    val pulseMax = 14
    val pulseStrand = IntArray(pulseMax)
    val pulsePos = FloatArray(pulseMax) { -1f } // <0 = inactive, 0..1 across the strand
    val pulseSpeed = FloatArray(pulseMax)

    // ---- ripples (ring buffer of start times) ----
    val rippleStart = FloatArray(8) { -10f }
    private var rippleHead = 0

    private var initialized = false

    fun step(dtRaw: Float, input: CoreInput, reduceMotion: Boolean) {
        if (!initialized) {
            // Counters that were already non-zero when the Core appeared are history, not events.
            initialized = true
            lastAckTick = input.acknowledgeTick
            lastWordTick = input.wordTick
        }
        val dt = dtRaw.coerceIn(0f, 0.1f)
        clock += dt
        state = input.state
        val target = StateStyle.forState(input.state)
        // Reduce-motion: state changes apply instantly (no fades), so the Core settles in one frame.
        val k = if (reduceMotion) 1f else 1f - exp(-dt * 5f)
        energy += (target.energy - energy) * k
        turbulence += (target.turbulence - turbulence) * k
        strandAmp += (target.strandAmp - strandAmp) * k
        audioGain += (target.audioGain - audioGain) * k
        spread += (target.spread - spread) * k
        travel += (target.travel - travel) * k
        glow += (target.glow - glow) * k
        lock += (target.lock - lock) * k
        fracture += (target.fracture - fracture) * k
        satellites += (target.satellites - satellites) * k
        val pal = appearance.palette.forState(input.state)
        primary = lerp(primary, pal.primary, k)
        secondary = lerp(secondary, pal.secondary, k)
        accent = lerp(accent, pal.accent, k)

        // ---- audio: measured envelope, or the labelled word-timing fallback ----
        val audioState = input.state == CoreState.LISTENING || input.state == CoreState.SPEAKING
        if (input.wordTick != lastWordTick) {
            lastWordTick = input.wordTick
            if (input.state == CoreState.SPEAKING && !input.audioMeasured) wordEnv = 1f
        }
        wordEnv *= exp(-dt * 9f)
        val targetLevel = when {
            !audioState -> 0f
            input.audioMeasured || input.state == CoreState.LISTENING -> input.audioLevel
            else -> wordEnv * 0.6f
        }
        levelMeasured = audioState && (input.audioMeasured || input.state == CoreState.LISTENING)
        level = follower.update(targetLevel, dt)
        val slowLevel = slow.update(targetLevel, dt)
        if (audioState && level - slowLevel > 0.10f && clock - lastRipple > 0.14f && !reduceMotion) {
            lastRipple = clock
            rippleStart[rippleHead] = clock
            rippleHead = (rippleHead + 1) % rippleStart.size
        }

        // ---- processing pattern follows the real streamed character count ----
        charsPhase += ((input.receivedChars * 0.02f) - charsPhase) * k

        // ---- acknowledge ----
        if (input.acknowledgeTick != lastAckTick) {
            ack = 1f
            lastAckTick = input.acknowledgeTick
        }
        ack = (ack - dt * 0.7f).coerceAtLeast(0f)

        if (reduceMotion) return // ambient motion frozen; colours, levels and state still update

        val motion = appearance.motionSpeed * (1f - lock * 0.92f) * (1f - fracture * 0.9f)
        time += dt * (0.18f + energy * 1.35f) * motion

        for (i in 0 until particleCount) {
            pAngle[i] = (pAngle[i] + pSpeed[i] * dt * (0.25f + energy * 1.6f) * motion) % TAU
        }

        // ---- travelling pulses ----
        val wanted = (travel * pulseMax).toInt()
        var active = 0
        for (i in 0 until pulseMax) if (pulsePos[i] >= 0f) active++
        for (i in 0 until pulseMax) {
            if (pulsePos[i] >= 0f) {
                pulsePos[i] += pulseSpeed[i] * dt * (0.35f + energy) * appearance.motionSpeed
                if (pulsePos[i] > 1f) { pulsePos[i] = -1f; active-- }
            } else if (active < wanted && rnd.nextFloat() < dt * 6f) {
                pulseStrand[i] = rnd.nextInt(appearance.strandCount.coerceAtLeast(1))
                pulsePos[i] = 0f
                pulseSpeed[i] = 0.25f + rnd.nextFloat() * 0.45f
                active++
            }
        }
    }

    /** Ripple progress 0..1 for slot [i], or <0 when inactive. Lifetime 1.1 s. */
    fun ripple(i: Int): Float {
        val p = (clock - rippleStart[i]) / 1.1f
        return if (p in 0f..1f) p else -1f
    }

    /** True when nothing would change on the next frame (lets reduce-motion stop the clock). */
    fun settled(target: CoreState): Boolean {
        val t = StateStyle.forState(target)
        return abs(t.glow - glow) < 0.01f && abs(t.energy - energy) < 0.01f && abs(t.lock - lock) < 0.01f &&
            abs(t.fracture - fracture) < 0.01f && level < 0.01f && ack <= 0f
    }

    companion object {
        const val TAU = (2 * PI).toFloat()
    }
}
