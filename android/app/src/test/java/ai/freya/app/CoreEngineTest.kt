package ai.freya.app

import ai.freya.app.domain.CoreState
import ai.freya.app.ui.core.CoreAppearance
import ai.freya.app.ui.core.CoreEngine
import ai.freya.app.ui.core.CoreInput
import ai.freya.app.ui.core.StateStyle
import ai.freya.app.voice.AudioEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class CoreEngineTest {
    private fun run(e: CoreEngine, input: CoreInput, seconds: Float, reduce: Boolean = false) {
        repeat((seconds * 60).toInt().coerceAtLeast(1)) { e.step(1f / 60, input, reduce) }
    }

    /** Mean projected distance of particles from the orb centre (orb radii). */
    private fun meanRadius(e: CoreEngine): Float =
        (0 until e.particleCount).map { hypot(e.sx[it], e.sy[it]) }.average().toFloat()

    @Test fun easesTowardTargetStyle() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.PROCESSING), 3f)
        assertEquals(StateStyle.forState(CoreState.PROCESSING).energy, e.energy, 0.02f)
        assertEquals(1f, e.swirl, 0.05f)
    }

    @Test fun thousandsOfParticlesFormAnOrb() {
        val e = CoreEngine(CoreAppearance.Default)
        assertTrue(e.particleCount >= 2000)
        run(e, CoreInput(CoreState.IDLE), 0.5f)
        // Every particle sits within the orb (perspective allows a little overshoot).
        assertTrue((0 until e.particleCount).all { hypot(e.sx[it], e.sy[it]) < 1.6f })
        // Both hemispheres are populated: it is a 3D volume, not a flat disc.
        assertTrue((0 until e.particleCount).count { e.depth[it] > 0.3f } > 300)
        assertTrue((0 until e.particleCount).count { e.depth[it] < -0.3f } > 300)
    }

    @Test fun ambientMotionMovesParticlesAndLeavesTrails() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.IDLE), 0.5f)
        val before = e.sx.copyOf()
        run(e, CoreInput(CoreState.IDLE), 0.2f)
        val moved = (0 until e.particleCount).count { abs(e.sx[it] - before[it]) > 1e-3f }
        assertTrue("moved $moved", moved > e.particleCount / 2)
        assertTrue((0 until e.particleCount).any { abs(e.psx[it] - e.sx[it]) > 1e-4f })
    }

    @Test fun audioOnlyDrivesGeometryWhileListeningOrSpeaking() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.IDLE, audioLevel = 1f, audioMeasured = true), 1f)
        assertEquals(0f, e.level, 0.001f)
        assertTrue(e.bands.all { it < 0.001f })
        run(e, CoreInput(CoreState.LISTENING, audioLevel = 0.8f), 0.5f)
        assertTrue("level ${e.level}", e.level > 0.7f)
        assertTrue(e.levelMeasured)
        // The mic has no spectrum: bands are the level spread over a profile, flagged as such.
        assertFalse(e.bandsMeasured)
        assertTrue(e.bands[1] > 0.5f)
    }

    @Test fun louderVoiceExpandsTheOrbAndRaisesTheField() {
        val quiet = CoreEngine()
        val loud = CoreEngine()
        run(quiet, CoreInput(CoreState.SPEAKING, audioLevel = 0f, audioMeasured = true), 1.5f)
        run(loud, CoreInput(CoreState.SPEAKING, audioLevel = 1f, audioMeasured = true), 1.5f)
        assertTrue(meanRadius(loud) > meanRadius(quiet) * 1.05f)
        assertTrue(loud.waveAmp(0) > quiet.waveAmp(0) + 0.2f)
    }

    @Test fun measuredSpectrumDrivesTheMatchingFieldWave() {
        val e = CoreEngine()
        val bands = FloatArray(AudioEnvelope.BANDS).also { it[AudioEnvelope.BANDS - 1] = 1f }
        run(e, CoreInput(CoreState.SPEAKING, audioLevel = 0.5f, audioMeasured = true, audioBands = bands), 1f)
        assertTrue(e.bandsMeasured)
        assertTrue(e.bands.last() > 0.9f && e.bands.first() < 0.05f)
        assertTrue(e.waveAmp(e.waveCount - 1) > e.waveAmp(0) + 0.3f)
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

    @Test fun onsetCreatesRippleThatDisplacesTheSurface() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.LISTENING, audioLevel = 0f), 0.3f)
        run(e, CoreInput(CoreState.LISTENING, audioLevel = 1f), 0.1f)
        assertTrue((e.rippleStart.indices).any { e.ripple(it) >= 0f })
    }

    @Test fun approvalContractsAndProcessingTwists() {
        val idle = CoreEngine()
        val approval = CoreEngine()
        run(idle, CoreInput(CoreState.IDLE), 2f)
        run(approval, CoreInput(CoreState.AWAITING_APPROVAL), 2f)
        assertTrue(meanRadius(approval) < meanRadius(idle) * 0.92f)
        val busy = CoreEngine()
        run(busy, CoreInput(CoreState.PROCESSING, receivedChars = 400), 2f)
        assertTrue(busy.charsPhase > 1f)
    }

    @Test fun taskSendsParticlesIntoAnOrbitStream() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.TOOL_EXECUTION), 3f)
        val outside = (0 until e.particleCount).count { hypot(e.sx[it], e.sy[it]) > 1.15f }
        assertTrue("outside $outside", outside > e.particleCount / 20)
    }

    @Test fun reduceMotionFreezesAmbientTimeButNotState() {
        val e = CoreEngine()
        run(e, CoreInput(CoreState.ERROR), 1f, reduce = true)
        assertEquals(0f, e.time, 0.0001f)
        assertEquals(1f, e.scatter, 0.05f)
        // No trails when nothing moves.
        assertTrue((0 until e.particleCount).all { e.psx[it] == e.sx[it] })
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

    @Test fun compactCoreHasNoFieldAndFewerParticles() {
        val c = CoreEngine(CoreAppearance.Compact)
        assertTrue(c.particleCount < 600)
        assertEquals(0, c.waveCount)
        assertEquals(0, c.streamCount)
    }
}
