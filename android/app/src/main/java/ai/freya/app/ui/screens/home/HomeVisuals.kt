package ai.freya.app.ui.screens.home

import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.voice.EnvelopeFollower
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Small animation driver for the home's secondary visuals. Runs a frame loop only while
 * [active] (live audio) and the screen is visible; otherwise it draws one static frame.
 * Returns (time, smoothed level) as state read only in draw.
 */
@Composable
private fun rememberLiveDriver(active: Boolean, level: () -> Float): Pair<() -> Float, () -> Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val smoothed = remember { mutableFloatStateOf(0f) }
    val follower = remember { EnvelopeFollower(attackPerSec = 26f, releasePerSec = 6f) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val reduce = FreyaTheme.motion.reduceMotion
    val currentLevel by rememberUpdatedState(level)
    LaunchedEffect(active, visible, reduce) {
        if (!visible) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else (now - last) / 1e9f
            last = now
            if (!reduce) time.floatValue += dt
            smoothed.floatValue = follower.update(if (active) currentLevel() else 0f, dt)
            if (!active && smoothed.floatValue < 0.005f) break
        }
    }
    return Pair({ time.floatValue }, { smoothed.floatValue })
}

/**
 * Compact horizontal waveform under the status label. Measured mic or voice level while
 * listening/speaking; a calm dotted baseline otherwise (never an imitation of audio).
 */
@Composable
fun LiveWaveStrip(active: Boolean, level: () -> Float, color: Color, modifier: Modifier = Modifier) {
    val (time, smoothed) = rememberLiveDriver(active, level)
    val accent = FreyaTheme.colors.violet
    Canvas(modifier) {
        val t = time()
        val lv = smoothed()
        val n = 48
        val step = size.width / n
        val cy = size.height / 2f
        for (i in 0 until n) {
            val u = abs(i - (n - 1) / 2f) / (n / 2f)
            val shape = exp(-u * u * 2.6f)
            val noise = 0.55f + 0.45f * sin(i * 1.3f + t * 11f) * sin(i * 0.47f - t * 6f)
            val h = (size.height * 0.48f * shape * lv * noise).coerceAtLeast(1.2f)
            val x = i * step + step / 2f
            val col = if (shape > 0.5f) color else accent
            if (lv < 0.02f) {
                drawCircle(col.copy(alpha = 0.25f + 0.4f * shape), radius = 1.4f + shape * 1.2f, center = Offset(x, cy))
            } else {
                drawLine(col.copy(alpha = 0.35f + 0.6f * shape), Offset(x, cy - h), Offset(x, cy + h), strokeWidth = step * 0.4f, cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * Flowing light behind the talk button. Amplitude follows the measured level of whichever
 * side is talking; static gentle curves when nobody is.
 */
@Composable
fun MicWaves(active: Boolean, level: () -> Float, modifier: Modifier = Modifier) {
    val (time, smoothed) = rememberLiveDriver(active, level)
    val c = FreyaTheme.colors
    val path = remember { Path() }
    val blend = if (c.isDark) BlendMode.Plus else BlendMode.SrcOver
    Canvas(modifier) {
        val t = time()
        val lv = smoothed()
        val cy = size.height * 0.55f
        val colors = listOf(c.violet, c.cyan, c.violet.copy(alpha = 0.7f))
        for (k in 0 until 3) {
            path.reset()
            val steps = 60
            for (s in 0..steps) {
                val x = size.width * s / steps
                val u = (x - size.width / 2f) / (size.width / 2f)
                val env = 0.25f + 0.75f * (1f - exp(-u * u * 4f)) // flatter under the button
                val amp = size.height * (0.06f + 0.05f * k + 0.30f * lv) * env
                val y = cy + amp * sin(u * (3.2f + k) + t * (1.8f + k * 0.6f) + k * 1.7f)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val brush = Brush.horizontalGradient(listOf(Color.Transparent, colors[k], Color.Transparent))
            drawPath(path, brush, alpha = 0.12f, style = Stroke(9f), blendMode = blend)
            drawPath(path, brush, alpha = 0.75f, style = Stroke(2f), blendMode = blend)
        }
    }
}
