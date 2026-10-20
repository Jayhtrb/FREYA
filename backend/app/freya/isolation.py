"""
现有引擎接口（/api/graph、/api/simulation、/api/report）的按用户隔离（§10）。

设计约束：不改写引擎逻辑，仅以 ``before_request`` 钩子注入 JWT 门禁与所有权
校验；跨用户资源访问返回 404（绝不 403，避免存在性泄露）。

- 无 token 且 ``FREYA_REQUIRE_AUTH`` 关闭时（默认，向后兼容既有引擎测试/前端）：
  不设 ``g.freya_user_id``，不强制隔离。
- 携带有效 token 时：始终设置 ``g.freya_user_id`` 并按用户校验所有权。
- 携带无效 token 时：始终 401。
"""

from typing import Optional

from flask import current_app, g, jsonify, request

from .auth.decorators import extract_bearer_token, current_user_id
from .auth.security import verify_token


def _enforce_auth() -> bool:
    return bool(current_app.config.get("FREYA_REQUIRE_AUTH", False))


def engine_jwt_gate():
    """before_request：校验 JWT（存在则解析，无效则 401）。"""
    token = extract_bearer_token()
    if not token:
        if _enforce_auth():
            return (
                jsonify({"success": False, "data": None, "error": "Authentication required"}),
                401,
            )
        return None

    user_id = verify_token(token)
    if not user_id:
        return (
            jsonify({"success": False, "data": None, "error": "Invalid or expired token"}),
            401,
        )
    g.freya_user_id = user_id
    return None


def _project_user_id(project) -> Optional[str]:
    return getattr(project, "user_id", None)


def _owner_for_project(project_id: str) -> Optional[str]:
    from ..models.project import ProjectManager

    project = ProjectManager.get_project(project_id)
    if not project:
        return None
    return _project_user_id(project)


def _owner_for_graph(graph_id: str) -> Optional[str]:
    from ..models.project import ProjectManager

    projects = ProjectManager.find_projects_by_graph_id(graph_id)
    for project in projects:
        owner = _project_user_id(project)
        if owner:
            return owner
    return None


def _owner_for_simulation(simulation_id: str) -> Optional[str]:
    from ..services.simulation_manager import SimulationManager

    state = SimulationManager().get_simulation(simulation_id)
    if not state:
        return None
    return _owner_for_project(state.project_id)


def _owner_for_report(report_id: str) -> Optional[str]:
    from ..services.report_agent import ReportManager

    report = ReportManager.get_report(report_id)
    if not report:
        return None
    return _owner_for_simulation(report.simulation_id)


def _resolve_owner(blueprint: str, ids: dict) -> Optional[str]:
    """从资源 ID（路径或请求体）解析所有者；无法确定时返回 None。"""
    if not ids:
        return None

    try:
        if blueprint == "graph":
            project_id = ids.get("project_id")
            if project_id:
                return _owner_for_project(project_id)
            graph_id = ids.get("graph_id")
            if graph_id:
                return _owner_for_graph(graph_id)
        elif blueprint == "simulation":
            simulation_id = ids.get("simulation_id")
            if simulation_id:
                return _owner_for_simulation(simulation_id)
            graph_id = ids.get("graph_id")
            if graph_id:
                return _owner_for_graph(graph_id)
        elif blueprint == "report":
            report_id = ids.get("report_id")
            if report_id:
                return _owner_for_report(report_id)
            simulation_id = ids.get("simulation_id")
            if simulation_id:
                return _owner_for_simulation(simulation_id)
    except Exception:
        # 解析失败不应阻断请求；由具体 handler 返回自身错误。
        return None
    return None


_ID_KEYS = ("project_id", "graph_id", "simulation_id", "report_id")


def _collect_ids() -> dict:
    """合并路径参数与 JSON 请求体中的资源 ID（只取已知键）。"""
    ids = dict(request.view_args or {})
    body = request.get_json(silent=True)
    if isinstance(body, dict):
        for key in _ID_KEYS:
            if key in body and key not in ids:
                ids[key] = body[key]
    return ids


def engine_ownership_gate():
    """before_request：已认证用户跨用户访问资源时返回 404。"""
    user_id = current_user_id()
    if not user_id:
        return None

    owner = _resolve_owner(request.blueprint, _collect_ids())
    if owner is not None and owner != user_id:
        return (
            jsonify({"success": False, "data": None, "error": "Resource not found"}),
            404,
        )
    return None


def install_engine_isolation(graph_bp, simulation_bp, report_bp) -> None:
    """在三个引擎蓝图挂载 JWT 门禁与所有权校验。"""
    for bp in (graph_bp, simulation_bp, report_bp):
        bp.before_request(engine_jwt_gate)
        bp.before_request(engine_ownership_gate)
