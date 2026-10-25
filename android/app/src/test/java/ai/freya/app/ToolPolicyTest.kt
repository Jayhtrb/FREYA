package ai.freya.app

import ai.freya.app.domain.ToolPolicy
import ai.freya.app.domain.ToolRisk
import ai.freya.app.ui.screens.control.ToolArgs
import ai.freya.app.ui.screens.control.ToolField
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPolicyTest {
    @Test fun readOnlyToolsRunWithoutApproval() {
        listOf("inspect_graph", "explain_relationships", "compare_scenarios", "summarize_findings").forEach {
            assertFalse(it, ToolPolicy.requiresApproval(it))
        }
    }

    @Test fun costlyOrWritingToolsNeedApproval() {
        listOf("run_simulation", "generate_report", "interview_agents").forEach {
            assertTrue(it, ToolPolicy.requiresApproval(it))
        }
    }

    @Test fun unknownToolsDefaultToSensitive() {
        assertEquals(ToolRisk.SENSITIVE, ToolPolicy.info("delete_everything").risk)
        assertTrue(ToolPolicy.requiresApproval("brand_new_tool"))
    }

    @Test fun argsRequiredFieldMissing() {
        val r = ToolArgs.build(listOf(ToolField("graph_id", "string", true)), emptyMap())
        assertTrue(r.isFailure)
    }

    @Test fun argsTypedConversion() {
        val fields = listOf(
            ToolField("project_id", "string", true),
            ToolField("max_rounds", "integer", false),
            ToolField("simulation_ids", "array", false),
        )
        val r = ToolArgs.build(fields, mapOf("project_id" to " p1 ", "max_rounds" to "12", "simulation_ids" to "a, b"))
        val obj = r.getOrThrow()
        assertEquals(JsonPrimitive("p1"), obj["project_id"])
        assertEquals(JsonPrimitive(12L), obj["max_rounds"])
        assertEquals(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))), obj["simulation_ids"])
    }

    @Test fun argsRejectBadInteger() {
        assertTrue(ToolArgs.build(listOf(ToolField("n", "integer", true)), mapOf("n" to "twelve")).isFailure)
    }

    @Test fun argsAcceptJsonArrayOfObjects() {
        val r = ToolArgs.build(listOf(ToolField("prompts", "array", true)), mapOf("prompts" to """[{"agent_id":1,"prompt":"hi"}]"""))
        assertTrue(r.isSuccess)
        assertEquals(1, (r.getOrThrow()["prompts"] as JsonArray).size)
    }

    @Test fun optionalBlankFieldsAreOmitted() {
        val r = ToolArgs.build(listOf(ToolField("x", "integer", false)), mapOf("x" to ""))
        assertTrue(r.getOrThrow().isEmpty())
    }
}
