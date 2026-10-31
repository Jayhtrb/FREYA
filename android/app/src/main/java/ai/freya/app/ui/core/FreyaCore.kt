package ai.freya.app.ui.core

import ai.freya.app.domain.CoreState
import ai.freya.app.ui.theme.FreyaTheme
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * The FREYA Core — a real-time, state-driven energy waveform: a 3D orb of luminous particles,
 * flowing light filaments, a horizontal field of frequency-band waves and travelling ripples.
 * Purely abstract: no face, figure, avatar or dial.
 *
 * Contract: the caller supplies the application [state] and, optionally, a measured audio
 * level ([audioLevel], read every frame without recomposition), whether it is measured
 * ([audioMeasured]) and the measured spectrum of FREYA's voice ([audioBands]). The Core never
 * touches microphones, TTS, permissions or the network; it only renders what it is told.
 *
 * Frame budget:
 *  - 60 fps while listening, speaking, thinking or running a task;
 *  - 30 fps idle; 20 fps while awaiting approval or showing an error;
 *  - no frames while the screen isn't visible (lifecycle below STARTED);
 *  - reduce-motion (system or in-app, or [animate] = false): ambient motion frozen, the loop
 *    runs only while state colours settle or live audio is present, then stops completely.
 */
@Composable
fun FreyaCore(
    state: CoreState,
    modifier: Modifier = Modifier,
    audioLevel: () -> Float = { 0f },
    audioMeasured: Boolean = false,
    audioBands: () -> FloatArray? = { null },
    wordTick: Long = 0,
    receivedChars: Int = 0,
    acknowledgeTick: Long = 0,
    appearance: CoreAppearance = CoreAppearance.Default,
    compact: Boolean = false,
    animate: Boolean = true,
) {
    val colors = FreyaTheme.colors
    val isDark = colors.isDark
    val reduce = FreyaTheme.motion.reduceMotion || !animate
    val resolved = remember(appearance, isDark) {
        if (isDark) appearance else appearance.copy(palette = CorePalette.Light)
    }
    val engine = remember(resolved) { CoreEngine(resolved) }
    val frame = remember { mutableIntStateOf(0) }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    val currentState by rememberUpdatedState(state)
    val currentLevel by rememberUpdatedState(audioLevel)
    val currentMeasured by rememberUpdatedState(audioMeasured)
    val currentBands by rememberUpdatedState(audioBands)
    val currentWord by rememberUpdatedState(wordTick)
    val currentChars by rememberUpdatedState(receivedChars)
    val currentAck by rememberUpdatedState(acknowledgeTick)

    val audioActive = state == CoreState.LISTENING || state == CoreState.SPEAKING
    val minFrameNs = when {
        reduce -> 66_000_000L
        state == CoreState.AWAITING_APPROVAL || state == CoreState.ERROR -> 50_000_000L
        state == CoreState.IDLE -> 33_000_000L
        else -> 0L
    }

    // In reduce-motion the loop exits once settled, so it is also keyed on the state to wake up.
    val restartKey: Any = if (reduce) state else Unit
    LaunchedEffect(visible, reduce, minFrameNs, audioActive, restartKey) {
        if (!visible) return@LaunchedEffect
        var lastStep = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (lastStep == 0L) lastStep = now - 16_000_000L
            if (now - lastStep < minFrameNs) continue
            val dt = (now - lastStep) / 1_000_000_000f
            lastStep = now
            val s = currentState
            engine.step(
                dt,
                CoreInput(
                    state = s,
                    audioLevel = currentLevel(),
                    audioMeasured = currentMeasured,
                    audioBands = currentBands(),
                    wordTick = currentWord,
                    receivedChars = currentChars,
                    acknowledgeTick = currentAck,
                ),
                reduceMotion = reduce,
            )
            frame.intValue++
            if (reduce && !audioActive && engine.settled(s)) break
        }
    }
    Canvas(
        modifier
            // Cache the Core in its own GPU layer: other UI changes (cursor blink, ripples, text)
            // re-composite the cached texture instead of re-rasterising every glow and particle.
            // The layer is re-rendered only when the engine advances (frame counter below).
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .semantics {
                contentDescription = "FREYA: ${state.label}"
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        frame.intValue // read in draw: each engine step invalidates only this draw pass
        drawFreyaCore(
            engine,
            compact = compact,
            surface = colors.surface,
            sunken = colors.sunken,
            highlight = colors.neuHighlight,
            shadow = colors.neuShadow,
        )
    }
}
