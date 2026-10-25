package ai.freya.app

import ai.freya.app.data.remote.ChatStreamEvent
import ai.freya.app.data.remote.SseParser
import ai.freya.app.domain.SuggestionAction
import ai.freya.app.domain.Suggestions
import ai.freya.app.domain.TextInsights
import ai.freya.app.ui.screens.home.splitCode
import ai.freya.app.ui.screens.home.TalkMode
import ai.freya.app.ui.screens.home.talkModeFor
import ai.freya.app.domain.CoreState
import ai.freya.app.voice.AndroidSpeechRecognizerProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {
    @Test fun sseDeltaKeepsWhitespace() {
        assertEquals(ChatStreamEvent.Delta(" "), SseParser.parse("delta", """{"delta":" "}"""))
        assertEquals(ChatStreamEvent.Delta("\n"), SseParser.parse("delta", """{"delta":"\n"}"""))
        assertNull(SseParser.parse("delta", """{"delta":""}"""))
    }

    @Test fun ssePlainTextDelta() = assertEquals(ChatStreamEvent.Delta("hello"), SseParser.parse(null, "hello"))

    @Test fun sseDoneCarriesModelAndConversation() {
        val e = SseParser.parse("done", """{"conversation_id":"conv_1","model":"deepseek-chat","finished":true}""")
        assertEquals(ChatStreamEvent.Done("conv_1", "deepseek-chat"), e)
    }

    @Test fun sseError() = assertEquals(ChatStreamEvent.Error("rate limit"), SseParser.parse("error", """{"error":"rate limit"}"""))

    @Test fun sseToolEvents() {
        assertEquals(ChatStreamEvent.ToolStarted("inspect_graph"), SseParser.parse("tool_start", """{"tool":"inspect_graph"}"""))
        assertEquals(ChatStreamEvent.ToolFinished("inspect_graph", true), SseParser.parse("tool_result", """{"tool":"inspect_graph","ok":true}"""))
        assertEquals(ChatStreamEvent.ToolFinished("x", false), SseParser.parse("tool_result", """{"name":"x","error":"nope"}"""))
    }

    @Test fun linksAreExtractedWithoutTrailingPunctuation() {
        val text = "See https://github.com/Jayhtrb/FREYA. Also (https://huggingface.co/models?x=1), and https://github.com/Jayhtrb/FREYA again."
        assertEquals(listOf("https://github.com/Jayhtrb/FREYA", "https://huggingface.co/models?x=1"), TextInsights.links(text))
        assertEquals("huggingface.co", TextInsights.host("https://www.huggingface.co/models"))
    }

    @Test fun codeBlocksSplit() {
        val parts = splitCode("Intro\n```kotlin\nval x = 1\n```\nOutro")
        assertEquals(3, parts.size)
        assertTrue(parts[1].first)
        assertEquals("val x = 1", parts[1].second)
    }

    @Test fun suggestionsHiddenWhileBusyAndCapped() {
        assertTrue(Suggestions.forContext(true, true, busy = true, lastReply = null, hour = 9).isEmpty())
        val s = Suggestions.forContext(false, true, busy = false, lastReply = null, hour = 9)
        assertTrue(s.size <= 4)
        assertTrue(s.any { it.action == SuggestionAction.OpenProjects })
        assertEquals(SuggestionAction.OpenPower, s.first().action)
    }

    @Test fun suggestionsReactToReply() {
        val s = Suggestions.forContext(true, false, false, "Here:\n```\ncode\n```", 14)
        assertEquals("Explain this code", s.first().label)
    }

    @Test fun talkModes() {
        assertEquals(TalkMode.UNAVAILABLE, talkModeFor(CoreState.IDLE, sttAvailable = false, hasPermission = true))
        assertEquals(TalkMode.NEEDS_PERMISSION, talkModeFor(CoreState.IDLE, true, false))
        assertEquals(TalkMode.START, talkModeFor(CoreState.ERROR, true, true))
        assertEquals(TalkMode.FINISH, talkModeFor(CoreState.LISTENING, true, true))
        assertEquals(TalkMode.BARGE_IN, talkModeFor(CoreState.SPEAKING, true, true))
        assertEquals(TalkMode.BLOCKED, talkModeFor(CoreState.PROCESSING, true, true))
        assertEquals(TalkMode.BLOCKED, talkModeFor(CoreState.AWAITING_APPROVAL, true, true))
    }

    @Test fun rmsNormalisation() {
        assertEquals(0f, AndroidSpeechRecognizerProvider.normaliseRms(-10f))
        assertEquals(1f, AndroidSpeechRecognizerProvider.normaliseRms(20f))
        assertEquals(0.5f, AndroidSpeechRecognizerProvider.normaliseRms(4f), 0.001f)
    }
}
