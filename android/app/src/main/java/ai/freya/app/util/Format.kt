package ai.freya.app.util

/** Turns snake_case statuses like `graph_completed` into "Graph completed". */
fun formatStatus(status: String?): String {
    if (status.isNullOrBlank()) return "—"
    return status.split('_').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}

fun formatBytes(bytes: Int): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    return "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
