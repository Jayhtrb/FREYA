package ai.freya.app.ui.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/**
 * Reusable draw buffer. The neumorphic renderer batches nothing per frame; this
 * class is kept so `FreyaCore`'s `remember { CoreCache() }` API stays stable.
 */
class CoreCache

/**
 * Draws one frame of the FREYA Core as a soft neumorphic orb — a raised disc of
 * the background colour inside a subtle sunken well, breathing with the measured
 * audio level and tinted by the muted state accent. Depth is dual-shadow only:
 * no particles, filaments, trails, or neon glow.
 */
fun DrawScope.drawFreyaCore(
    e: CoreEngine,
    compact: Boolean,
    surface: Color,
    sunken: Color,
    highlight: Color,
    shadow: Color,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val c = Offset(w / 2f, h / 2f)
    val baseR = if (compact) min(w, h) * 0.40f else min(w * 0.30f, h * 0.32f)

    // Audio-driven breathing: the orb swells with the smoothed level + ack + energy.
    val breathe = 1f + (e.level * 0.06f) + (e.ack * 0.03f) + (e.energy * 0.02f)
    val r = baseR * breathe

    // ---- sunken well behind the orb (inset) ----
    val wellR = r * 1.30f
    drawCircle(sunken, radius = wellR, center = c)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(shadow.copy(alpha = 0.30f), Color.Transparent),
            center = c,
            radius = wellR,
        ),
        radius = wellR,
        center = c,
    )

    // ---- raised orb: dark shadow (bottom-right) + light highlight (top-left) ----
    val off = r * 0.055f
    drawCircle(shadow.copy(alpha = 0.55f), radius = r, center = c + Offset(off, off))
    drawCircle(highlight.copy(alpha = 0.50f), radius = r, center = c + Offset(-off, -off))
    drawCircle(surface, radius = r, center = c)

    // Soft top-left sheen inside the orb (the "lit" side of the raised disc).
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(highlight.copy(alpha = 0.28f), Color.Transparent),
            center = c + Offset(-r * 0.42f, -r * 0.42f),
            radius = r * 1.10f,
        ),
        radius = r,
        center = c,
    )

    // ---- muted state accent ring (audio + glow driven) ----
    val ringAlpha = (0.16f + e.glow * 0.5f).coerceIn(0f, 0.7f)
    drawCircle(
        color = e.primary.copy(alpha = ringAlpha),
        radius = r * 0.90f,
        center = c,
        style = Stroke(width = (if (compact) 2f else 3f) * (0.8f + e.level * 0.6f)),
    )

    // ---- small state-tinted nucleus ----
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(e.secondary.copy(alpha = 0.30f * (0.7f + e.glow)), Color.Transparent),
            center = c,
            radius = r * 0.52f,
        ),
        radius = r * 0.52f,
        center = c,
    )
}
