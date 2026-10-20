package ai.freya.app.ui.navigation

object Routes {
    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"

    const val HOME = "home"
    const val PROJECTS = "projects"
    const val SEARCH = "search"
    const val JOBS = "jobs"
    const val SETTINGS = "settings"
    const val MEMORY = "memory"
    const val POWER = "power"

    const val SIMULATION = "simulation/{projectId}"
    const val REPORT = "report/{simulationId}"
    const val INTERVIEW = "interview/{simulationId}"

    fun simulation(projectId: String) = "simulation/$projectId"
    fun report(simulationId: String) = "report/$simulationId"
    fun interview(simulationId: String) = "interview/$simulationId"

    val bottomLevel: Set<String> = setOf(HOME, PROJECTS, SEARCH, JOBS, SETTINGS)
}
