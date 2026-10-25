package ai.freya.app

import ai.freya.app.domain.CoreInputs
import ai.freya.app.domain.CoreState
import ai.freya.app.domain.deriveCoreState
import ai.freya.app.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreStateTest {
    private fun inputs(
        voice: VoiceState = VoiceState.IDLE,
        busy: Boolean = false,
        tool: String? = null,
        approval: Boolean = false,
        error: String? = null,
    ) = CoreInputs(voice, busy, tool, approval, error)

    @Test fun idleWhenNothingHappens() = assertEquals(CoreState.IDLE, deriveCoreState(inputs()))
    @Test fun listening() = assertEquals(CoreState.LISTENING, deriveCoreState(inputs(voice = VoiceState.LISTENING)))
    @Test fun processingWhileRequestInFlight() = assertEquals(CoreState.PROCESSING, deriveCoreState(inputs(busy = true, voice = VoiceState.THINKING)))
    @Test fun toolBeatsProcessing() = assertEquals(CoreState.TOOL_EXECUTION, deriveCoreState(inputs(busy = true, tool = "inspect_graph")))
    @Test fun speaking() = assertEquals(CoreState.SPEAKING, deriveCoreState(inputs(voice = VoiceState.SPEAKING)))
    @Test fun approvalBlocksEverything() =
        assertEquals(CoreState.AWAITING_APPROVAL, deriveCoreState(inputs(voice = VoiceState.LISTENING, busy = true, tool = "x", approval = true, error = "e")))
    @Test fun errorOnlyWhenOtherwiseIdle() {
        assertEquals(CoreState.ERROR, deriveCoreState(inputs(error = "boom")))
        assertEquals(CoreState.LISTENING, deriveCoreState(inputs(error = "boom", voice = VoiceState.LISTENING)))
        assertEquals(CoreState.PROCESSING, deriveCoreState(inputs(error = "boom", busy = true)))
    }
    @Test fun blankErrorIsNotAnError() = assertEquals(CoreState.IDLE, deriveCoreState(inputs(error = "  ")))
    @Test fun taskExecutionVoiceStateAloneIsNotATool() =
        assertEquals(CoreState.IDLE, deriveCoreState(inputs(voice = VoiceState.TASK_EXECUTION)))
}
