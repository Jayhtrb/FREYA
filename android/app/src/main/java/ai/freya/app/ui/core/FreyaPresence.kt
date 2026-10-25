package ai.freya.app.ui.core

import ai.freya.app.domain.CoreState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin

/**
 * FREYA's presence: an original side-profile drawn in light, with flowing strands of hair
 * that move with ambient energy and her voice. Vector-only; drawn inside the orb clip.
 *
 * Expression rules (all from real state):
 *  - lips part only by the measured voice level while SPEAKING (closed when silent);
 *  - the eye brightens with the microphone level while LISTENING;
 *  - a slight upward tilt while thinking or running a task;
 *  - hair sway grows with energy and audio.
 */
internal fun DrawScope.drawPresenceProfile(
    e: CoreEngine,
    c: Offset,
    ro: Float,
    light: BlendMode,
    glow: Float,
    th: Float,
    path: Path,
) {
    val t = e.time
    val speaking = e.state == CoreState.SPEAKING
    val listening = e.state == CoreState.LISTENING
    val thinking = e.state == CoreState.PROCESSING || e.state == CoreState.TOOL_EXECUTION
    val mouth = if (speaking && e.levelMeasured) e.level * 0.045f else if (speaking) e.level * 0.02f else 0f

    fun P(x: Float, y: Float) = Offset(c.x + (x - 0.04f) * ro, c.y + (y + 0.02f) * ro)

    rotate(if (thinking) -3f else 0f, c) {
        // ---- hair: flowing strands behind and around the head ----
        for (i in 0 until 18) {
            val s = i / 17f
            val sway = 0.07f * sin(t * 0.6f + i * 0.55f) * (0.4f + e.energy) + 0.035f * e.level * sin(t * 3f + i)
            // Root runs along the hairline from forehead back over the crown.
            val root = quad(P(0.03f, -0.64f), P(-0.12f, -0.86f), P(-0.38f, -0.6f), s)
            val c1 = root + Offset((-0.30f - s * 0.18f) * ro, (0.04f + s * 0.05f) * ro)
            val c2 = P(-0.55f - s * 0.25f + sway, 0.18f + s * 0.25f)
            val end = P(-0.42f - s * 0.45f + sway * 1.6f, 0.82f + s * 0.12f)
            path.reset()
            path.moveTo(root.x, root.y)
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
            val col = when (i % 3) { 0 -> e.accent; 1 -> e.secondary; else -> e.primary }
            drawPath(path, col.copy(alpha = 0.10f * glow), style = Stroke(th * 5f), blendMode = light)
            drawPath(path, col.copy(alpha = (0.35f + 0.25f * (1f - s)) * glow), style = Stroke(th * (0.9f + (i % 3) * 0.45f), cap = StrokeCap.Round), blendMode = light)
        }
        // A few strands that sweep forward past the neck for depth.
        for (i in 0 until 5) {
            val sway = 0.05f * sin(t * 0.7f + i)
            val a = P(-0.20f + i * 0.04f, -0.70f)
            val b1 = P(-0.42f, -0.20f + sway)
            val b2 = P(-0.10f + sway, 0.45f)
            val end = P(0.18f + i * 0.04f + sway, 0.95f)
            path.reset()
            path.moveTo(a.x, a.y)
            path.cubicTo(b1.x, b1.y, b2.x, b2.y, end.x, end.y)
            drawPath(path, e.secondary.copy(alpha = 0.28f * glow), style = Stroke(th * 1.1f, cap = StrokeCap.Round), blendMode = light)
        }

        // ---- face: closed shape for a faint fill, then the bright profile line ----
        buildProfile(path, ::P, mouth, closed = true)
        drawPath(
            path,
            Brush.radialGradient(listOf(e.secondary.copy(alpha = 0.16f * glow), Color.Transparent), center = P(0.12f, -0.05f), radius = ro * 0.55f),
            blendMode = light,
        )
        buildProfile(path, ::P, mouth, closed = false)
        drawPath(path, e.secondary.copy(alpha = 0.16f * glow), style = Stroke(th * 6f, cap = StrokeCap.Round), blendMode = light)
        drawPath(path, Color.White.copy(alpha = 0.85f * glow), style = Stroke(th * 1.4f, cap = StrokeCap.Round), blendMode = light)

        // ---- mouth line (parts with measured voice) ----
        val corner = P(0.205f, 0.155f + mouth * 0.5f)
        path.reset()
        path.moveTo(corner.x, corner.y)
        val inner = P(0.15f, 0.152f + mouth * 0.5f)
        path.quadraticBezierTo((corner.x + inner.x) / 2f, corner.y + ro * 0.008f, inner.x, inner.y)
        drawPath(path, Color.White.copy(alpha = 0.6f * glow), style = Stroke(th, cap = StrokeCap.Round), blendMode = light)

        // ---- eye: closed and serene; glows with the live microphone level ----
        path.reset()
        val e0 = P(0.055f, -0.212f)
        val e1 = P(0.15f, -0.205f)
        path.moveTo(e0.x, e0.y)
        path.quadraticBezierTo(P(0.10f, -0.18f).x, P(0.10f, -0.18f).y, e1.x, e1.y)
        drawPath(path, Color.White.copy(alpha = 0.8f * glow), style = Stroke(th * 1.2f, cap = StrokeCap.Round), blendMode = light)
        for (l in 0 until 3) {
            val base = quad(e0, P(0.10f, -0.18f), e1, 0.3f + l * 0.25f)
            drawLine(Color.White.copy(alpha = 0.5f * glow), base, base + Offset(-ro * 0.008f, ro * 0.022f), strokeWidth = th * 0.8f, blendMode = light)
        }
        val eyeGlow = (if (listening) 0.25f + 0.75f * e.level else 0f) + (if (thinking) 0.25f else 0f)
        if (eyeGlow > 0.01f) {
            val ec = P(0.105f, -0.195f)
            drawCircle(
                Brush.radialGradient(listOf(e.secondary.copy(alpha = 0.6f * eyeGlow * glow), Color.Transparent), center = ec, radius = ro * 0.12f),
                radius = ro * 0.12f, center = ec, blendMode = light,
            )
        }
        // Brow
        path.reset()
        val b0 = P(0.045f, -0.27f)
        path.moveTo(b0.x, b0.y)
        path.quadraticBezierTo(P(0.11f, -0.305f).x, P(0.11f, -0.305f).y, P(0.165f, -0.275f).x, P(0.165f, -0.275f).y)
        drawPath(path, Color.White.copy(alpha = 0.45f * glow), style = Stroke(th, cap = StrokeCap.Round), blendMode = light)
    }
}

/** Side profile, facing right, in orb units. [mouth] lowers the lower lip and chin. */
private fun buildProfile(path: Path, P: (Float, Float) -> Offset, mouth: Float, closed: Boolean) {
    path.reset()
    fun m(p: Offset) = path.moveTo(p.x, p.y)
    fun cub(a: Offset, b: Offset, d: Offset) = path.cubicTo(a.x, a.y, b.x, b.y, d.x, d.y)
    m(P(0.02f, -0.66f))
    cub(P(0.12f, -0.62f), P(0.18f, -0.42f), P(0.17f, -0.30f))              // forehead → brow
    cub(P(0.16f, -0.25f), P(0.18f, -0.23f), P(0.19f, -0.20f))              // brow → bridge
    cub(P(0.22f, -0.14f), P(0.30f, -0.07f), P(0.31f, -0.02f))              // bridge → nose tip
    cub(P(0.31f, 0.02f), P(0.26f, 0.03f), P(0.22f, 0.03f))                 // → under nose
    cub(P(0.22f, 0.06f), P(0.26f, 0.08f), P(0.25f, 0.11f))                 // upper lip
    cub(P(0.245f, 0.135f), P(0.22f, 0.15f), P(0.205f, 0.155f))             // → mouth corner
    val d = mouth
    cub(P(0.23f, 0.165f + d), P(0.245f, 0.19f + d), P(0.235f, 0.205f + d)) // lower lip
    cub(P(0.225f, 0.235f + d), P(0.19f, 0.25f + d), P(0.19f, 0.27f + d))   // chin crease
    cub(P(0.20f, 0.30f + d), P(0.23f, 0.34f + d), P(0.21f, 0.37f + d))     // chin
    cub(P(0.18f, 0.42f + d), P(0.10f, 0.46f), P(0.03f, 0.47f))             // jaw
    cub(P(0.0f, 0.55f), P(-0.02f, 0.65f), P(-0.02f, 0.80f))                // neck
    if (closed) {
        val back = P(-0.34f, 0.80f)
        path.lineTo(back.x, back.y)
        val top = P(-0.30f, -0.55f)
        path.lineTo(top.x, top.y)
        path.close()
    }
}

private fun quad(a: Offset, b: Offset, c: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(u * u * a.x + 2 * u * t * b.x + t * t * c.x, u * u * a.y + 2 * u * t * b.y + t * t * c.y)
}
