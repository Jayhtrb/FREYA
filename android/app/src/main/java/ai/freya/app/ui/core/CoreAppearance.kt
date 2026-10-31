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
    val filamentCount: Int = 5,
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
        val Compact = CoreAppearance(particleCount = 420, filamentCount = 3, waveCount = 0, streamCount = 0)
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
        // Muted, desaturated state accents — soft-UI, never neon.
        private val Violet = Color(0xFF8B8FD0)
        private val VioletDim = Color(0xFF7479BC)
        private val Cyan = Color(0xFF5FA8C9)
        private val Ice = Color(0xFFC9D6E0)
        private val IceBlue = Color(0xFF9FB8C9)
        private val Amber = Color(0xFFD9A96B)
        private val Rose = Color(0xFFE08086)

        val Dark = CorePalette(
            idle = StatePalette(primary = Violet, secondary = Cyan, accent = Violet, hot = Ice),
            listening = StatePalette(primary = Cyan, secondary = Ice, accent = Violet, hot = Color(0xFFDDE6EC)),
            processing = StatePalette(primary = Violet, secondary = Cyan, accent = VioletDim, hot = Color(0xFFD5D8EE)),
            tool = StatePalette(primary = Cyan, secondary = Violet, accent = VioletDim, hot = Ice),
            speaking = StatePalette(primary = Cyan, secondary = Violet, accent = VioletDim, hot = Color(0xFFDDE6EC)),
            approval = StatePalette(primary = Amber, secondary = Color(0xFFE3C18F), accent = Violet, hot = Color(0xFFF0E0C8)),
            error = StatePalette(primary = Rose, secondary = Color(0xFFC4B8C4), accent = Violet, hot = Color(0xFFEAC4C8)),
        )

        /** Light theme deepens the same muted hues so they stay visible on light. */
        val Light = CorePalette(
            idle = StatePalette(Color(0xFF6C6FB8), Color(0xFF4C90A8), Color(0xFF6C6FB8), Color(0xFF3A4A6B)),
            listening = StatePalette(Color(0xFF4C90A8), Color(0xFF6C6FB8), Color(0xFF6C6FB8), Color(0xFF2C4A5A)),
            processing = StatePalette(Color(0xFF6C6FB8), Color(0xFF4C90A8), Color(0xFF5A4FB0), Color(0xFF3A2E6E)),
            tool = StatePalette(Color(0xFF4C90A8), Color(0xFF6C6FB8), Color(0xFF5A4FB0), Color(0xFF2C4A5A)),
            speaking = StatePalette(Color(0xFF4C90A8), Color(0xFF6C6FB8), Color(0xFF5A4FB0), Color(0xFF2C4A5A)),
            approval = StatePalette(Color(0xFFB0803C), Color(0xFFC79A5A), Color(0xFF6C6FB8), Color(0xFF5A4A1E)),
            error = StatePalette(Color(0xFFC25B63), Color(0xFF9A8A98), Color(0xFF6C6FB8), Color(0xFF5E2A30)),
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
