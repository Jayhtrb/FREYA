package ai.freya.app

import ai.freya.app.domain.CoreState
import ai.freya.app.ui.core.CoreAppearance
import ai.freya.app.ui.core.CoreEngine
import ai.freya.app.ui.core.CoreInput
import ai.freya.app.ui.core.StateStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreEngineTest {
    private fun run(e: CoreEngine, input: CoreInput, seconds: Float, reduce: Boolean = false) {
        repeat((seconds * 60).toInt()) { e.step(1f / 60, input, reduce) }
    }

    @Test fun easesTowardTargetStyle() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.PROCESSING), 2f)
        assertEquals(StateStyle.forState(CoreState.PROCESSING).energy, e.energy, 0.02f)
        assertEquals(1f, e.travel, 0.05f)
    }

    @Test fun audioOnlyDrivesGeometryWhileListeningOrSpeaking() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.IDLE, audioLevel = 1f, audioMeasured = true), 1f)
        assertEquals(0f, e.level, 0.001f)
        run(e, CoreInput(CoreState.LISTENING, audioLevel = 0.8f), 0.5f)
        assertTrue("level ${e.level}", e.level > 0.7f)
        assertTrue(e.levelMeasured)
    }

    @Test fun speakingWithoutMeasuredAudioIsFlaggedAsNotMeasured() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.SPEAKING, audioLevel = 0.9f, audioMeasured = false), 0.5f)
        assertFalse(e.levelMeasured)
        // Without word timing there is nothing to show: no fake amplitude.
        assertEquals(0f, e.level, 0.01f)
        run(e, CoreInput(CoreState.SPEAKING, audioMeasured = false, wordTick = 1), 1f / 30)
        assertTrue("word timing pulse", e.level > 0.05f)
    }

    @Test fun measuredSpeechFollowsTheEnvelope() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.SPEAKING, audioLevel = 0.6f, audioMeasured = true), 0.5f)
        assertEquals(0.6f, e.level, 0.05f)
        assertTrue(e.levelMeasured)
    }

    @Test fun onsetCreatesRipple() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.LISTENING, audioLevel = 0f), 0.3f)
        run(e, CoreInput(CoreState.LISTENING, audioLevel = 1f), 0.1f)
        assertTrue((e.rippleStart.indices).any { e.ripple(it) >= 0f })
    }

    @Test fun reduceMotionFreezesAmbientTimeButNotState() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.ERROR), 1f, reduce = true)
        assertEquals(0f, e.time, 0.0001f)
        assertEquals(1f, e.fracture, 0.05f)
    }

    @Test fun settlesWhenIdleSoReduceMotionCanStop() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.AWAITING_APPROVAL), 3f, reduce = true)
        assertTrue(e.settled(CoreState.AWAITING_APPROVAL))
        assertFalse(e.settled(CoreState.PROCESSING))
    }

    @Test fun preExistingAcknowledgeIsNotReplayed() {
        val e = CoreEngine()
        e.step(1f / 60, CoreInput(CoreState.IDLE, acknowledgeTick = 5), false)
        assertEquals(0f, e.ack, 0.001f)
        e.step(1f / 60, CoreInput(CoreState.IDLE, acknowledgeTick = 6), false)
        assertTrue(e.ack > 0.9f)
    }

    @Test fun particleCountFollowsDensityToken() {
        assertEquals(150, CoreEngine(CoreAppearance.Default).particleCount)
        assertTrue(CoreEngine(CoreAppearance.Compact).particleCount < 60)
    }
}
