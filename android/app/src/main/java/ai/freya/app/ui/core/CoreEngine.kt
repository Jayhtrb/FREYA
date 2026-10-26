package ai.freya.app.ui.core

import ai.freya.app.domain.CoreState
import ai.freya.app.voice.AudioEnvelope
import ai.freya.app.voice.EnvelopeFollower
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** What the Core is told each frame. The renderer never owns audio, permissions or network. */
class CoreInput(
    val state: CoreState,
    /** Measured level 0..1 for the active direction (mic while listening, voice while speaking). */
    val audioLevel: Float = 0f,
    /** false when the active level is not measured audio (timing-only fallback or none). */
    val audioMeasured: Boolean = false,
    /** Measured spectrum of FREYA's voice ([AudioEnvelope.BANDS] values), or null when unavailable. */
    val audioBands: FloatArray? = null,
    /** Spoken-word counter from TTS timing; drives the clearly-labelled fallback only. */
    val wordTick: Long = 0,
    /** Characters streamed so far in the current reply (shifts the PROCESSING pattern). */
    val receivedChars: Int = 0,
    /** Increments when a task completes successfully. */
    val acknowledgeTick: Long = 0,
)

/**
 * Simulation for the FREYA energy core: a 3D orb of luminous particles with flowing light
 * filaments, a horizontal energy field whose waves are frequency bands, and onset ripples that
 * travel across the particle surface. Pure Kotlin (no drawing, no Android) — [step] advances it
 * once per frame and writes projected positions into flat arrays the renderer batches.
 *
 * Coordinates: everything is in units of the orb radius, centred on the orb, already rotated
 * and perspective-projected. `sx/sy` are screen offsets (y down), `depth` is +toward the viewer.
 */
class CoreEngine(
    private val appearance: CoreAppearance = CoreAppearance.Default,
    seed: Int = 7,
) {
    private val rnd = Random(seed)

    // ---- clocks ----
    /** Ambient time; advances faster with energy, frozen with reduce-motion. */
    var time = 0f; private set
    /** Real elapsed seconds (ripples). */
    var clock = 0f; private set

    // ---- eased style ----
    var state = CoreState.IDLE; private set
    private val s0 = StateStyle.forState(CoreState.IDLE)
    var energy = s0.energy; private set
    var turbulence = s0.turbulence; private set
    var swirl = s0.swirl; private set
    var audioGain = s0.audioGain; private set
    var breath = s0.breath; private set
    var contraction = s0.contraction; private set
    var scatter = s0.scatter; private set
    var orbit = s0.orbit; private set
    var field = s0.field; private set
    var glow = s0.glow; private set
    var spin = s0.spin; private set
    var primary: Color = appearance.palette.idle.primary; private set
    var secondary: Color = appearance.palette.idle.secondary; private set
    var accent: Color = appearance.palette.idle.accent; private set
    var hot: Color = appearance.palette.idle.hot; private set

    // ---- audio ----
    private val follower = EnvelopeFollower(attackPerSec = 30f, releasePerSec = 8f)
    private val slow = EnvelopeFollower(attackPerSec = 4f, releasePerSec = 3f)
    /** Smoothed level actually used for geometry (0 outside listening/speaking). */
    var level = 0f; private set
    /** True when [level] is measured audio rather than the timing fallback. */
    var levelMeasured = false; private set
    /** Smoothed per-band energy driving the field waves and the orb's latitude bands. */
    val bands = FloatArray(AudioEnvelope.BANDS)
    /** True when [bands] come from the measured spectrum of the voice being played. */
    var bandsMeasured = false; private set
    private val bandFollowers = Array(AudioEnvelope.BANDS) { EnvelopeFollower(attackPerSec = 26f, releasePerSec = 7f) }
    private var wordEnv = 0f
    private var lastWordTick = 0L
    private var lastRipple = -10f

    // ---- processing pattern / acknowledge ----
    var charsPhase = 0f; private set
    var ack = 0f; private set
    private var lastAckTick = 0L

    // ---- orientation ----
    var rotY = 0.6f; private set
    var tiltX = 0.42f; private set

    // ---- particles (struct-of-arrays, no per-frame allocation) ----
    val particleCount = appearance.particleCount.coerceIn(32, 6000)
    private val bx = FloatArray(particleCount)
    private val by = FloatArray(particleCount)
    private val bz = FloatArray(particleCount)
    private val shell = FloatArray(particleCount)
    private val phase = FloatArray(particleCount) { rnd.nextFloat() * TAU }
    private val jx = FloatArray(particleCount) { rnd.nextFloat() * 2f - 1f }
    private val jy = FloatArray(particleCount) { rnd.nextFloat() * 2f - 1f }
    private val jz = FloatArray(particleCount) { rnd.nextFloat() * 2f - 1f }
    private val bandOf = IntArray(particleCount)
    /** 0 = orb, 1 = magenta accent spark, 2 = joins the orbit stream during tasks. */
    val kind = ByteArray(particleCount)
    val sx = FloatArray(particleCount)
    val sy = FloatArray(particleCount)
    val depth = FloatArray(particleCount)
    /** 0..1: how energised (displaced) the particle is this frame; picks the hot colour. */
    val heat = FloatArray(particleCount)
    /** Previous-frame projected positions, for motion trails. */
    val psx = FloatArray(particleCount)
    val psy = FloatArray(particleCount)

    // ---- filaments: flowing arcs of light over the orb ----
    val filamentCount = appearance.filamentCount.coerceIn(0, 16)
    val filamentSamples = 64
    private val fAz = FloatArray(filamentCount) { rnd.nextFloat() * TAU }
    private val fEl = FloatArray(filamentCount) { (rnd.nextFloat() - 0.5f) * 2.4f }
    private val fH = FloatArray(filamentCount) { (rnd.nextFloat() - 0.5f) * 0.9f }
    private val fLen = FloatArray(filamentCount) { 0.35f + rnd.nextFloat() * 0.4f }
    private val fSpeed = FloatArray(filamentCount) { (0.5f + rnd.nextFloat() * 0.8f) * (if (rnd.nextBoolean()) 1f else -1f) }
    private val fHead = FloatArray(filamentCount) { rnd.nextFloat() * TAU }
    val fx = FloatArray(filamentCount * filamentSamples)
    val fy = FloatArray(filamentCount * filamentSamples)
    val fz = FloatArray(filamentCount * filamentSamples)

    // ---- horizontal field ----
    val waveCount = appearance.waveCount.coerceIn(0, 16)
    val streamCount = appearance.streamCount.coerceIn(0, 2000)
    /** Stream position along the field in half-widths (-1..1), its wave lane and speed. */
    val streamU = FloatArray(streamCount) { (rnd.nextFloat() * 2f - 1f) }
    val streamLane = IntArray(streamCount) { if (waveCount == 0) 0 else rnd.nextInt(waveCount) }
    private val streamSpeed = FloatArray(streamCount) { 0.25f + rnd.nextFloat() * 0.6f }
    val streamJitter = FloatArray(streamCount) { (rnd.nextFloat() - 0.5f) * 0.05f }

    // ---- ripples (ring buffer of start times and strengths) ----
    val rippleStart = FloatArray(6) { -10f }
    val rippleStrength = FloatArray(6)
    private var rippleHead = 0

    private var initialized = false

    init {
        // Fibonacci sphere: even coverage without clumping. ~72% sit on the shell, the rest fill
        // the volume so the orb reads as having depth rather than as a hollow ball.
        val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
        for (i in 0 until particleCount) {
            val y = 1f - 2f * (i + 0.5f) / particleCount
            val r = sqrt((1f - y * y).coerceAtLeast(0f))
            val a = golden * i
            bx[i] = cos(a) * r
            by[i] = y
            bz[i] = sin(a) * r
            val onShell = rnd.nextFloat() < 0.72f
            shell[i] = if (onShell) 0.97f + rnd.nextFloat() * 0.06f else 0.35f + rnd.nextFloat() * 0.58f
            // Equator carries the low bands, the poles the high ones.
            bandOf[i] = (abs(y) * (AudioEnvelope.BANDS - 1) + 0.5f).toInt().coerceIn(0, AudioEnvelope.BANDS - 1)
            val roll = rnd.nextFloat()
            kind[i] = when {
                roll < 0.07f -> 1
                roll < 0.25f -> 2
                else -> 0
            }
        }
    }

    fun step(dtRaw: Float, input: CoreInput, reduceMotion: Boolean) {
        val first = !initialized
        if (first) {
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
        val k = if (reduceMotion) 1f else 1f - exp(-dt * 4f)
        energy += (target.energy - energy) * k
        turbulence += (target.turbulence - turbulence) * k
        swirl += (target.swirl - swirl) * k
        audioGain += (target.audioGain - audioGain) * k
        breath += (target.breath - breath) * k
        contraction += (target.contraction - contraction) * k
        scatter += (target.scatter - scatter) * k
        orbit += (target.orbit - orbit) * k
        field += (target.field - field) * k
        glow += (target.glow - glow) * k
        spin += (target.spin - spin) * k
        val pal = appearance.palette.forState(input.state)
        primary = lerp(primary, pal.primary, k)
        secondary = lerp(secondary, pal.secondary, k)
        accent = lerp(accent, pal.accent, k)
        hot = lerp(hot, pal.hot, k)

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
        if (audioState && level - slowLevel > 0.10f && clock - lastRipple > 0.16f && !reduceMotion) {
            lastRipple = clock
            addRipple(0.55f + 0.45f * level)
        }

        // ---- bands: the voice's measured spectrum, else the level spread over a speech profile ----
        val measuredBands = input.audioBands
        bandsMeasured = input.state == CoreState.SPEAKING && input.audioMeasured && measuredBands != null &&
            measuredBands.size >= AudioEnvelope.BANDS
        for (b in 0 until AudioEnvelope.BANDS) {
            val t = when {
                !audioState -> 0f
                bandsMeasured -> measuredBands!![b]
                // The mic only reports a level, so distribute it with a fixed speech-like tilt.
                else -> targetLevel * SPEECH_PROFILE[b]
            }
            bands[b] = bandFollowers[b].update(t, dt)
        }

        // ---- processing pattern follows the real streamed character count ----
        charsPhase += ((input.receivedChars * 0.015f) - charsPhase) * k

        // ---- acknowledge: one bright wave across the orb ----
        if (input.acknowledgeTick != lastAckTick) {
            ack = 1f
            lastAckTick = input.acknowledgeTick
            if (!reduceMotion) addRipple(1f)
        }
        ack = (ack - dt * 0.8f).coerceAtLeast(0f)

        if (!reduceMotion) {
            val motion = appearance.motionSpeed * (1f - contraction * 4.5f).coerceIn(0.05f, 1f) * (1f - scatter * 0.85f)
            time += dt * (0.25f + energy * 1.4f) * motion
            rotY = (rotY + dt * (0.10f + energy * 0.75f) * motion) % TAU
            tiltX = 0.42f + 0.07f * sin(time * 0.37f)
            for (f in 0 until filamentCount) {
                fHead[f] = (fHead[f] + fSpeed[f] * dt * (0.4f + energy * 2.2f + level) * motion) % TAU
            }
            val flow = (0.12f + energy * 0.5f + level * 0.9f) * motion
            for (i in 0 until streamCount) {
                val dir = if (streamU[i] >= 0f) 1f else -1f
                streamU[i] += dir * streamSpeed[i] * dt * flow
                if (abs(streamU[i]) > 1.05f) {
                    // Energy is emitted from the orb and flows outward along the field.
                    streamU[i] = (if (rnd.nextBoolean()) 1f else -1f) * rnd.nextFloat() * 0.18f
                    streamLane[i] = if (waveCount == 0) 0 else rnd.nextInt(waveCount)
                }
            }
        }

        computeParticles(trails = !reduceMotion && !first)
        computeFilaments()
    }

    private fun addRipple(strength: Float) {
        rippleStart[rippleHead] = clock
        rippleStrength[rippleHead] = strength
        rippleHead = (rippleHead + 1) % rippleStart.size
    }

    /** Ripple progress 0..1 for slot [i], or <0 when inactive. Lifetime 1.2 s. */
    fun ripple(i: Int): Float {
        val p = (clock - rippleStart[i]) / 1.2f
        return if (p in 0f..1f) p else -1f
    }

    private fun computeParticles(trails: Boolean) {
        if (trails) {
            sx.copyInto(psx); sy.copyInto(psy)
        }
        val t = time
        val ct = cos(tiltX)
        val st = sin(tiltX)
        val radiusScale = 1f - contraction
        val gain = audioGain * appearance.waveAmplitude
        val breathe = breath * 0.035f * sin(t * 2.2f)
        // Active ripples, gathered once per frame.
        var nRip = 0
        for (r in rippleStart.indices) if (ripple(r) >= 0f) nRip++
        for (i in 0 until particleCount) {
            val x0 = bx[i]; val y0 = by[i]; val z0 = bz[i]
            val ph = phase[i]
            // Vortex: processing twists latitudes against each other into spiral bands.
            val ang = rotY + swirl * (y0 * 2.4f + charsPhase * 0.5f)
            val ca = cos(ang); val sa = sin(ang)
            val xr = x0 * ca + z0 * sa
            val zr = -x0 * sa + z0 * ca
            val ux = xr
            val uy = y0 * ct - zr * st
            val uz = y0 * st + zr * ct

            val noise = 0.5f * sin(2.1f * x0 + 1.3f * y0 + t * 1.1f + ph * 0.15f) +
                0.35f * sin(3.3f * y0 - 2.2f * z0 + t * 1.7f + 1.3f) +
                0.25f * sin(4.7f * z0 + 3.1f * x0 - t * 2.3f + 2.1f)
            val band = bands[bandOf[i]]
            val audio = gain * (level * 0.14f + band * 0.22f) * (0.7f + 0.3f * sin(ph * 3f + t * 9f))
            var bump = 0f
            if (nRip > 0) {
                val theta = acos(uz.coerceIn(-1f, 1f))
                for (r in rippleStart.indices) {
                    val p = ripple(r)
                    if (p < 0f) continue
                    val d = (theta - p * 2.8f) / 0.24f
                    val fade = (1f - p)
                    bump += rippleStrength[r] * fade * fade * 0.11f * exp(-d * d)
                }
            }
            val disp = turbulence * noise + audio + breathe + bump
            val r = shell[i] * (1f + disp) * radiusScale
            var x = ux * r
            var y = uy * r
            var z = uz * r

            if (scatter > 0.001f) {
                val s = scatter * (0.10f + 0.10f * sin(ph + t * 4f))
                x += jx[i] * s; y += jy[i] * s + scatter * 0.04f; z += jz[i] * s
            }
            if (orbit > 0.001f && kind[i] == 2.toByte()) {
                // Task: these particles peel off into a tilted stream circling the orb.
                val a = ph + t * (1.6f + abs(jx[i]))
                val rad = 1.30f + 0.10f * jy[i] + 0.04f * sin(a * 3f)
                val ox = cos(a) * rad
                val oz0 = sin(a) * rad
                val oy0 = 0.10f * jz[i]
                val oy = oy0 * 0.88f - oz0 * 0.47f
                val oz = oy0 * 0.47f + oz0 * 0.88f
                val w = orbit * smooth01(((jx[i] + 1f) * 0.5f) * 0.5f + 0.5f)
                x += (ox - x) * w; y += (oy - y) * w; z += (oz - z) * w
            }
            val p = PERSPECTIVE / (PERSPECTIVE - z)
            sx[i] = x * p
            sy[i] = -y * p
            depth[i] = z
            heat[i] = ((disp - turbulence * 0.3f) * 5f + bump * 6f).coerceIn(0f, 1f)
        }
        if (!trails) {
            sx.copyInto(psx); sy.copyInto(psy)
        }
    }

    private fun computeFilaments() {
        if (filamentCount == 0) return
        val t = time
        val ct = cos(tiltX)
        val st = sin(tiltX)
        val radiusScale = 1f - contraction
        val gain = audioGain * appearance.waveAmplitude
        val lenScale = 1f - scatter * 0.7f
        for (f in 0 until filamentCount) {
            // Plane normal precesses; processing spins them fast so they braid around the orb.
            val az = fAz[f] + t * spin * (0.35f + 0.1f * f)
            val el = fEl[f] + 0.25f * sin(t * 0.3f + f)
            val nx = cos(el) * cos(az); val ny = sin(el); val nz = cos(el) * sin(az)
            // Basis in the plane: e1 = normalize(n × up'), e2 = n × e1.
            var e1x = nz; var e1y = 0f; var e1z = -nx
            var len = sqrt(e1x * e1x + e1z * e1z)
            if (len < 1e-4f) { e1x = 1f; e1z = 0f; len = 1f }
            e1x /= len; e1z /= len
            val e2x = ny * e1z - nz * e1y
            val e2y = nz * e1x - nx * e1z
            val e2z = nx * e1y - ny * e1x
            val h = fH[f]
            val rc = sqrt(1f - h * h)
            val span = fLen[f] * TAU * lenScale
            val band = bands[f % AudioEnvelope.BANDS]
            for (j in 0 until filamentSamples) {
                val u = j / (filamentSamples - 1f)
                val th = fHead[f] - span * (1f - u)
                val c = cos(th); val s = sin(th)
                val px = nx * h + (e1x * c + e2x * s) * rc
                val py = ny * h + (e1y * c + e2y * s) * rc
                val pz = nz * h + (e1z * c + e2z * s) * rc
                val rm = (1.06f + 0.05f * sin(th * 3f + t * 1.7f + f) + turbulence * 0.5f * sin(th * 5f - t * 2.6f) +
                    gain * (level * 0.08f + band * 0.14f) * sin(th * (4f + f) - t * 7f)) * radiusScale
                val x0 = px * rm; val y0 = py * rm; val z0 = pz * rm
                val ca = cos(rotY); val sa = sin(rotY)
                val xr = x0 * ca + z0 * sa
                val zr = -x0 * sa + z0 * ca
                val yy = y0 * ct - zr * st
                val zz = y0 * st + zr * ct
                val p = PERSPECTIVE / (PERSPECTIVE - zz)
                val idx = f * filamentSamples + j
                fx[idx] = xr * p
                fy[idx] = -yy * p
                fz[idx] = zz
            }
        }
    }

    /** Amplitude (in orb radii) of horizontal field wave [lane]. */
    fun waveAmp(lane: Int): Float {
        val b = bands[(lane * AudioEnvelope.BANDS / waveCount.coerceAtLeast(1)).coerceIn(0, AudioEnvelope.BANDS - 1)]
        val base = field * (0.55f + 0.45f * ((lane * 37) % 5) / 4f) * (1f + breath * 0.15f * sin(time * 2.2f + lane))
        return base + audioGain * appearance.waveAmplitude * (level * 0.30f + b * 0.55f) * (1f - contraction)
    }

    /**
     * Vertical offset (orb radii, y down) of field wave [lane] at [u] half-widths from the centre.
     * Low bands are long, slow swells; high bands are short ripples. Processing raises the
     * frequency and phase-locks the lanes into a braid.
     */
    fun waveY(lane: Int, u: Float): Float {
        val env = exp(-u * u * 2.1f)
        val freq = 1.2f + lane * 0.55f + swirl * 1.4f
        val w = 1.0f + lane * 0.35f
        val dir = if (lane % 2 == 0) 1f else -1f
        val ph = lane * (1.7f - swirl * 1.2f) + charsPhase * swirl
        return -waveAmp(lane) * env * sin(u * freq * PI.toFloat() + dir * time * w * 2f + ph)
    }

    /** True when nothing would change on the next frame (lets reduce-motion stop the clock). */
    fun settled(target: CoreState): Boolean {
        val t = StateStyle.forState(target)
        return abs(t.glow - glow) < 0.01f && abs(t.energy - energy) < 0.01f && abs(t.contraction - contraction) < 0.01f &&
            abs(t.scatter - scatter) < 0.01f && level < 0.01f && ack <= 0f && bands.all { it < 0.01f }
    }

    companion object {
        const val TAU = (2 * PI).toFloat()
        /** Camera distance in orb radii; lower = stronger 3D perspective. */
        const val PERSPECTIVE = 3.4f
        /** How a bare level is spread across bands when no spectrum exists (mic, timing fallback). */
        val SPEECH_PROFILE = floatArrayOf(0.85f, 1f, 0.9f, 0.75f, 0.6f, 0.45f, 0.34f, 0.26f)

        private fun smooth01(x: Float): Float {
            val c = x.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }
    }
}
