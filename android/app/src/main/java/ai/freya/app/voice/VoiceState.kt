package ai.freya.app.voice

/**
 * Voice-pipeline states. THINKING / TASK_EXECUTION are kept for compatibility with the
 * persona contract; the UI derives its richer [ai.freya.app.domain.CoreState] from the
 * session, not from this enum alone.
 */
enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    TASK_EXECUTION,
}
