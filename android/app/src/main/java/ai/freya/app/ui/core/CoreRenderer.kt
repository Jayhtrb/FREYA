package ai.freya.app.ui.core

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Reusable draw buffers. Thousands of particles are drawn with a handful of batched
 * `drawPoints`/`drawLines` calls (one per colour/depth bucket) instead of one call per particle,
 * and nothing is allocated per particle per frame.
 */
class CoreCache {
    internal val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    internal val additive = PorterDuffXfermode(PorterDuff.Mode.ADD)
    internal val path = Path()

    private var particleCap = -1
    private var filamentCap = -1
    private var streamCap = -1

    internal var pts: Array<FloatArray> = emptyArray()
    internal var trails: Array<FloatArray> = emptyArray()
    internal val ptsN = IntArray(BUCKETS)
    internal val trailsN = IntArray(BUCKETS)

    internal var fil: Array<FloatArray> = emptyArray()
    internal val filN = IntArray(FIL_BUCKETS)
    internal var heads = FloatArray(0)

    internal var stream = FloatArray(0)
    internal var streamTrail = FloatArray(0)

    internal fun ensure(e: CoreEngine) {
        if (particleCap != e.particleCount) {
            particleCap = e.particleCount
            pts = Array(BUCKETS) { FloatArray(particleCap * 2) }
            trails = Array(BUCKETS) { FloatArray(particleCap * 4) }
        }
        val segs = e.filamentCount * e.filamentSamples
        if (filamentCap != segs) {
            filamentCap = segs
            fil = Array(FIL_BUCKETS) { FloatArray(segs * 4 + 4) }
            heads = FloatArray(e.filamentCount * 2 + 2)
        }
        if (streamCap != e.streamCount) {
            streamCap = e.streamCount
            stream = FloatArray(streamCap * 2 + 2)
            streamTrail = FloatArray(streamCap * 4 + 4)
        }
    }

    internal companion object {
        const val BACK = 0
        const val MID = 1
        const val FRONT = 2
        const val HOT = 3
        const val ACCENT = 4
        const val BUCKETS = 5
        const val TAPER = 4
        const val FIL_BUCKETS = TAPER * 2 // taper steps × (back, front)
    }
}

/**
 * Draws one frame of the FREYA energy core. Layer order (back to front):
 * volumetric haze → horizontal frequency-band field + outward particle stream → far filaments →
 * far particles → luminous nucleus → near particles (with motion trails) → near filaments.
 * No faces, rings or dials — only particles, light filaments, waves and glow.
 */
fun DrawScope.drawFreyaCore(e: CoreEngine, cache: CoreCache, additive: Boolean, compact: Boolean) {
    cache.ensure(e)
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val c = Offset(w / 2f, h / 2f)
    val r = if (compact) min(w, h) * 0.40f else min(w * 0.34f, h * 0.37f)
    val unit = max(r / 150f, 0.6f)
    val blend = if (additive) BlendMode.Plus else BlendMode.SrcOver
    val themeK = if (additive) 1f else 0.7f
    val glow = e.glow * themeK
    val lv = e.level

    // ---- 1. volumetric haze ----
    val haloR = r * (if (compact) 1.5f else 2.2f)
    drawCircle(
        Brush.radialGradient(
            0f to e.primary.copy(alpha = (0.30f * glow * (0.8f + lv * 0.7f + e.ack * 0.5f)).coerceAtMost(0.6f)),
            0.45f to e.accent.copy(alpha = 0.07f * glow),
            1f to Color.Transparent,
            center = c, radius = haloR,
        ),
        radius = haloR, center = c, blendMode = blend,
    )
    if (!compact) {
        // A flattened light bank along the field: reads as volumetric light, not a shape.
        scale(scaleX = 3.4f, scaleY = 0.38f, pivot = c) {
            drawCircle(
                Brush.radialGradient(
                    listOf(e.secondary.copy(alpha = 0.11f * glow * (1f + lv * 1.2f)), Color.Transparent),
                    center = c, radius = r * 1.1f,
                ),
                radius = r * 1.1f, center = c, blendMode = blend,
            )
        }
    }

    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val paint = cache.paint
        paint.xfermode = if (additive) cache.additive else null

        fun batch(arr: FloatArray, n: Int, color: Color, alpha: Float, width: Float, points: Boolean) {
            if (n < (if (points) 2 else 4)) return
            paint.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
            paint.strokeWidth = max(width, 1f)
            if (points) nc.drawPoints(arr, 0, n, paint) else nc.drawLines(arr, 0, n, paint)
        }

        // ---- 2. horizontal energy field: one wave per frequency band ----
        if (e.waveCount > 0) {
            paint.xfermode = null // Compose paths below use their own blend mode
            val half = w / 2f
            val steps = 96
            for (lane in 0 until e.waveCount) {
                val path = cache.path
                path.reset()
                for (s in 0..steps) {
                    val u = -1f + 2f * s / steps
                    val x = c.x + u * half
                    val y = c.y + e.waveY(lane, u) * r
                    if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val tint = when {
                    lane % 4 == 3 -> lerp(e.accent, e.secondary, 0.35f) // subtle magenta/violet strands
                    else -> lerp(e.primary, e.secondary, lane / (e.waveCount - 1f).coerceAtLeast(1f))
                }
                val strength = (0.22f + e.waveAmp(lane) * 1.6f).coerceAtMost(0.95f) * glow
                val brush = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.32f to tint, 0.5f to lerp(tint, e.hot, 0.35f), 0.68f to tint, 1f to Color.Transparent,
                    startX = 0f, endX = w,
                )
                drawPath(path, brush, alpha = strength * 0.16f, style = Stroke(unit * 8f), blendMode = blend)
                drawPath(path, brush, alpha = strength, style = Stroke(max(unit * 1.1f, 1f)), blendMode = blend)
            }
            paint.xfermode = if (additive) cache.additive else null

            // Outward particle stream riding the waves.
            var n = 0
            var tn = 0
            val sArr = cache.stream
            val tArr = cache.streamTrail
            for (i in 0 until e.streamCount) {
                val u = e.streamU[i]
                val lane = e.streamLane[i]
                val x = c.x + u * half
                val y = c.y + (e.waveY(lane, u) + e.streamJitter[i]) * r
                sArr[n++] = x; sArr[n++] = y
                val back = u - (if (u >= 0f) 1f else -1f) * (0.03f + e.energy * 0.03f + lv * 0.05f)
                tArr[tn++] = x; tArr[tn++] = y
                tArr[tn++] = c.x + back * half; tArr[tn++] = c.y + (e.waveY(lane, back) + e.streamJitter[i]) * r
            }
            batch(tArr, tn, e.secondary, 0.22f * glow, unit * 0.8f, points = false)
            batch(sArr, n, lerp(e.secondary, e.hot, 0.5f), 0.55f * glow, unit * 1.3f, points = true)
        }

        // ---- 3. filaments: bucket segments by depth and position along the arc (taper) ----
        val filN = cache.filN
        filN.fill(0)
        var headN = 0
        val samples = e.filamentSamples
        for (f in 0 until e.filamentCount) {
            val base = f * samples
            for (j in 0 until samples - 1) {
                val a = base + j
                val b = a + 1
                val taper = (j * CoreCache.TAPER / (samples - 1)).coerceIn(0, CoreCache.TAPER - 1)
                val front = (e.fz[a] + e.fz[b]) > 0f
                val bucket = taper + (if (front) CoreCache.TAPER else 0)
                val arr = cache.fil[bucket]
                var k = filN[bucket]
                arr[k++] = c.x + e.fx[a] * r; arr[k++] = c.y + e.fy[a] * r
                arr[k++] = c.x + e.fx[b] * r; arr[k++] = c.y + e.fy[b] * r
                filN[bucket] = k
            }
            val hIdx = base + samples - 1
            if (e.fz[hIdx] > -0.2f) {
                cache.heads[headN++] = c.x + e.fx[hIdx] * r
                cache.heads[headN++] = c.y + e.fy[hIdx] * r
            }
        }
        fun drawFilaments(front: Boolean) {
            val depthK = if (front) 0.62f else 0.22f
            for (t in 0 until CoreCache.TAPER) {
                val bucket = t + (if (front) CoreCache.TAPER else 0)
                val n = filN[bucket]
                val a = TAPER_ALPHA[t] * depthK * glow
                val col = lerp(e.secondary, e.hot, t / (CoreCache.TAPER - 1f) * 0.7f)
                batch(cache.fil[bucket], n, col, a * 0.12f, unit * (3f + t), points = false)
                batch(cache.fil[bucket], n, col, a, unit * (0.6f + t * 0.25f), points = false)
            }
        }
        drawFilaments(front = false)

        // ---- 4. particles: bucket by depth / energy / kind ----
        val pts = cache.pts
        val trails = cache.trails
        val ptsN = cache.ptsN
        val trailsN = cache.trailsN
        ptsN.fill(0); trailsN.fill(0)
        val trailK = 2.6f
        for (i in 0 until e.particleCount) {
            val d = e.depth[i]
            val bucket = when {
                e.kind[i] == 1.toByte() -> CoreCache.ACCENT
                e.heat[i] > 0.55f -> CoreCache.HOT
                d < -0.35f -> CoreCache.BACK
                d < 0.35f -> CoreCache.MID
                else -> CoreCache.FRONT
            }
            val x = c.x + e.sx[i] * r
            val y = c.y + e.sy[i] * r
            val pa = pts[bucket]
            var pn = ptsN[bucket]
            pa[pn++] = x; pa[pn++] = y
            ptsN[bucket] = pn
            var dx = (e.psx[i] - e.sx[i]) * r * trailK
            var dy = (e.psy[i] - e.sy[i]) * r * trailK
            // Cap trail length so slow frames (or a dropped frame) never smear into streaks.
            val len = abs(dx) + abs(dy)
            val maxLen = r * 0.07f
            if (len > maxLen) { dx *= maxLen / len; dy *= maxLen / len }
            if (abs(dx) + abs(dy) > 0.6f) {
                val ta = trails[bucket]
                var tn = trailsN[bucket]
                ta[tn++] = x; ta[tn++] = y; ta[tn++] = x + dx; ta[tn++] = y + dy
                trailsN[bucket] = tn
            }
        }
        fun particles(bucket: Int, color: Color, alpha: Float, size: Float) {
            batch(trails[bucket], trailsN[bucket], color, alpha * 0.45f, size * 0.55f, points = false)
            batch(pts[bucket], ptsN[bucket], color, alpha, size, points = true)
        }
        particles(CoreCache.BACK, e.primary, 0.42f * glow, unit * 1.0f)
        particles(CoreCache.MID, lerp(e.primary, e.secondary, 0.5f), 0.55f * glow, unit * 1.35f)

        // ---- 5. nucleus: volumetric light from inside the orb ----
        paint.xfermode = null
        val nucR = r * 0.78f
        val nucA = (0.42f * glow * (0.6f + lv * 0.9f + e.ack * 0.7f)).coerceAtMost(0.75f)
        drawCircle(
            Brush.radialGradient(
                0f to e.hot.copy(alpha = nucA),
                0.35f to e.secondary.copy(alpha = nucA * 0.35f),
                1f to Color.Transparent,
                center = c, radius = nucR,
            ),
            radius = nucR, center = c, blendMode = blend,
        )
        paint.xfermode = if (additive) cache.additive else null

        particles(CoreCache.FRONT, e.secondary, 0.92f * glow, unit * 1.75f)
        particles(CoreCache.HOT, e.hot, 0.95f * glow, unit * 2.1f)
        particles(CoreCache.ACCENT, e.accent, 0.75f * glow, unit * 1.6f)

        drawFilaments(front = true)
        batch(cache.heads, headN, e.hot, 0.7f * glow, unit * 2.4f, points = true)
        paint.xfermode = null
    }
}

private val TAPER_ALPHA = floatArrayOf(0.10f, 0.28f, 0.55f, 0.92f)
