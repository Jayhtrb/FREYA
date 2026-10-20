"""
MiroFish 工具封装（§4）。

这七个工具直接调用现有 MiroFish 服务/端点逻辑，绝不返回伪造结果。
底层能力（Zep 图谱、OASIS 模拟、报告 Agent）需要相应密钥/环境；
缺少前置条件时以明确的工具错误失败，而不是返回占位数据。
"""

from typing import Any, Dict, List, Optional

from .dispatcher import ToolArgumentError, ToolExecutionError
from .registry import ToolRegistry, ToolSpec


# ---------------------------------------------------------------------------
# 工具实现
# ---------------------------------------------------------------------------

def _run_simulation(args: Dict[str, Any], user_id: Optional[str]) -> Dict[str, Any]:
    from ...models.project import ProjectManager
    from ...services.simulation_manager import SimulationManager
    from ...services.simulation_runner import SimulationRunner

    project_id = args.get("project_id")
    requirement = args.get("simulation_requirement")
    max_rounds = args.get("max_rounds")

    if not project_id:
        raise ToolArgumentError("project_id is required")
    if not requirement:
        raise ToolArgumentError("simulation_requirement is required")

    project = ProjectManager.get_project(project_id)
    if not project:
        raise ToolExecutionError(f"project not found: {project_id}")
    if not project.graph_id:
        raise ToolExecutionError("project graph has not been built yet")

    manager = SimulationManager()
    state = manager.create_simulation(
        project_id=project_id, graph_id=project.graph_id
    )
    document_text = ProjectManager.get_extracted_text(project_id) or ""
    manager.prepare_simulation(
        simulation_id=state.simulation_id,
        simulation_requirement=requirement,
        document_text=document_text,
    )
    SimulationRunner.start_simulation(
        simulation_id=state.simulation_id,
        platform="parallel",
        max_rounds=max_rounds if max_rounds else None,
    )
    return {"simulation_id": state.simulation_id, "status": "running"}


def _inspect_graph(args: Dict[str, Any], user_id: Optional[str]) -> Dict[str, Any]:
    from ...services.zep_entity_reader import ZepEntityReader

    graph_id = args.get("graph_id")
    if not graph_id:
        raise ToolArgumentError("graph_id is required")

    reader = ZepEntityReader()
    nodes = reader.get_all_nodes(graph_id)
    edges = reader.get_all_edges(graph_id)
    return {
        "graph_id": graph_id,
        "node_count": len(nodes),
        "edge_count": len(edges),
        "nodes": nodes,
        "edges": edges,
    }


def _explain_relationships(args: Dict[str, Any], user_id: Optional[str]) -> Dict[str, Any]:
    from ...services.zep_entity_reader import ZepEntityReader

    graph_id = args.get("graph_id")
    entity_uuid = args.get("entity_uuid")
    if not graph_id:
        raise ToolArgumentError("graph_id is required")

    reader = ZepEntityReader()
    if entity_uuid:
        edges = reader.get_node_edges(entity_uuid, graph_id=graph_id)
        return {
            "graph_id": graph_id,
            "entity_uuid": entity_uuid,
            "edge_count": len(edges),
            "edges": edges,
        }

    nodes = reader.get_all_nodes(graph_id)
    edges = reader.get_all_edges(graph_id)
    return {
        "graph_id": graph_id,
        "node_count": len(nodes),
        "edge_count": len(edges),
        "nodes": nodes,
        "edges": edges,
    }


def _interview_agents(args: Dict[str, Any], user_id: Optional[str]) -> Dict[str, Any]:
    from ...services.simulation_runner import SimulationRunner

    simulation_id = args.get("simulation_id")
    prompts = args.get("prompts") or []
    if not simulation_id:
        raise ToolArgumentError("simulation_id is required")
    if not isinstance(prompts, list) or not prompts:
        raise ToolArgumentError("prompts must be a non-empty list")

    if not SimulationRunner.check_env_alive(simulation_id):
        raise ToolExecutionError("simulation environment is not running")

    results = []
    for item in prompts:
        if not isinstance(item, dict):
            raise ToolArgumentError("each prompt must be an object")
        agent_id = item.get("agent_id")
        prompt = item.get("prompt")
        if agent_id is None or not prompt:
            raise ToolArgumentError("each prompt needs agent_id and prompt")
        result = SimulationRunner.interview_agent(
            simulation_id=simulation_id,
            agent_id=agent_id,
            prompt=prompt,
        )
        results.append(result)

    return {"simulation_id": simulation_id, "results": results}


def _generate_report(args: Dict[str, Any], user_id: Optional[str]) -> Dict[str, Any]:
    from ...models.project import ProjectManager
    from ...services.simulation_manager import SimulationManager
    from ...services.report_agent import ReportManager, ReportAgent

    simulation_id = args.get("simulation_id")
    if not simulation_id:
        raise ToolArgumentError("simulation_id is required")

    existing = ReportManager.get_report_by_simulation(simulation_id)
    if existing:
        return existing.to_dict()

    state = SimulationManager().get_simulation(simulation_id)
    if not state:
        raise ToolExecutionError(f"simulation not found: {simulation_id}")

    project = ProjectManager.get_project(state.project_id)
    if not project:
        raise ToolExecutionError(f"project not found: {state.project_id}")

    agent = ReportAgent(
        graph_id=project.graph_id or "",
        simulation_id=simulation_id,
        simulation_requirement=project.simulation_requirement or "",
    )
    report = agent.generate_report()
    return report.to_dict()


def _compare_scenarios(args: Dict[str, Any], user_id: Optional[str]) -> Dict[str, Any]:
    from ...services.simulation_manager import SimulationManager

    simulation_ids = args.get("simulation_ids") or []
    if not isinstance(simulation_ids, list) or len(simulation_ids) < 2:
        raise ToolArgumentError("simulation_ids must contain at least two ids")

    manager = SimulationManager()
    summaries = []
    for sid in simulation_ids:
        state = manager.get_simulation(sid)
        if state:
            summaries.append(state.to_simple_dict())
        else:
            summaries.append({"simulation_id": sid, "error": "not found"})

    return {"simulations": summaries, "count": len(summaries)}


def _summarize_findings(args: Dict[str, Any], user_id: Optional[str]) -> Dict[str, Any]:
    from ...services.report_agent import ReportManager

    report_id = args.get("report_id")
    if not report_id:
        raise ToolArgumentError("report_id is required")

    report = ReportManager.get_report(report_id)
    if not report:
        raise ToolExecutionError(f"report not found: {report_id}")

    return {
        "report_id": report_id,
        "status": report.status.value,
        "title": report.outline.title if report.outline else None,
        "summary": report.outline.summary if report.outline else "",
    }


# ---------------------------------------------------------------------------
# 注册
# ---------------------------------------------------------------------------

_TOOL_DEFS = [
    ToolSpec(
        name="run_simulation",
        description="Create, prepare, and start a MiroFish simulation for a project.",
        input_schema={
            "type": "object",
            "properties": {
                "project_id": {"type": "string"},
                "simulation_requirement": {"type": "string"},
                "max_rounds": {"type": "integer"},
            },
        },
        required=["project_id", "simulation_requirement"],
        handler=_run_simulation,
    ),
    ToolSpec(
        name="inspect_graph",
        description="Inspect a MiroFish graph's nodes and edges.",
        input_schema={
            "type": "object",
            "properties": {"graph_id": {"type": "string"}},
        },
        required=["graph_id"],
        handler=_inspect_graph,
    ),
    ToolSpec(
        name="explain_relationships",
        description="Explain the relationships of an entity (or the whole graph).",
        input_schema={
            "type": "object",
            "properties": {
                "graph_id": {"type": "string"},
                "entity_uuid": {"type": "string"},
            },
        },
        required=["graph_id"],
        handler=_explain_relationships,
    ),
    ToolSpec(
        name="interview_agents",
        description="Interview running simulation agents with free-form prompts.",
        input_schema={
            "type": "object",
            "properties": {
                "simulation_id": {"type": "string"},
                "prompts": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "agent_id": {"type": "integer"},
                            "prompt": {"type": "string"},
                        },
                    },
                },
            },
        },
        required=["simulation_id", "prompts"],
        handler=_interview_agents,
    ),
    ToolSpec(
        name="generate_report",
        description="Generate (or retrieve) the analysis report for a simulation.",
        input_schema={
            "type": "object",
            "properties": {"simulation_id": {"type": "string"}},
        },
        required=["simulation_id"],
        handler=_generate_report,
    ),
    ToolSpec(
        name="compare_scenarios",
        description="Side-by-side summary of two or more simulation runs.",
        input_schema={
            "type": "object",
            "properties": {
                "simulation_ids": {"type": "array", "items": {"type": "string"}},
            },
        },
        required=["simulation_ids"],
        handler=_compare_scenarios,
    ),
    ToolSpec(
        name="summarize_findings",
        description="Summarize the findings of a generated report.",
        input_schema={
            "type": "object",
            "properties": {"report_id": {"type": "string"}},
        },
        required=["report_id"],
        handler=_summarize_findings,
    ),
]


def register_all(registry: ToolRegistry) -> None:
    """把全部 MiroFish 工具注册到给定注册表。"""
    for spec in _TOOL_DEFS:
        registry.register(spec)
