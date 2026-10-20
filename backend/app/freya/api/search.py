"""
全局搜索端点（§6）。

- GET /api/freya/search?q=&scope=  scope ∈ all|conversations|projects|simulations|reports

返回 ``{ type, id, title, snippet, updated_at }`` 的按用户隔离匹配。
"""

from typing import Any, Dict, List

from flask import Blueprint, jsonify, request

from ..auth.decorators import require_auth, current_user_id
from ..db import query_all

search_bp = Blueprint("freya_search", __name__)

VALID_SCOPES = {"all", "conversations", "projects", "simulations", "reports"}


def _ok(data: Dict[str, Any]):
    return jsonify({"success": True, "data": data, "error": None})


def _snippet(text: str, max_len: int = 160) -> str:
    collapsed = " ".join((text or "").split())
    return collapsed[:max_len]


def _search_conversations(user_id: str, q: str) -> List[Dict[str, Any]]:
    like = f"%{q}%"
    rows = query_all(
        "SELECT c.conversation_id AS id, c.title, c.updated_at, m.content "
        "FROM messages m JOIN conversations c ON c.conversation_id = m.conversation_id "
        "WHERE c.user_id = ? AND (m.content LIKE ? OR c.title LIKE ?) "
        "ORDER BY c.updated_at DESC",
        (user_id, like, like),
    )
    seen = set()
    results = []
    for r in rows:
        if r["id"] in seen:
            continue
        seen.add(r["id"])
        results.append(
            {
                "type": "conversation",
                "id": r["id"],
                "title": r["title"] or "",
                "snippet": _snippet(r["content"]),
                "updated_at": r["updated_at"],
            }
        )
    return results


def _search_projects(user_id: str, q: str) -> List[Dict[str, Any]]:
    from ...models.project import ProjectManager

    ql = q.lower()
    results = []
    for project in ProjectManager.list_projects(limit=None):
        if getattr(project, "user_id", None) != user_id:
            continue
        if ql in (project.name or "").lower() or ql in project.project_id.lower():
            results.append(
                {
                    "type": "project",
                    "id": project.project_id,
                    "title": project.name,
                    "snippet": _snippet(project.analysis_summary or project.simulation_requirement or ""),
                    "updated_at": project.updated_at,
                }
            )
    return results


def _search_simulations(user_id: str, q: str) -> List[Dict[str, Any]]:
    from ...models.project import ProjectManager
    from ...services.simulation_manager import SimulationManager

    ql = q.lower()
    owned_projects = {
        p.project_id for p in ProjectManager.list_projects(limit=None)
        if getattr(p, "user_id", None) == user_id
    }
    results = []
    for state in SimulationManager().list_simulations():
        if state.project_id not in owned_projects:
            continue
        if ql in state.simulation_id.lower() or ql in state.status.value.lower():
            results.append(
                {
                    "type": "simulation",
                    "id": state.simulation_id,
                    "title": state.simulation_id,
                    "snippet": f"status={state.status.value} entities={state.entities_count}",
                    "updated_at": state.updated_at,
                }
            )
    return results


def _search_reports(user_id: str, q: str) -> List[Dict[str, Any]]:
    from ...models.project import ProjectManager
    from ...services.simulation_manager import SimulationManager
    from ...services.report_agent import ReportManager

    ql = q.lower()
    owned_projects = {
        p.project_id for p in ProjectManager.list_projects(limit=None)
        if getattr(p, "user_id", None) == user_id
    }
    manager = SimulationManager()
    results = []
    for report in ReportManager.list_reports(limit=None):
        state = manager.get_simulation(report.simulation_id)
        if not state or state.project_id not in owned_projects:
            continue
        title = report.outline.title if report.outline else report.report_id
        summary = report.outline.summary if report.outline else ""
        if ql in title.lower() or ql in summary.lower() or ql in report.report_id.lower():
            results.append(
                {
                    "type": "report",
                    "id": report.report_id,
                    "title": title,
                    "snippet": _snippet(summary),
                    "updated_at": report.completed_at or report.created_at,
                }
            )
    return results


@search_bp.route("", methods=["GET"])
@require_auth
def search():
    q = request.args.get("q", "")
    scope = request.args.get("scope", "all")
    if scope not in VALID_SCOPES:
        scope = "all"

    user_id = current_user_id()
    results: List[Dict[str, Any]] = []
    if not q:
        return _ok({"results": results})

    if scope in ("all", "conversations"):
        results.extend(_search_conversations(user_id, q))
    if scope in ("all", "projects"):
        results.extend(_search_projects(user_id, q))
    if scope in ("all", "simulations"):
        results.extend(_search_simulations(user_id, q))
    if scope in ("all", "reports"):
        results.extend(_search_reports(user_id, q))

    return _ok({"results": results})
