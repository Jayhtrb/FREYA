package ai.freya.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 4-dp spacing scale. Screens use these instead of ad-hoc values. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Horizontal gutter for full-width screen content. */
    val gutter = 20.dp

    /** Minimum touch target (Material / WCAG 2.5.8). */
    val touch = 48.dp
}

object Radius {
    val chip = RoundedCornerShape(10.dp)
    val control = RoundedCornerShape(14.dp)
    val panel = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(50)
}

/**
 * Animation rules.
 *  - Micro (press, toggle): 120 ms.  - State change (core colour/shape): 420 ms.
 *  - Screen / layout transitions: 520 ms with the "emphasized" curve.
 *  - Ambient loops are slow (>= 6 s period) and pause when reduce-motion is on.
 *  - Nothing loops unless it represents a live state (listening, processing, speaking).
 */
object Motion {
    const val micro = 120
    const val state = 420
    const val layout = 520
    const val ambientPeriod = 7000

    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
