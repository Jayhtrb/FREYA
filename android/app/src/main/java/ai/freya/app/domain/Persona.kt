package ai.freya.app.domain

import java.time.LocalTime

/** Static identity + greeting data mirrored from persona/freya.persona.json. */
object Persona {
    const val NAME = "FREYA"
    const val ACRONYM = "Flexible Reasoning Engine for Your Applications"
    const val TAGLINE = "One intelligence. Every interface. Your world."
    const val GREETING_TITLE = "FREYA ONLINE"

    /**
     * Time boundaries from the persona: morning >= 5, afternoon >= 12,
     * evening >= 17, night >= 21 (night maps to "Good evening").
     */
    fun timeGreeting(hour: Int): String = when {
        hour in 5..11 -> "Good morning"
        hour in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    fun greetingBody(): String {
        val greeting = timeGreeting(LocalTime.now().hour)
        return "$greeting. FREYA is online and ready. I've organized your priorities, " +
            "prepared your workspace, and identified a few things that could be done more " +
            "intelligently. Where shall we begin?"
    }
}

/** Tracks the once-per-session greeting (session = one app process lifetime). */
object SessionGreeting {
    @Volatile
    var shown = false
}
