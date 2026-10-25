package ai.freya.app.domain

import ai.freya.app.voice.VoiceState

/** The seven visual states of the FREYA Core. Each maps to something actually happening. */
enum class CoreState(val label: String) {
    IDLE("Standing by"),
    LISTENING("Listening to you"),
    PROCESSING("Thinking"),
    TOOL_EXECUTION("Running a task"),
    SPEAKING("Speaking"),
    AWAITING_APPROVAL("Waiting for your approval"),
    ERROR("Something went wrong"),
}

/** Snapshot of the real signals the Core is derived from. */
data class CoreInputs(
    val voice: VoiceState,
    val requestInFlight: Boolean,
    val activeTool: String?,
    val pendingApproval: Boolean,
    val error: String?,
)

/**
 * Deterministic priority: an approval gate blocks everything; then whatever the user is
 * doing right now (talking); then work in progress (tool, request); then output (speech);
 * an unresolved error shows only when nothing else is happening.
 */
fun deriveCoreState(i: CoreInputs): CoreState = when {
    i.pendingApproval -> CoreState.AWAITING_APPROVAL
    i.voice == VoiceState.LISTENING -> CoreState.LISTENING
    i.activeTool != null -> CoreState.TOOL_EXECUTION
    i.requestInFlight -> CoreState.PROCESSING
    i.voice == VoiceState.SPEAKING -> CoreState.SPEAKING
    !i.error.isNullOrBlank() -> CoreState.ERROR
    else -> CoreState.IDLE
}
