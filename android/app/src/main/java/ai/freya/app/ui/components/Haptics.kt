package ai.freya.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Subtle, semantic haptics. Uses platform constants so the user's system haptic settings apply. */
class FreyaHaptics(private val view: View, private val enabled: Boolean) {
    private fun perform(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }

    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)
    fun press() = perform(HapticFeedbackConstants.VIRTUAL_KEY)
    fun start() = perform(HapticFeedbackConstants.LONG_PRESS)

    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    )

    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    )
}

@Composable
fun rememberFreyaHaptics(enabled: Boolean): FreyaHaptics {
    val view = LocalView.current
    return remember(view, enabled) { FreyaHaptics(view, enabled) }
}
