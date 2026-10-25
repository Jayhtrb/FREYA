package ai.freya.app.ui.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Size-dependent geometry that never changes between frames (tick rings, dot rings, the orb
 * clip). Rebuilt only when the canvas size changes; strand paths are reused and reset each frame.
 */
class CoreCache {
    var size: Size = Size.Zero; private set
    val outerTicks = Path()
    val innerTicks = Path()
    val dotRing = Path()
    val orbClip = Path()
    val strandPaths = Array(10) { Path() }
    val scratch = Path()

    fun ensure(size: Size, geo: CoreGeometry) {
        if (size == this.size) return
        this.size = size
        buildTicks(outerTicks, geo.c, geo.r * 0.88f, 144, geo.r * 0.022f, geo.r * 0.05f, every = 12)
        buildTicks(innerTicks, geo.c, geo.orb * 1.12f, 90, geo.r * 0.012f, geo.r * 0.024f, every = 15)
        dotRing.reset()
        val dots = 72
        val dr = geo.orb * 1.27f
        for (i in 0 until dots) {
            val a = i * (2 * PI / dots).toFloat()
            val x = geo.c.x + cos(a) * dr
            val y = geo.c.y + sin(a) * dr
            val s = max(1f, geo.r * 0.004f)
            dotRing.addOval(androidx.compose.ui.geometry.Rect(x - s, y - s, x + s, y + s))
        }
        orbClip.reset()
        orbClip.addOval(androidx.compose.ui.geometry.Rect(geo.c.x - geo.orb, geo.c.y - geo.orb, geo.c.x + geo.orb, geo.c.y + geo.orb))
    }

    private fun buildTicks(p: Path, c: Offset, radius: Float, count: Int, short: Float, long: Float, every: Int) {
        p.reset()
        for (i in 0 until count) {
            val a = i * (2 * PI / count).toFloat()
            val len = if (i % every == 0) long else short
            val ca = cos(a)
            val sa = sin(a)
            p.moveTo(c.x + ca * radius, c.y + sa * radius)
            p.lineTo(c.x + ca * (radius - len), c.y + sa * (radius - len))
        }
    }
}

/** Layout of the core inside its canvas (responsive: everything derives from size). */
data class CoreGeometry(val c: Offset, val r: Float, val orb: Float, val hair: Float, val width: Float, val height: Float) {
    companion object {
        fun of(size: Size): CoreGeometry {
            val r = min(size.width * 0.5f, size.height * 0.5f) * 0.86f
            return CoreGeometry(
                c = Offset(size.width / 2f, size.height / 2f),
                r = r,
                orb = r * 0.56f,
                hair = max(1f, r / 230f),
                width = size.width,
                height = size.height,
            )
        }
    }
}

/**
 * Draws the full Core from the engine's current values. Called inside Canvas' draw phase only.
 * [additive] uses BlendMode.Plus for light (dark theme); light theme uses normal blending.
 */
internal fun DrawScope.drawFreyaCore(
    e: CoreEngine,
    cache: CoreCache,
    appearance: CoreAppearance,
    presence: Presence,
    additive: Boolean,
    compact: Boolean,
) {
    val geo = CoreGeometry.of(size)
    cache.ensure(size, geo)
    val light = if (additive) BlendMode.Plus else BlendMode.SrcOver
    val glow = e.glow * appearance.glow
    val th = geo.hair * appearance.ringThickness

    drawNebula(e, geo, appearance, light, glow)
    drawParticles(e, geo, front = false, light = light, glow = glow)
    if (!compact) drawCrosshair(e, geo, light, glow, th)
    drawRings(e, geo, cache, light, glow, th, compact)
    if (!compact) drawSpectrum(e, geo, appearance, light, glow, th)
    drawStrands(e, geo, cache, appearance, light, glow, th)
    drawOrb(e, geo, cache, appearance, presence, light, glow, th)
    drawParticles(e, geo, front = true, light = light, glow = glow)
    drawPulses(e, geo, appearance, light, glow, th)
    drawRipples(e, geo, light, glow, th)
    drawOverlays(e, geo, light, glow, th)
}

// ------------------------------------------------------------------------------------ layers

private fun DrawScope.drawNebula(e: CoreEngine, g: CoreGeometry, a: CoreAppearance, light: BlendMode, glow: Float) {
    val t = e.time
    val blobs = arrayOf(
        Triple(Offset(-0.38f + 0.05f * sin(t * 0.11f), -0.22f), e.accent, 1.15f),
        Triple(Offset(0.42f, 0.18f + 0.05f * cos(t * 0.09f)), e.primary, 1.05f),
        Triple(Offset(0.08f, 0.62f), a.palette.haze, 0.9f),
    )
    blobs.forEach { (o, col, rf) ->
        val center = g.c + Offset(o.x * g.r, o.y * g.r)
        val radius = g.r * rf
        drawCircle(
            brush = Brush.radialGradient(listOf(col.copy(alpha = 0.16f * glow), Color.Transparent), center = center, radius = radius),
            radius = radius, center = center, blendMode = light,
        )
    }
    // Soft stage light behind the orb.
    drawCircle(
        brush = Brush.radialGradient(listOf(e.secondary.copy(alpha = 0.18f * glow + 0.15f * e.level), Color.Transparent), center = g.c, radius = g.r * 1.15f),
        radius = g.r * 1.15f, center = g.c, blendMode = light,
    )
}

private fun DrawScope.drawParticles(e: CoreEngine, g: CoreGeometry, front: Boolean, light: BlendMode, glow: Float) {
    val t = e.time
    val push = 1f + e.spread * e.level * 0.45f
    for (i in 0 until e.particleCount) {
        val z = e.pDepth[i]
        if ((z > 0.55f) != front) continue
        val wobble = sin(t * 0.7f + e.pPhase[i]) * 0.04f
        val rr = g.r * (e.pRadius[i] + wobble) * (1f + (push - 1f) * (0.4f + z))
        val a = e.pAngle[i]
        // Flattened orbit gives depth (parallax): near particles sweep wider and faster.
        val p = g.c + Offset(cos(a) * rr, sin(a) * rr * (0.62f + z * 0.38f))
        if (p.x < -8 || p.x > g.width + 8 || p.y < -8 || p.y > g.height + 8) continue
        val twinkle = 0.55f + 0.45f * sin(t * 2.3f + e.pPhase[i] * 3f)
        val alpha = (0.18f + 0.62f * z) * twinkle * glow
        val radius = g.hair * (0.6f + z * 1.9f) * (1f + e.level * 0.6f * z)
        val col = if (i % 5 == 0) e.accent else if (i % 2 == 0) e.secondary else e.primary
        if (z > 0.8f) drawCircle(col.copy(alpha = alpha * 0.18f), radius = radius * 3.2f, center = p, blendMode = light)
        drawCircle(col.copy(alpha = alpha), radius = radius, center = p, blendMode = light)
    }
}

private fun DrawScope.drawCrosshair(e: CoreEngine, g: CoreGeometry, light: BlendMode, glow: Float, th: Float) {
    val c = g.c
    drawLine(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, e.secondary.copy(alpha = 0.45f * glow), Color.Transparent)),
        start = Offset(0f, c.y), end = Offset(g.width, c.y), strokeWidth = th, blendMode = light,
    )
    val top = c.y - g.r * 1.18f
    val bottom = c.y + g.r * 1.18f
    drawLine(
        brush = Brush.verticalGradient(listOf(Color.Transparent, e.secondary.copy(alpha = 0.4f * glow), Color.Transparent), startY = top, endY = bottom),
        start = Offset(c.x, top), end = Offset(c.x, bottom), strokeWidth = th, blendMode = light,
    )
    // Star flares where the axis meets the outer ring.
    for (y in floatArrayOf(c.y - g.r, c.y + g.r)) {
        val p = Offset(c.x, y)
        val f = g.r * 0.06f * (0.8f + 0.2f * sin(e.time * 1.7f))
        drawCircle(e.secondary.copy(alpha = 0.35f * glow), radius = f, center = p, blendMode = light)
        drawLine(Color.White.copy(alpha = 0.75f * glow), p - Offset(f * 1.6f, 0f), p + Offset(f * 1.6f, 0f), strokeWidth = th, blendMode = light)
        drawLine(Color.White.copy(alpha = 0.75f * glow), p - Offset(0f, f * 1.6f), p + Offset(0f, f * 1.6f), strokeWidth = th, blendMode = light)
        drawCircle(Color.White.copy(alpha = 0.9f * glow), radius = th * 1.6f, center = p, blendMode = light)
    }
}

private fun DrawScope.drawRings(e: CoreEngine, g: CoreGeometry, cache: CoreCache, light: BlendMode, glow: Float, th: Float, compact: Boolean) {
    val c = g.c
    val t = e.time
    val broken = e.fracture
    drawCircle(e.primary.copy(alpha = 0.22f * glow), radius = g.r, center = c, style = Stroke(th), blendMode = light)

    // Fine tick ring (cached path, rotated as a whole).
    rotate(t * 4f, c) {
        drawPath(cache.outerTicks, e.secondary.copy(alpha = 0.42f * glow * (1f - broken * 0.5f)), style = Stroke(th), blendMode = light)
    }
    rotate(-t * 3f, c) {
        drawPath(cache.innerTicks, e.primary.copy(alpha = 0.35f * glow), style = Stroke(th), blendMode = light)
    }
    if (!compact) {
        rotate(t * 1.5f, c) {
            drawPath(cache.dotRing, e.secondary.copy(alpha = 0.5f * glow), blendMode = light)
        }
    }

    val rArc = g.r * 1.04f
    val tl = Offset(c.x - rArc, c.y - rArc)
    val sz = Size(rArc * 2, rArc * 2)
    if (broken > 0.5f) {
        // Error: static, broken ring — nothing spins.
        for (i in 0 until 10) {
            drawArc(e.primary.copy(alpha = 0.7f * glow), i * 36f + 8f, 20f, false, tl, sz, style = Stroke(th * 2.2f, cap = StrokeCap.Round), blendMode = light)
        }
        return
    }
    rotate(t * 14f, c) {
        // Two-pass bloom: wide faint stroke under a thin bright one.
        drawArc(e.primary.copy(alpha = 0.16f * glow), -90f, 70f, false, tl, sz, style = Stroke(th * 7f, cap = StrokeCap.Round), blendMode = light)
        drawArc(e.primary.copy(alpha = 0.95f * glow), -90f, 70f, false, tl, sz, style = Stroke(th * 2f, cap = StrokeCap.Round), blendMode = light)
        drawArc(e.secondary.copy(alpha = 0.7f * glow), 130f, 28f, false, tl, sz, style = Stroke(th * 1.6f, cap = StrokeCap.Round), blendMode = light)
    }
    val rSeg = g.r * 0.95f
    rotate(-t * 9f, c) {
        for (i in 0 until 3) {
            drawArc(
                e.accent.copy(alpha = 0.55f * glow), i * 120f + 20f, 38f, false,
                Offset(c.x - rSeg, c.y - rSeg), Size(rSeg * 2, rSeg * 2), style = Stroke(th * 1.3f), blendMode = light,
            )
        }
    }
}

/** Horizontal audio spectrum through the core: measured audio in LISTENING/SPEAKING, ambient otherwise. */
private fun DrawScope.drawSpectrum(e: CoreEngine, g: CoreGeometry, a: CoreAppearance, light: BlendMode, glow: Float, th: Float) {
    val bars = 46
    val start = g.orb * 1.08f
    val span = (g.width / 2f - start - th * 4f).coerceAtLeast(10f)
    val step = span / bars
    val t = e.time
    val ambient = (0.10f + 0.05f * sin(t * 0.8f)) * (0.6f + e.energy)
    val drive = ambient * (1f - e.audioGain) + (0.05f + e.level * 1.05f) * e.audioGain
    for (side in intArrayOf(-1, 1)) {
        for (i in 0 until bars) {
            val u = i.toFloat() / bars
            val fall = exp(-u * 1.9f)
            val noise = 0.55f + 0.45f * sin(i * 1.9f + t * 7f + side * 0.7f) * cos(i * 0.57f - t * 4.1f)
            val h = g.r * 0.48f * a.waveAmplitude * fall * drive * noise * (1f - e.fracture * 0.8f) + th
            val x = g.c.x + side * (start + i * step)
            val col = lerpColor(e.secondary, e.primary, u)
            drawLine(col.copy(alpha = 0.16f * glow * fall), Offset(x, g.c.y - h), Offset(x, g.c.y + h), strokeWidth = step * 0.9f, cap = StrokeCap.Round, blendMode = light)
            drawLine(col.copy(alpha = (0.35f + 0.6f * fall) * glow), Offset(x, g.c.y - h), Offset(x, g.c.y + h), strokeWidth = max(th, step * 0.32f), cap = StrokeCap.Round, blendMode = light)
        }
    }
}

/** Strand height at canvas x for strand k (shared by strands and the pulses that travel on them). */
internal fun strandY(e: CoreEngine, g: CoreGeometry, a: CoreAppearance, k: Int, x: Float): Float {
    val n = a.strandCount
    val u = (x - g.c.x) / g.r
    val env = exp(-u * u * 0.85f)
    val amp = g.r * a.waveAmplitude * (0.05f + e.strandAmp * 0.22f + e.level * e.audioGain * 0.42f) * (1f - e.fracture * 0.85f)
    val t = e.time
    val w1 = sin(u * (2.2f + k * 0.35f + e.turbulence * 1.5f) + t * (1.6f + k * 0.4f) + k * 1.3f + e.charsPhase)
    val w2 = sin(u * (4.7f + k * 0.8f) - t * (2.3f + k * 0.3f) + k) * e.turbulence
    val offset = (k - (n - 1) / 2f) * g.r * 0.014f
    return g.c.y + amp * env * (w1 * 0.65f + w2 * 0.35f) + offset * env
}

private fun DrawScope.drawStrands(e: CoreEngine, g: CoreGeometry, cache: CoreCache, a: CoreAppearance, light: BlendMode, glow: Float, th: Float) {
    val n = a.strandCount.coerceIn(1, cache.strandPaths.size)
    val steps = 96
    val dx = g.width / steps
    for (k in 0 until n) {
        val p = cache.strandPaths[k]
        p.reset()
        for (s in 0..steps) {
            val x = s * dx
            val y = strandY(e, g, a, k, x)
            if (s == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        val col = when (k % 3) { 0 -> e.secondary; 1 -> e.primary; else -> e.accent }
        val brush = Brush.horizontalGradient(listOf(Color.Transparent, col, col, Color.Transparent))
        drawPath(p, brush, alpha = 0.10f * glow, style = Stroke(th * 6f), blendMode = light)
        drawPath(p, brush, alpha = (0.55f + 0.3f * e.level) * glow * (if (k == 0) 1f else 0.75f), style = Stroke(th * (if (k == 0) 1.8f else 1.1f)), blendMode = light)
    }
}

private fun DrawScope.drawOrb(e: CoreEngine, g: CoreGeometry, cache: CoreCache, a: CoreAppearance, presence: Presence, light: BlendMode, glow: Float, th: Float) {
    val c = g.c
    val ro = g.orb * (1f + 0.03f * e.level + 0.02f * e.ack)
    drawCircle(
        brush = Brush.radialGradient(listOf(e.primary.copy(alpha = 0.28f * glow), Color.Transparent), center = c, radius = ro * 1.5f),
        radius = ro * 1.5f, center = c, blendMode = light,
    )
    drawCircle(a.palette.orbFill, radius = ro, center = c)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(e.secondary.copy(alpha = 0.30f * glow), e.primary.copy(alpha = 0.14f * glow), Color.Transparent),
            center = c + Offset(-ro * 0.15f, -ro * 0.2f), radius = ro * 1.1f,
        ),
        radius = ro, center = c, blendMode = light,
    )
    clipPath(cache.orbClip) {
        when (presence) {
            Presence.AVATAR -> drawPresenceProfile(e, c, ro, light, glow, th, cache.scratch)
            Presence.CORE -> drawEnergySwirl(e, c, ro, light, glow, th, cache.scratch)
        }
    }
    // Rim: brighter with live audio; green flash on a backend-confirmed task.
    drawCircle(e.secondary.copy(alpha = (0.14f + 0.2f * e.level) * glow), radius = ro, center = c, style = Stroke(th * 7f), blendMode = light)
    drawCircle(e.secondary.copy(alpha = (0.75f + 0.25f * e.level) * glow), radius = ro, center = c, style = Stroke(th * 1.6f), blendMode = light)
    if (e.ack > 0.01f) {
        drawCircle(Color(0xFF4FD18B).copy(alpha = 0.7f * e.ack), radius = ro * (1.04f + 0.3f * (1f - e.ack)), center = c, style = Stroke(th * 2.5f), blendMode = light)
    }
}

/** Abstract presence: luminous ribbons swirling inside the orb. */
private fun DrawScope.drawEnergySwirl(e: CoreEngine, c: Offset, ro: Float, light: BlendMode, glow: Float, th: Float, path: Path) {
    val t = e.time
    for (i in 0 until 9) {
        val a0 = i * 0.7f + t * (0.25f + i * 0.03f)
        val swell = 1f + 0.25f * e.level + 0.1f * sin(t * 0.9f + i)
        val p0 = c + polar(ro * 0.98f, a0)
        val p1 = c + polar(ro * 0.55f * swell, a0 + 1.1f + 0.3f * sin(t + i))
        val p2 = c + polar(ro * 0.35f * swell, a0 + 2.3f)
        val p3 = c + polar(ro * 0.98f, a0 + 3.4f + 0.2f * cos(t * 0.7f + i))
        path.reset()
        path.moveTo(p0.x, p0.y)
        path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        val col = if (i % 3 == 0) e.accent else if (i % 2 == 0) e.secondary else e.primary
        drawPath(path, col.copy(alpha = 0.12f * glow), style = Stroke(th * 6f), blendMode = light)
        drawPath(path, col.copy(alpha = (0.45f + 0.35f * e.level) * glow), style = Stroke(th * (1f + (i % 3) * 0.5f)), blendMode = light)
    }
}

private fun DrawScope.drawPulses(e: CoreEngine, g: CoreGeometry, a: CoreAppearance, light: BlendMode, glow: Float, th: Float) {
    for (i in 0 until e.pulseMax) {
        val pos = e.pulsePos[i]
        if (pos < 0f) continue
        val k = e.pulseStrand[i] % a.strandCount.coerceAtLeast(1)
        for (tail in 0 until 4) {
            val x = (pos - tail * 0.012f) * g.width
            val y = strandY(e, g, a, k, x)
            val fade = 1f - tail / 4f
            drawCircle(Color.White.copy(alpha = 0.85f * fade * glow), radius = th * (2.4f - tail * 0.4f), center = Offset(x, y), blendMode = light)
            if (tail == 0) drawCircle(e.secondary.copy(alpha = 0.3f * glow), radius = th * 7f, center = Offset(x, y), blendMode = light)
        }
    }
    // Light travelling around the tick ring (one in idle, more when busy).
    val n = 1 + (e.travel * 3).toInt()
    for (j in 0 until n) {
        val ang = e.time * (0.6f + j * 0.25f) + j * 2.1f
        val p = g.c + polar(g.r * 0.88f, ang)
        drawCircle(Color.White.copy(alpha = 0.8f * glow), radius = th * 1.8f, center = p, blendMode = light)
        drawCircle(e.secondary.copy(alpha = 0.25f * glow), radius = th * 6f, center = p, blendMode = light)
    }
}

private fun DrawScope.drawRipples(e: CoreEngine, g: CoreGeometry, light: BlendMode, glow: Float, th: Float) {
    for (i in e.rippleStart.indices) {
        val p = e.ripple(i)
        if (p < 0f) continue
        val r = g.orb + (g.r * 1.1f - g.orb) * p
        drawCircle(e.secondary.copy(alpha = 0.55f * (1f - p) * glow), radius = r, center = g.c, style = Stroke(th * (2.4f - 1.6f * p)), blendMode = light)
    }
}

private fun DrawScope.drawOverlays(e: CoreEngine, g: CoreGeometry, light: BlendMode, glow: Float, th: Float) {
    val c = g.c
    if (e.satellites > 0.01f) {
        // TOOL EXECUTION: three satellites with trails on a sequencer orbit.
        val r = g.r * 0.95f
        for (j in 0 until 3) {
            val ang = e.time * 1.9f + j * (2 * PI / 3).toFloat()
            val p = c + polar(r, ang)
            drawArc(
                e.secondary.copy(alpha = 0.5f * e.satellites * glow), Math.toDegrees(ang.toDouble()).toFloat() - 40f, 40f, false,
                Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(th * 2.4f, cap = StrokeCap.Round), blendMode = light,
            )
            drawCircle(Color.White.copy(alpha = e.satellites * glow), radius = th * 3f, center = p, blendMode = light)
        }
    }
    if (e.lock > 0.01f) {
        // AWAITING APPROVAL: calm, closed double ring with four brackets.
        drawCircle(e.primary.copy(alpha = 0.7f * e.lock), radius = g.r * 1.08f, center = c, style = Stroke(th * 2f), blendMode = light)
        drawCircle(e.primary.copy(alpha = 0.3f * e.lock), radius = g.r * 1.13f, center = c, style = Stroke(th), blendMode = light)
        val rb = g.r * 1.18f
        for (q in 0 until 4) {
            drawArc(
                e.primary.copy(alpha = 0.9f * e.lock), q * 90f - 12f, 24f, false,
                Offset(c.x - rb, c.y - rb), Size(rb * 2, rb * 2), style = Stroke(th * 3f, cap = StrokeCap.Round), blendMode = light,
            )
        }
    }
}

// ------------------------------------------------------------------------------------ helpers

internal fun polar(r: Float, a: Float) = Offset(cos(a) * r, sin(a) * r)

internal fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))
