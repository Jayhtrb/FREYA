package ai.freya.app.ui.core

import ai.freya.app.domain.CoreState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Design tokens for the FREYA Core. Every visual quantity the renderer uses comes from here
 * (palette, glow, particle density, wave amplitude, ring thickness, motion speed), so the
 * look can be tuned or themed without touching drawing code.
 */
@Immutable
data class CoreAppearance(
    /** Multiplier on particle count (1 = 150 particles). Lowered automatically for small cores. */
    val particleDensity: Float = 1f,
    /** 0..1.5 multiplier on every glow/bloom alpha. Kept restrained so text stays readable. */
    val glow: Float = 1f,
    /** Multiplier on strand / spectrum amplitude. */
    val waveAmplitude: Float = 1f,
    /** Base hairline width in px is size-relative; this scales it. */
    val ringThickness: Float = 1f,
    /** Multiplier on all ambient motion speeds. */
    val motionSpeed: Float = 1f,
    /** Number of flowing waveform strands across the core. */
    val strandCount: Int = 6,
    val palette: CorePalette = CorePalette.Dark,
) {
    companion object {
        val Default = CoreAppearance()
        /** For small placements (header badge, sign-in): fewer particles, no spectrum. */
        val Compact = CoreAppearance(particleDensity = 0.35f, strandCount = 3, waveAmplitude = 0.7f)
    }
}

/** Colour sets per state. Cyan / electric blue / violet with restrained magenta. */
@Immutable
data class CorePalette(
    val idle: StatePalette,
    val listening: StatePalette,
    val processing: StatePalette,
    val tool: StatePalette,
    val speaking: StatePalette,
    val approval: StatePalette,
    val error: StatePalette,
    val orbFill: Color,
    val haze: Color,
) {
    fun forState(s: CoreState): StatePalette = when (s) {
        CoreState.IDLE -> idle
        CoreState.LISTENING -> listening
        CoreState.PROCESSING -> processing
        CoreState.TOOL_EXECUTION -> tool
        CoreState.SPEAKING -> speaking
        CoreState.AWAITING_APPROVAL -> approval
        CoreState.ERROR -> error
    }

    companion object {
        private val Cyan = Color(0xFF5FE6FF)
        private val Electric = Color(0xFF3D7BFF)
        private val Violet = Color(0xFF9B6BFF)
        private val Magenta = Color(0xFFE05BFF)
        private val Ice = Color(0xFFD7F3FF)
        private val Amber = Color(0xFFFFC46B)
        private val Ember = Color(0xFFFF6B72)

        val Dark = CorePalette(
            idle = StatePalette(primary = Electric, secondary = Cyan, accent = Violet),
            listening = StatePalette(primary = Cyan, secondary = Ice, accent = Electric),
            processing = StatePalette(primary = Violet, secondary = Cyan, accent = Magenta),
            tool = StatePalette(primary = Cyan, secondary = Violet, accent = Ice),
            speaking = StatePalette(primary = Violet, secondary = Cyan, accent = Magenta),
            approval = StatePalette(primary = Amber, secondary = Color(0xFFFFE2B0), accent = Violet),
            error = StatePalette(primary = Ember, secondary = Color(0xFFFFB3B6), accent = Color(0xFF7A5CFF)),
            orbFill = Color(0xFF060A1C),
            haze = Violet,
        )

        /** Light theme keeps the hues but deepens them so light-on-light stays visible. */
        val Light = CorePalette(
            idle = StatePalette(primary = Color(0xFF2F5BE0), secondary = Color(0xFF0091B8), accent = Color(0xFF6B45E0)),
            listening = StatePalette(primary = Color(0xFF0091B8), secondary = Color(0xFF2F5BE0), accent = Color(0xFF6B45E0)),
            processing = StatePalette(primary = Color(0xFF6B45E0), secondary = Color(0xFF0091B8), accent = Color(0xFFB03FD6)),
            tool = StatePalette(primary = Color(0xFF0091B8), secondary = Color(0xFF6B45E0), accent = Color(0xFF2F5BE0)),
            speaking = StatePalette(primary = Color(0xFF6B45E0), secondary = Color(0xFF0091B8), accent = Color(0xFFB03FD6)),
            approval = StatePalette(primary = Color(0xFFB36A00), secondary = Color(0xFFD48A1E), accent = Color(0xFF6B45E0)),
            error = StatePalette(primary = Color(0xFFC62E3A), secondary = Color(0xFFE0636B), accent = Color(0xFF6B45E0)),
            orbFill = Color(0xFFE8EEFF),
            haze = Color(0xFF6B45E0),
        )
    }
}

@Immutable
data class StatePalette(val primary: Color, val secondary: Color, val accent: Color)

/**
 * Behaviour per state. The engine eases from the current style to the target style, so
 * transitions are continuous and always reflect the real state model.
 */
data class StateStyle(
    val energy: Float,        // ambient motion speed 0..1
    val turbulence: Float,    // strand shape complexity 0..1
    val strandAmp: Float,     // baseline strand amplitude 0..1 (before audio)
    val audioGain: Float,     // how strongly measured audio drives geometry
    val spread: Float,        // particle outward push from audio 0..1
    val travel: Float,        // travelling light pulses 0..1
    val glow: Float,          // overall brightness 0..1
    val lock: Float,          // approval: motion damped + bracket marks
    val fracture: Float,      // error: broken rings, desaturated
    val satellites: Float,    // tool execution: orbiting markers
) {
    companion object {
        fun forState(s: CoreState): StateStyle = when (s) {
            CoreState.IDLE -> StateStyle(0.22f, 0.25f, 0.30f, 0f, 0f, 0.15f, 0.62f, 0f, 0f, 0f)
            CoreState.LISTENING -> StateStyle(0.45f, 0.35f, 0.12f, 1.0f, 0.9f, 0.05f, 0.85f, 0f, 0f, 0f)
            CoreState.PROCESSING -> StateStyle(0.95f, 0.85f, 0.45f, 0f, 0.2f, 1.0f, 0.9f, 0f, 0f, 0f)
            CoreState.TOOL_EXECUTION -> StateStyle(0.75f, 0.55f, 0.35f, 0f, 0.1f, 0.8f, 0.85f, 0f, 0f, 1f)
            CoreState.SPEAKING -> StateStyle(0.5f, 0.45f, 0.12f, 1.0f, 0.6f, 0.2f, 0.9f, 0f, 0f, 0f)
            CoreState.AWAITING_APPROVAL -> StateStyle(0.06f, 0.1f, 0.18f, 0f, 0f, 0f, 0.7f, 1f, 0f, 0f)
            CoreState.ERROR -> StateStyle(0.04f, 0.05f, 0.06f, 0f, 0f, 0f, 0.5f, 0f, 1f, 0f)
        }
    }
}
