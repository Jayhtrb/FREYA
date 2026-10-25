package ai.freya.app.ui.screens.home

import ai.freya.app.domain.CoreState
import ai.freya.app.ui.components.FreyaHaptics
import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.Motion
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/** What a press on the talk control does in each state (documented in docs/futuristic-ui.md). */
enum class TalkMode { START, FINISH, BARGE_IN, BLOCKED, UNAVAILABLE, NEEDS_PERMISSION }

fun talkModeFor(state: CoreState, sttAvailable: Boolean, hasPermission: Boolean): TalkMode = when {
    !sttAvailable -> TalkMode.UNAVAILABLE
    !hasPermission -> TalkMode.NEEDS_PERMISSION
    state == CoreState.LISTENING -> TalkMode.FINISH
    state == CoreState.SPEAKING -> TalkMode.BARGE_IN
    state == CoreState.PROCESSING || state == CoreState.TOOL_EXECUTION || state == CoreState.AWAITING_APPROVAL -> TalkMode.BLOCKED
    else -> TalkMode.START
}

/**
 * Push-to-talk.
 *  - Tap: start listening (recognition ends on its own at end of speech); tap again to finish early.
 *  - Hold (>= 300 ms): listen while held; release sends what was heard.
 *  - While FREYA speaks: tap interrupts her and starts listening (barge-in).
 *  - While a request/tool/approval is pending: disabled — use Stop.
 */
@Composable
fun PushToTalkButton(
    mode: TalkMode,
    level: Float,
    haptics: FreyaHaptics,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onBargeIn: () -> Unit,
    onRequestPermission: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
) {
    val c = FreyaTheme.colors
    val listening = mode == TalkMode.FINISH
    val enabled = mode != TalkMode.BLOCKED
    val ring by animateColorAsState(
        when (mode) {
            TalkMode.FINISH -> c.cyan
            TalkMode.BLOCKED -> c.textMuted.copy(alpha = 0.4f)
            TalkMode.UNAVAILABLE -> c.textMuted
            else -> c.violet
        },
        tween(Motion.state), label = "pttRing",
    )
    val pressScale by animateFloatAsState(if (listening) 1.06f else 1f, tween(Motion.micro), label = "pttScale")
    val currentMode by rememberUpdatedState(mode)

    val label = when (mode) {
        TalkMode.START -> "Push to talk"
        TalkMode.FINISH -> "Finish speaking"
        TalkMode.BARGE_IN -> "Interrupt and talk"
        TalkMode.BLOCKED -> "Talk unavailable while FREYA is working"
        TalkMode.UNAVAILABLE -> "Voice input unavailable"
        TalkMode.NEEDS_PERMISSION -> "Allow microphone to talk"
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(pressScale)
            .semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = if (listening) "Listening" else "Not listening"
                onClick(label) { handleTap(currentMode, onStart, onFinish, onBargeIn, onRequestPermission, onUnavailable); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val m = currentMode
                        if (m == TalkMode.BLOCKED) return@detectTapGestures
                        haptics.press()
                        if (m != TalkMode.START && m != TalkMode.BARGE_IN) {
                            // Non-hold modes: act on release as a tap.
                            if (tryAwaitRelease()) handleTap(m, onStart, onFinish, onBargeIn, onRequestPermission, onUnavailable)
                            return@detectTapGestures
                        }
                        // START / BARGE_IN: begin immediately; decide tap vs hold on release.
                        if (m == TalkMode.BARGE_IN) onBargeIn() else onStart()
                        haptics.start()
                        val releasedQuickly = withTimeoutOrNull(300) { tryAwaitRelease() }
                        if (releasedQuickly == null) {
                            // Held: listen until release, then finish.
                            tryAwaitRelease()
                            haptics.confirm()
                            onFinish()
                        }
                        // Quick tap: keep listening until end of speech or another tap.
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            if (listening) {
                drawCircle(c.cyan.copy(alpha = 0.18f + 0.35f * level), radius = r * (0.92f + 0.10f * level))
            }
            drawCircle(
                Brush.radialGradient(listOf(ring.copy(alpha = 0.28f), Color.Transparent), radius = r),
                radius = r,
            )
            drawCircle(ring.copy(alpha = if (enabled) 0.9f else 0.4f), radius = r * 0.98f, style = Stroke(r * 0.05f))
        }
        Box(
            Modifier
                .size(size * 0.70f)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        if (listening) listOf(c.cyan, c.cyan.copy(alpha = 0.7f))
                        else listOf(ring.copy(alpha = 0.95f), ring.copy(alpha = 0.65f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (mode) {
                    TalkMode.FINISH -> Icons.Filled.GraphicEq
                    TalkMode.UNAVAILABLE -> Icons.Filled.MicOff
                    TalkMode.BLOCKED -> Icons.Filled.Lock
                    else -> Icons.Filled.Mic
                },
                contentDescription = null,
                tint = if (c.isDark) Color(0xFF05060B) else Color.White,
                modifier = Modifier.size(size * 0.32f),
            )
        }
    }
}

private fun handleTap(
    mode: TalkMode,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onBargeIn: () -> Unit,
    onRequestPermission: () -> Unit,
    onUnavailable: () -> Unit,
) {
    when (mode) {
        TalkMode.START -> onStart()
        TalkMode.FINISH -> onFinish()
        TalkMode.BARGE_IN -> onBargeIn()
        TalkMode.NEEDS_PERMISSION -> onRequestPermission()
        TalkMode.UNAVAILABLE -> onUnavailable()
        TalkMode.BLOCKED -> Unit
    }
}
