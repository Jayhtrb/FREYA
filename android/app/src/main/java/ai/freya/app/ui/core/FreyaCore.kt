package ai.freya.app.ui.core

import ai.freya.app.domain.CoreState
import ai.freya.app.ui.theme.FreyaColors
import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.Motion
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Whether the centre of the Core shows the FREYA avatar or an abstract orb. */
enum class Presence { AVATAR, CORE }

/**
 * Live signals the Core renders. Everything here is measured, never simulated:
 * [level] is the microphone RMS, [wordTick] increments per word the TTS engine reports,
 * [audible] is true only between the engine's real start and done callbacks.
 */
data class CoreSignals(
    val level: Flow<Float>,
    val wordTick: Long,
    val wordSynced: Boolean,
    val audible: Boolean,
    val receivedChars: Int,
    val acknowledgeTick: Long,
    val speechRate: Float = 1f,
)

/**
 * The FREYA Core: one Canvas, procedural vector drawing, state-driven.
 *
 * Cost model: the frame loop runs only while motion represents a live state, and at ~30 fps
 * for calm states; with reduce-motion it never runs (state is shown by colour and shape only).
 * Animated values are read inside the draw lambda so per-frame work never recomposes.
 */
@Composable
fun FreyaCore(
    state: CoreState,
    signals: CoreSignals,
    presence: Presence,
    modifier: Modifier = Modifier,
) {
    val colors = FreyaTheme.colors
    val reduceMotion = FreyaTheme.motion.reduceMotion

    // ---- per-state targets, blended over Motion.state ms ----
    val primary by animateColorAsState(stateColor(state, colors), tween(Motion.state), label = "corePrimary")
    val secondary by animateColorAsState(stateSecondary(state, colors), tween(Motion.state), label = "coreSecondary")
    val energy by animateFloatAsState(stateEnergy(state), tween(Motion.state, easing = Motion.standard), label = "coreEnergy")
    val listenMix by animateFloatAsState(if (state == CoreState.LISTENING) 1f else 0f, tween(Motion.state), label = "listen")
    val processMix by animateFloatAsState(if (state == CoreState.PROCESSING) 1f else 0f, tween(Motion.state), label = "process")
    val toolMix by animateFloatAsState(if (state == CoreState.TOOL_EXECUTION) 1f else 0f, tween(Motion.state), label = "tool")
    val approvalMix by animateFloatAsState(if (state == CoreState.AWAITING_APPROVAL) 1f else 0f, tween(Motion.state), label = "approval")
    val errorMix by animateFloatAsState(if (state == CoreState.ERROR) 1f else 0f, tween(Motion.state), label = "error")

    // ---- live microphone level (smoothed, read in draw) ----
    val level = remember { Animatable(0f) }
    LaunchedEffect(signals.level) { signals.level.collect { level.animateTo(it, tween(90, easing = LinearEasing)) } }

    // ---- clock: runs only while something live is moving ----
    // Held as a state object and only read in the draw phase / coroutines, so frames never recompose.
    val clock = remember { mutableFloatStateOf(0f) }
    val origin = remember { System.nanoTime() }
    val calm = state == CoreState.IDLE || state == CoreState.AWAITING_APPROVAL
    val runClock = !reduceMotion && state != CoreState.ERROR
    LaunchedEffect(runClock, calm) {
        if (!runClock) return@LaunchedEffect
        val minFrame = if (calm) 33_000_000L else 0L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (now - last >= minFrame) {
                    clock.floatValue = (now - origin) / 1_000_000_000f
                    last = now
                }
            }
        }
    }

    // ---- speech: ripples on each real spoken word ----
    val ripples = remember { mutableStateListOf<Float>() }
    val speech = remember { Animatable(0f) } // mouth / pulse envelope 0..1
    LaunchedEffect(signals.wordTick) {
        if (signals.wordTick == 0L || !signals.audible) return@LaunchedEffect
        pulse(ripples, speech, clock.floatValue, reduceMotion)
    }
    // Documented approximation: engines that do not report word ranges get a cadence-based
    // pulse, but only while audio is actually playing (between onStart and onDone).
    LaunchedEffect(signals.audible, signals.wordSynced) {
        if (!signals.audible || signals.wordSynced) return@LaunchedEffect
        delay(450) // give the engine a chance to report real word ranges first
        while (true) {
            pulse(ripples, speech, clock.floatValue, reduceMotion)
            delay((260 / signals.speechRate.coerceIn(0.5f, 2f)).toLong())
        }
    }
    LaunchedEffect(signals.audible) {
        if (!signals.audible) speech.animateTo(0f, tween(160))
    }

    // ---- acknowledgement (task completed) ----
    val ack = remember { Animatable(0f) }
    LaunchedEffect(signals.acknowledgeTick) {
        if (signals.acknowledgeTick == 0L) return@LaunchedEffect
        ack.snapTo(1f)
        ack.animateTo(0f, tween(1600, easing = FastOutSlowInEasing))
    }

    // ---- avatar blink (expression only; disabled with reduce-motion) ----
    val blink = remember { Animatable(0f) }
    LaunchedEffect(presence, reduceMotion) {
        if (presence != Presence.AVATAR || reduceMotion) return@LaunchedEffect
        while (true) {
            delay(3200L + (origin % 2600))
            blink.animateTo(1f, tween(70))
            blink.animateTo(0f, tween(110))
        }
    }

    val chars = signals.receivedChars
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "FREYA: ${state.label}"
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        val t = clock.floatValue
        val c = center
        val outer = size.minDimension / 2f * 0.86f
        val orb = outer * 0.50f
        val stroke = max(1f, size.minDimension / 260f)

        drawHalo(c, outer, primary, energy, t, approvalMix, errorMix)
        drawRings(c, outer, orb, primary, secondary, energy, t, stroke, errorMix, approvalMix)
        if (listenMix > 0.01f) drawListening(c, orb, outer, secondary, level.value, t, listenMix, stroke)
        if (processMix > 0.01f) drawProcessing(c, outer, primary, secondary, t, chars, processMix, stroke)
        if (toolMix > 0.01f) drawTool(c, outer, secondary, t, toolMix, stroke)
        drawRipples(c, orb, outer, secondary, ripples, t, stroke)

        when (presence) {
            Presence.CORE -> drawOrb(c, orb, primary, secondary, colors, t, energy, speech.value, ack.value)
            Presence.AVATAR -> {
                drawOrb(c, orb, primary, secondary, colors, t, energy * 0.4f, 0f, 0f, dim = true)
                drawAvatar(
                    center = c, r = orb * 0.92f, colors = colors, accent = secondary, t = t,
                    expression = Expression(
                        listening = listenMix,
                        thinking = max(processMix, toolMix),
                        mouth = speech.value,
                        acknowledge = ack.value,
                        concern = max(errorMix, approvalMix * 0.5f),
                        blink = blink.value,
                    ),
                    stroke = stroke,
                )
            }
        }
    }
}

private suspend fun pulse(
    ripples: MutableList<Float>,
    speech: Animatable<Float, AnimationVector1D>,
    now: Float,
    reduceMotion: Boolean,
) {
    if (!reduceMotion) {
        ripples.removeAll { now - it > 1.1f }
        if (ripples.size < 6) ripples.add(now)
    }
    speech.animateTo(1f, tween(70))
    speech.animateTo(0.12f, tween(170))
}

// ------------------------------------------------------------------ state tables

private fun stateColor(s: CoreState, c: FreyaColors): Color = when (s) {
    CoreState.IDLE -> c.violet
    CoreState.LISTENING -> c.cyan
    CoreState.PROCESSING -> c.violet
    CoreState.TOOL_EXECUTION -> c.ice
    CoreState.SPEAKING -> c.cyan
    CoreState.AWAITING_APPROVAL -> c.approval
    CoreState.ERROR -> c.error
}

private fun stateSecondary(s: CoreState, c: FreyaColors): Color = when (s) {
    CoreState.IDLE -> c.cyan
    CoreState.LISTENING -> c.ice
    CoreState.PROCESSING -> c.ice
    CoreState.TOOL_EXECUTION -> c.cyan
    CoreState.SPEAKING -> c.violet
    CoreState.AWAITING_APPROVAL -> c.approval
    CoreState.ERROR -> c.error
}

private fun stateEnergy(s: CoreState): Float = when (s) {
    CoreState.IDLE -> 0.18f
    CoreState.LISTENING -> 0.55f
    CoreState.PROCESSING -> 1.0f
    CoreState.TOOL_EXECUTION -> 0.8f
    CoreState.SPEAKING -> 0.5f
    CoreState.AWAITING_APPROVAL -> 0.0f
    CoreState.ERROR -> 0.0f
}

// ------------------------------------------------------------------ layers

private fun DrawScope.drawHalo(c: Offset, outer: Float, color: Color, energy: Float, t: Float, approval: Float, error: Float) {
    val breath = 0.5f + 0.5f * sin(t * (2f * PI.toFloat() / 6.5f))
    val attention = approval * (0.5f + 0.5f * sin(t * 2.2f))
    val alpha = 0.16f + 0.10f * breath * (1f - error) + 0.10f * energy + 0.12f * attention
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = outer * 1.25f),
        radius = outer * 1.25f,
        center = c,
    )
}

private fun DrawScope.drawRings(
    c: Offset, outer: Float, orb: Float, primary: Color, secondary: Color,
    energy: Float, t: Float, stroke: Float, error: Float, approval: Float,
) {
    // Static hairline orbit.
    drawCircle(primary.copy(alpha = 0.22f), radius = outer, center = c, style = Stroke(stroke))
    drawCircle(secondary.copy(alpha = 0.10f), radius = outer * 0.78f, center = c, style = Stroke(stroke))

    // Rotating arcs. Speed follows energy; an error breaks the ring into a static dashed state.
    val spin = t * (8f + 70f * energy)
    val arcStroke = Stroke(stroke * 2.2f, cap = StrokeCap.Round)
    val tl = Offset(c.x - outer, c.y - outer)
    val sz = Size(outer * 2, outer * 2)
    if (error > 0.5f) {
        for (i in 0 until 8) {
            drawArc(primary.copy(alpha = 0.7f), startAngle = i * 45f + 6f, sweepAngle = 26f, useCenter = false, topLeft = tl, size = sz, style = arcStroke)
        }
        return
    }
    rotate(spin, c) {
        drawArc(primary.copy(alpha = 0.85f), -90f, 64f + 30f * energy, false, tl, sz, style = arcStroke)
        drawArc(secondary.copy(alpha = 0.55f), 120f, 34f, false, tl, sz, style = arcStroke)
    }
    val inner = outer * 0.78f
    rotate(-spin * 0.6f, c) {
        drawArc(
            secondary.copy(alpha = 0.45f), 30f, 90f, false,
            Offset(c.x - inner, c.y - inner), Size(inner * 2, inner * 2), style = Stroke(stroke * 1.4f, cap = StrokeCap.Round),
        )
    }
    if (approval > 0.01f) {
        // A closed, steady double ring: motion stops, attention is requested.
        drawCircle(primary.copy(alpha = 0.75f * approval), radius = outer * 1.04f, center = c, style = Stroke(stroke * 2f))
        drawCircle(primary.copy(alpha = 0.35f * approval), radius = outer * 1.10f, center = c, style = Stroke(stroke))
    }
}

/** LISTENING: radial spectrum whose height is the real microphone level. Silent mic = flat. */
private fun DrawScope.drawListening(c: Offset, orb: Float, outer: Float, color: Color, level: Float, t: Float, mix: Float, stroke: Float) {
    val bars = 72
    val base = orb * 1.08f
    val maxLen = (outer * 0.74f - base).coerceAtLeast(4f)
    for (i in 0 until bars) {
        val a = (i.toFloat() / bars) * 2f * PI.toFloat()
        val variation = 0.55f + 0.45f * sin(i * 0.9f + t * 7f) * cos(i * 0.37f - t * 4f)
        val len = 2f + maxLen * level * variation * mix
        val dir = Offset(cos(a), sin(a))
        drawLine(
            color = color.copy(alpha = (0.35f + 0.6f * level) * mix),
            start = c + dir * base,
            end = c + dir * (base + len),
            strokeWidth = stroke * 1.6f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * PROCESSING: three counter-rotating segmented arcs. Before the first token they chase;
 * as tokens stream in, the segment pattern shifts with the real character count.
 */
private fun DrawScope.drawProcessing(c: Offset, outer: Float, primary: Color, secondary: Color, t: Float, chars: Int, mix: Float, stroke: Float) {
    val radii = floatArrayOf(0.90f, 0.66f, 1.12f)
    val phaseShift = chars * 0.035f
    radii.forEachIndexed { k, rf ->
        val r = outer * rf
        val dir = if (k % 2 == 0) 1f else -1f
        val segs = 3 + k
        val sweep = 18f + 22f * (0.5f + 0.5f * sin(t * (1.6f + k * 0.5f) + phaseShift + k))
        rotate(dir * t * (90f + 40f * k) + phaseShift * 40f, c) {
            for (s in 0 until segs) {
                drawArc(
                    color = (if (k == 1) secondary else primary).copy(alpha = 0.65f * mix),
                    startAngle = s * (360f / segs),
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(stroke * (1.2f + 0.6f * (2 - k).coerceAtLeast(0)), cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** TOOL_EXECUTION: a satellite orbiting with a trail plus a stepping four-tick sequencer. */
private fun DrawScope.drawTool(c: Offset, outer: Float, color: Color, t: Float, mix: Float, stroke: Float) {
    val r = outer * 1.0f
    val a = t * 2.4f
    val sat = c + Offset(cos(a), sin(a)) * r
    drawArc(
        color = color.copy(alpha = 0.6f * mix),
        startAngle = Math.toDegrees(a.toDouble()).toFloat() - 70f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(stroke * 2.4f, cap = StrokeCap.Round),
    )
    drawCircle(color.copy(alpha = mix), radius = stroke * 4.5f, center = sat)
    drawCircle(color.copy(alpha = 0.25f * mix), radius = stroke * 10f, center = sat)
    val step = (t * 2f).toInt() % 4
    for (i in 0 until 4) {
        val ang = i * (PI.toFloat() / 2f) - PI.toFloat() / 4f
        val p = c + Offset(cos(ang), sin(ang)) * (outer * 0.66f)
        drawCircle(color.copy(alpha = (if (i == step) 0.95f else 0.25f) * mix), radius = stroke * 2.6f, center = p)
    }
}

private fun DrawScope.drawRipples(c: Offset, orb: Float, outer: Float, color: Color, ripples: List<Float>, t: Float, stroke: Float) {
    ripples.forEach { start ->
        val p = ((t - start) / 1.0f).coerceIn(0f, 1f)
        if (p in 0f..0.999f) {
            val r = orb + (outer * 1.15f - orb) * p
            drawCircle(color.copy(alpha = 0.55f * (1f - p)), radius = r, center = c, style = Stroke(stroke * (2.2f - 1.4f * p)))
        }
    }
}

private fun DrawScope.drawOrb(
    c: Offset, r: Float, primary: Color, secondary: Color, colors: FreyaColors,
    t: Float, energy: Float, speech: Float, ack: Float, dim: Boolean = false,
) {
    val swell = 1f + 0.06f * speech + 0.04f * ack
    val rr = r * swell
    val core = if (colors.isDark) Color(0xFF0A0F22) else Color(0xFFE9EEFC)
    drawCircle(core, radius = rr, center = c)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(secondary.copy(alpha = if (dim) 0.22f else 0.55f), primary.copy(alpha = if (dim) 0.12f else 0.30f), Color.Transparent),
            center = c + Offset(-rr * 0.25f, -rr * 0.30f),
            radius = rr * 1.25f,
        ),
        radius = rr,
        center = c,
    )
    // Slow internal highlight sweep (only moves when the clock runs).
    val a = t * (0.4f + 1.6f * energy)
    val hl = c + Offset(cos(a), sin(a)) * (rr * 0.42f)
    drawCircle(
        brush = Brush.radialGradient(listOf(colors.ice.copy(alpha = if (dim) 0.10f else 0.28f), Color.Transparent), center = hl, radius = rr * 0.6f),
        radius = rr * 0.6f,
        center = hl,
    )
    drawCircle(primary.copy(alpha = 0.55f), radius = rr, center = c, style = Stroke(max(1f, rr / 70f)))
    if (ack > 0.01f) {
        drawCircle(colors.success.copy(alpha = 0.6f * ack), radius = rr * (1.05f + 0.25f * (1f - ack)), center = c, style = Stroke(max(1.5f, rr / 40f)))
    }
}
