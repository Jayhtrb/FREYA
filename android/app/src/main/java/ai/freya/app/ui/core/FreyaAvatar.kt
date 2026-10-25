package ai.freya.app.ui.core

import ai.freya.app.ui.theme.FreyaColors
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.max
import kotlin.math.sin

/**
 * Expression weights, each 0..1. All are driven by real session state:
 * listening = mic open, thinking = request or tool in flight, mouth = TTS word envelope
 * (0 whenever FREYA is silent), acknowledge = a task just completed, concern = error/approval.
 */
data class Expression(
    val listening: Float = 0f,
    val thinking: Float = 0f,
    val mouth: Float = 0f,
    val acknowledge: Float = 0f,
    val concern: Float = 0f,
    val blink: Float = 0f,
)

/**
 * FREYA's visage: a minimal, deliberately non-photoreal line portrait — hood, diadem with
 * three nodes, almond eye-lights, a single mouth line. Vector-only, a few dozen draw calls,
 * scales cleanly from a 40 dp header badge to the full home Core.
 */
internal fun DrawScope.drawAvatar(
    center: Offset,
    r: Float,
    colors: FreyaColors,
    accent: Color,
    t: Float,
    expression: Expression,
    stroke: Float,
) {
    val e = expression
    val line = colors.ice
    val tilt = -4f * e.listening + 2f * e.concern
    rotate(tilt, center) {
        val cx = center.x
        val cy = center.y + r * 0.04f

        // ---- hood: two sweeping curves framing the face ----
        val hood = Path().apply {
            moveTo(cx, cy - r * 0.92f)
            cubicTo(cx - r * 0.78f, cy - r * 0.80f, cx - r * 0.86f, cy + r * 0.10f, cx - r * 0.58f, cy + r * 0.78f)
            moveTo(cx, cy - r * 0.92f)
            cubicTo(cx + r * 0.78f, cy - r * 0.80f, cx + r * 0.86f, cy + r * 0.10f, cx + r * 0.58f, cy + r * 0.78f)
        }
        drawPath(
            hood,
            brush = Brush.verticalGradient(listOf(line.copy(alpha = 0.55f), accent.copy(alpha = 0.10f)), startY = cy - r, endY = cy + r),
            style = Stroke(stroke * 1.6f, cap = StrokeCap.Round),
        )

        // ---- face: elongated oval, faint fill so the eyes read as light ----
        val faceW = r * 0.62f
        val faceH = r * 0.84f
        val faceTl = Offset(cx - faceW / 2f, cy - faceH * 0.52f)
        drawOval(
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                startY = faceTl.y, endY = faceTl.y + faceH,
            ),
            topLeft = faceTl, size = Size(faceW, faceH),
        )
        drawOval(line.copy(alpha = 0.42f), topLeft = faceTl, size = Size(faceW, faceH), style = Stroke(stroke * 1.1f))

        // ---- diadem: arc with three nodes, brightens while thinking ----
        val dr = r * 0.50f
        val diademAlpha = 0.45f + 0.45f * e.thinking
        drawArc(
            accent.copy(alpha = diademAlpha), startAngle = 205f, sweepAngle = 130f, useCenter = false,
            topLeft = Offset(cx - dr, cy - r * 0.40f - dr), size = Size(dr * 2, dr * 2),
            style = Stroke(stroke * 1.3f, cap = StrokeCap.Round),
        )
        for (i in -1..1) {
            val nx = cx + i * dr * 0.62f
            val ny = cy - r * 0.40f - dr * (if (i == 0) 1.0f else 0.80f)
            val pulse = if (e.thinking > 0.01f) 0.5f + 0.5f * sin(t * 4f + i) else 1f
            drawCircle(accent.copy(alpha = diademAlpha * pulse), radius = stroke * (if (i == 0) 3.2f else 2.2f), center = Offset(nx, ny))
        }

        // ---- eyes ----
        val look = Offset(
            x = r * 0.035f * e.thinking + r * 0.01f * sin(t * 0.8f) * e.thinking,
            y = -r * 0.03f * e.thinking,
        )
        val eyeY = cy - r * 0.07f
        val eyeDx = r * 0.135f
        val eyeW = r * 0.15f * (1f + 0.10f * e.listening)
        val open = ((0.75f + 0.30f * e.listening - 0.15f * e.concern) * (1f - e.blink)).coerceIn(0.04f, 1.1f)
        for (side in listOf(-1f, 1f)) {
            val ec = Offset(cx + side * eyeDx, eyeY) + look
            drawCircle(
                brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent), center = ec, radius = eyeW * 1.3f),
                radius = eyeW * 1.3f, center = ec,
            )
            if (e.acknowledge > 0.45f) {
                // Content, closed "smiling" eyes after a completed task.
                val arc = Path().apply {
                    moveTo(ec.x - eyeW / 2f, ec.y + eyeW * 0.08f)
                    quadraticTo(ec.x, ec.y - eyeW * 0.30f, ec.x + eyeW / 2f, ec.y + eyeW * 0.08f)
                }
                drawPath(arc, accent, style = Stroke(stroke * 1.8f, cap = StrokeCap.Round))
            } else {
                val h = eyeW * 0.42f * open
                val almond = Path().apply {
                    moveTo(ec.x - eyeW / 2f, ec.y)
                    quadraticTo(ec.x, ec.y - h, ec.x + eyeW / 2f, ec.y)
                    quadraticTo(ec.x, ec.y + h, ec.x - eyeW / 2f, ec.y)
                    close()
                }
                drawPath(almond, colors.ice.copy(alpha = 0.95f))
                drawPath(almond, accent.copy(alpha = 0.55f), style = Stroke(stroke))
            }
        }

        // ---- mouth: closed line unless real speech is playing ----
        val my = cy + r * 0.25f
        val mw = r * 0.17f * (1f + 0.15f * e.acknowledge)
        val curve = (0.12f + 0.55f * e.acknowledge - 0.35f * e.concern) * r * 0.06f
        if (e.mouth > 0.06f) {
            val mh = r * 0.085f * e.mouth
            drawOval(
                accent.copy(alpha = 0.30f + 0.5f * e.mouth),
                topLeft = Offset(cx - mw / 2f, my - mh / 2f),
                size = Size(mw, max(stroke, mh)),
            )
            drawOval(line.copy(alpha = 0.8f), topLeft = Offset(cx - mw / 2f, my - mh / 2f), size = Size(mw, max(stroke, mh)), style = Stroke(stroke))
        } else {
            val mouth = Path().apply {
                moveTo(cx - mw / 2f, my)
                quadraticTo(cx, my + curve, cx + mw / 2f, my)
            }
            drawPath(mouth, line.copy(alpha = 0.75f), style = Stroke(stroke * 1.3f, cap = StrokeCap.Round))
        }
    }
}
