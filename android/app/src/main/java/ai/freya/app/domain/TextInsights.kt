package ai.freya.app.domain

/** Pure text helpers used to build source references and suggestions. */
object TextInsights {
    private val url = Regex("""https?://[^\s<>()\[\]{}"'`]+""", RegexOption.IGNORE_CASE)
    private val trailing = charArrayOf('.', ',', ';', ':', '!', '?', '*', '_')

    /** Distinct http(s) links in order of appearance, without trailing punctuation. */
    fun links(text: String): List<String> =
        url.findAll(text).map { it.value.trimEnd(*trailing) }.filter { it.length > 10 }.distinct().toList()

    /** Display host for a link ("github.com"), falling back to the raw link. */
    fun host(link: String): String =
        link.substringAfter("://").substringBefore('/').removePrefix("www.").ifBlank { link }

    fun hasCode(text: String): Boolean = text.contains("```")
}

sealed interface SuggestionAction {
    data class Send(val text: String) : SuggestionAction
    data object OpenControlCenter : SuggestionAction
    data object OpenPower : SuggestionAction
    data object OpenProjects : SuggestionAction
}

data class Suggestion(val label: String, val action: SuggestionAction)

/**
 * Context-aware quick actions. Deterministic, at most [max], empty while FREYA is busy.
 * Every action maps to something the app can really do (send a prompt or open a screen);
 * nothing implies a capability that isn't connected (e.g. there is no "Search the web").
 */
object Suggestions {
    fun forContext(
        providerConfigured: Boolean?,
        conversationEmpty: Boolean,
        busy: Boolean,
        lastReply: String?,
        hour: Int,
        max: Int = 4,
    ): List<Suggestion> {
        if (busy) return emptyList()
        val out = mutableListOf<Suggestion>()
        if (providerConfigured == false) {
            out += Suggestion("Connect an AI provider", SuggestionAction.OpenPower)
        }
        if (conversationEmpty) {
            out += when (hour) {
                in 5..11 -> Suggestion("Plan my day", SuggestionAction.Send("Help me plan my day. Ask me what's on my plate first."))
                in 12..16 -> Suggestion("Unblock a task", SuggestionAction.Send("I'm stuck on a task. Ask me questions until we find the next step."))
                else -> Suggestion("Wrap up my day", SuggestionAction.Send("Help me wrap up today and list what's left for tomorrow."))
            }
            out += Suggestion("Check my projects", SuggestionAction.OpenProjects)
            out += Suggestion("Run a task", SuggestionAction.OpenControlCenter)
            out += Suggestion("What can you do?", SuggestionAction.Send("What can you actually do for me right now, with the tools you have connected?"))
        } else if (!lastReply.isNullOrBlank()) {
            if (TextInsights.hasCode(lastReply)) {
                out += Suggestion("Explain this code", SuggestionAction.Send("Walk me through that code step by step."))
            }
            if (lastReply.length > 600) {
                out += Suggestion("Shorter, please", SuggestionAction.Send("Give me that in three bullet points."))
            }
            if (TextInsights.links(lastReply).isNotEmpty()) {
                out += Suggestion("Which source is best?", SuggestionAction.Send("Of the sources you mentioned, which is most reliable and why?"))
            }
            out += Suggestion("What's the next step?", SuggestionAction.Send("What should I do next?"))
            out += Suggestion("Check my projects", SuggestionAction.OpenProjects)
        }
        return out.distinctBy { it.label }.take(max)
    }
}
