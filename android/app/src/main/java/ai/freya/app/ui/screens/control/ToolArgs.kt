package ai.freya.app.ui.screens.control

import ai.freya.app.data.remote.FreyaJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Client-side validation of tool arguments against the declared schema types (unit-tested). */
object ToolArgs {
    fun build(fields: List<ToolField>, values: Map<String, String>): Result<JsonObject> {
        val out = mutableMapOf<String, JsonElement>()
        for (f in fields) {
            val raw = values[f.name]?.trim().orEmpty()
            if (raw.isEmpty()) {
                if (f.required) return Result.failure(IllegalArgumentException("${f.name} is required"))
                continue
            }
            val element: JsonElement = when (f.type) {
                "integer" -> raw.toLongOrNull()?.let { JsonPrimitive(it) }
                    ?: return Result.failure(IllegalArgumentException("${f.name} must be a whole number"))
                "number" -> raw.toDoubleOrNull()?.let { JsonPrimitive(it) }
                    ?: return Result.failure(IllegalArgumentException("${f.name} must be a number"))
                "boolean" -> raw.toBooleanStrictOrNull()?.let { JsonPrimitive(it) }
                    ?: return Result.failure(IllegalArgumentException("${f.name} must be true or false"))
                "array" -> parseArray(raw)
                    ?: return Result.failure(IllegalArgumentException("${f.name} must be a JSON array or comma-separated list"))
                "object" -> (runCatching { FreyaJson.json.parseToJsonElement(raw) }.getOrNull() as? JsonObject)
                    ?: return Result.failure(IllegalArgumentException("${f.name} must be a JSON object"))
                else -> JsonPrimitive(raw)
            }
            out[f.name] = element
        }
        return Result.success(JsonObject(out))
    }

    /** Accepts `["a","b"]`, `[{"agent_id":1,...}]`, or a plain `a, b` list of strings. */
    private fun parseArray(raw: String): JsonArray? {
        if (raw.startsWith("[")) return runCatching { FreyaJson.json.parseToJsonElement(raw) }.getOrNull() as? JsonArray
        val items = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return if (items.isEmpty()) null else JsonArray(items.map { JsonPrimitive(it) })
    }
}
