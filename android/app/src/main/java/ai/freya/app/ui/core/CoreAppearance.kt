package ai.freya.app.ui.core

import ai.freya.app.domain.CoreState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Design tokens for the FREYA energy core. Every visual quantity the renderer uses comes from
 * here (particle budget, filament and wave counts, glow, amplitude, motion speed, palette), so
 * the look can be tuned or themed without touching simulation or drawing code.
 */
@Immutable
data class CoreAppearance(
    /** Luminous particles forming the 3D energy orb. */
    val particleCount: Int = 2400,
    /** Flowing light filaments sweeping over the orb surface. */
    val filamentCount: Int = 7,
    /** Horizontal energy-field waves; one per frequency band (0 = no field, e.g. compact). */
    val waveCount: Int = 8,
    /** Particles streaming outward along the horizontal field. */
    val streamCount: Int = 320,
    /** 0..1.5 multiplier on every glow alpha. */
    val glow: Float = 1f,
    /** Multiplier on audio-driven displacement and wave amplitude. */
    val waveAmplitude: Float = 1f,
    /** Multiplier on all ambient motion speeds. */
    val motionSpeed: Float = 1f,
    val palette: CorePalette = CorePalette.Dark,
) {
    companion object {
        val Default = CoreAppearance()
        /** Small placements (header badge, sign-in): the orb only, fewer particles, no field. */
        val Compact = CoreAppearance(particleCount = 420, filamentCount = 4, waveCount = 0, streamCount = 0)
    }
}

/** Colour sets per state: electric cyan, ice blue and violet, with restrained magenta. */
@Immutable
data class CorePalette(
    val idle: StatePalette,
    val listening: StatePalette,
    val processing: StatePalette,
    val tool: StatePalette,
    val speaking: StatePalette,
    val approval: StatePalette,
    val error: StatePalette,
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
        private val Cyan = Color(0xFF4FE3FF)
        private val Ice = Color(0xFFD4F4FF)
        private val IceBlue = Color(0xFF7FB8FF)
        private val Electric = Color(0xFF3E8BFF)
        private val Violet = Color(0xFF8E6BFF)
        private val Magenta = Color(0xFFE25BFF)

        val Dark = CorePalette(
            idle = StatePalette(primary = Electric, secondary = Cyan, accent = Violet, hot = Ice),
            listening = StatePalette(primary = Cyan, secondary = Ice, accent = IceBlue, hot = Color(0xFFF0FBFF)),
            processing = StatePalette(primary = Violet, secondary = Cyan, accent = Magenta, hot = Color(0xFFE9DDFF)),
            tool = StatePalette(primary = Cyan, secondary = Violet, accent = Magenta, hot = Ice),
            speaking = StatePalette(primary = Color(0xFF5AA8FF), secondary = Cyan, accent = Magenta, hot = Ice),
            approval = StatePalette(primary = Color(0xFFC46BFF), secondary = Color(0xFFF1C8FF), accent = Violet, hot = Color(0xFFFBEFFF)),
            error = StatePalette(primary = Color(0xFFFF4F86), secondary = Color(0xFFB8A9C9), accent = Violet, hot = Color(0xFFFFC2D4)),
        )

        /** Light theme keeps the hues but deepens them so light-on-light stays visible. */
        val Light = CorePalette(
            idle = StatePalette(Color(0xFF1F5FD6), Color(0xFF0088B0), Color(0xFF5B3FD0), Color(0xFF0B3A8F)),
            listening = StatePalette(Color(0xFF0088B0), Color(0xFF1F5FD6), Color(0xFF5B3FD0), Color(0xFF00506A)),
            processing = StatePalette(Color(0xFF5B3FD0), Color(0xFF0088B0), Color(0xFFA02FC8), Color(0xFF331F8A)),
            tool = StatePalette(Color(0xFF0088B0), Color(0xFF5B3FD0), Color(0xFFA02FC8), Color(0xFF00506A)),
            speaking = StatePalette(Color(0xFF1F5FD6), Color(0xFF0088B0), Color(0xFFA02FC8), Color(0xFF0B3A8F)),
            approval = StatePalette(Color(0xFF8E2FC8), Color(0xFFB45FE0), Color(0xFF5B3FD0), Color(0xFF55127A)),
            error = StatePalette(Color(0xFFC8245A), Color(0xFF8A7A9C), Color(0xFF5B3FD0), Color(0xFF7A0F33)),
        )
    }
}

@Immutable
data class StatePalette(val primary: Color, val secondary: Color, val accent: Color, val hot: Color)

/**
 * Behaviour per state. The engine eases from the current style to the target style, so
 * transitions are continuous and always reflect the real state model.
 */
data class StateStyle(
    val energy: Float,       // rotation and ambient flow speed 0..1
    val turbulence: Float,   // surface noise displacement (fraction of radius)
    val swirl: Float,        // processing: latitude-dependent vortex twist, braided filaments
    val audioGain: Float,    // how strongly the measured level and bands move geometry
    val breath: Float,       // slow breathing amplitude
    val contraction: Float,  // approval: orb draws in and holds still
    val scatter: Float,      // error: particles drift apart, desaturated
    val orbit: Float,        // task: a share of particles leaves the orb as an orbiting stream
    val field: Float,        // horizontal field baseline amplitude (fraction of radius)
    val glow: Float,         // overall brightness 0..1
    val spin: Float,         // filament precession speed
) {
    companion object {
        fun forState(s: CoreState): StateStyle = when (s) {
            CoreState.IDLE -> StateStyle(0.22f, 0.07f, 0f, 0f, 1f, 0f, 0f, 0f, 0.12f, 0.72f, 0.2f)
            CoreState.LISTENING -> StateStyle(0.40f, 0.06f, 0f, 1f, 0.3f, 0f, 0f, 0f, 0.10f, 0.92f, 0.3f)
            CoreState.PROCESSING -> StateStyle(1.00f, 0.16f, 1f, 0f, 0.2f, 0f, 0f, 0f, 0.22f, 0.95f, 1.4f)
            CoreState.TOOL_EXECUTION -> StateStyle(0.70f, 0.10f, 0.35f, 0f, 0.2f, 0f, 0f, 1f, 0.16f, 0.90f, 0.8f)
            CoreState.SPEAKING -> StateStyle(0.45f, 0.06f, 0f, 1f, 0.2f, 0f, 0f, 0f, 0.10f, 0.95f, 0.4f)
            CoreState.AWAITING_APPROVAL -> StateStyle(0.06f, 0.03f, 0f, 0f, 0.6f, 0.16f, 0f, 0f, 0.04f, 0.75f, 0.05f)
            CoreState.ERROR -> StateStyle(0.05f, 0.02f, 0f, 0f, 0f, 0.05f, 1f, 0f, 0.02f, 0.50f, 0f)
        }
    }
}
