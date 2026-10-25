package ai.freya.app.domain

/**
 * Client-side permission policy for backend tools.
 *
 * IMPORTANT: this is a UX gate, not a security boundary. The backend's
 * POST /api/freya/tools currently executes any registered tool for an authenticated user;
 * server-side enforcement is still required (tracked in docs/futuristic-ui.md).
 *
 * Unknown tools default to SENSITIVE so a new backend tool is never run silently.
 */
enum class ToolRisk(val label: String) {
    READ_ONLY("Read-only"),
    SENSITIVE("Changes data or uses paid compute"),
}

data class ToolInfo(val title: String, val risk: ToolRisk, val why: String, val activeLabel: String = "Working on $title")

object ToolPolicy {
    private val known = mapOf(
        "inspect_graph" to ToolInfo("Inspect knowledge graph", ToolRisk.READ_ONLY, "Reads graph nodes and edges.", activeLabel = "Inspecting your knowledge graph"),
        "explain_relationships" to ToolInfo("Explain relationships", ToolRisk.READ_ONLY, "Reads graph relationships.", activeLabel = "Tracing relationships in your graph"),
        "compare_scenarios" to ToolInfo("Compare scenarios", ToolRisk.READ_ONLY, "Reads existing simulation runs.", activeLabel = "Comparing your simulation runs"),
        "summarize_findings" to ToolInfo("Summarize findings", ToolRisk.READ_ONLY, "Reads an existing report.", activeLabel = "Summarizing report findings"),
        "run_simulation" to ToolInfo(
            "Run simulation", ToolRisk.SENSITIVE,
            "Creates and starts a new simulation. Uses model credits and can run for a long time.", activeLabel = "Running a simulation"),
        "generate_report" to ToolInfo(
            "Generate report", ToolRisk.SENSITIVE,
            "Starts report generation, which calls the AI provider many times.", activeLabel = "Generating your report"),
        "interview_agents" to ToolInfo(
            "Interview agents", ToolRisk.SENSITIVE,
            "Sends prompts to running simulation agents; each answer is a paid model call.", activeLabel = "Interviewing simulation agents"),
    )

    fun info(name: String): ToolInfo = known[name] ?: ToolInfo(
        title = name.replace('_', ' ').replaceFirstChar { it.uppercase() },
        risk = ToolRisk.SENSITIVE,
        why = "This tool isn't in FREYA's policy list yet, so it needs approval.",
    )

    fun requiresApproval(name: String): Boolean = info(name).risk != ToolRisk.READ_ONLY
}
