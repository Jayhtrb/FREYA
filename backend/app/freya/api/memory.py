"""
记忆与偏好端点（§5）。

- GET    /api/freya/memory
- PUT    /api/freya/memory/preferences
- DELETE /api/freya/memory/preferences/<key>
- GET    /api/freya/memory/search
"""

from typing import Any, Dict

from flask import Blueprint, jsonify, request

from ..auth.decorators import require_auth, current_user_id
from ..memory.store import MemoryStore, SensitiveKeyError, GLOBAL_SCOPE

memory_bp = Blueprint("freya_memory", __name__)

_store = MemoryStore()


def _ok(data: Dict[str, Any], status: int = 200):
    return jsonify({"success": True, "data": data, "error": None}), status


def _err(message: str, status: int = 400):
    return jsonify({"success": False, "data": None, "error": message}), status


@memory_bp.route("", methods=["GET"])
@require_auth
def get_memory():
    user_id = current_user_id()
    return _ok(
        {
            "preferences": _store.get_preferences(user_id),
            "project_context": _store.get_project_context(user_id),
        }
    )


@memory_bp.route("/preferences", methods=["PUT"])
@require_auth
def put_preference():
    data = request.get_json(silent=True) or {}
    key = data.get("key")
    value = data.get("value")
    project_id = data.get("project_id", GLOBAL_SCOPE)

    if key is None or value is None:
        return _err("key and value are required", 400)

    try:
        _store.set_preference(current_user_id(), str(key), value, project_id)
    except SensitiveKeyError as exc:
        return _err(str(exc), 400)

    return _ok({"ok": True, "key": key})


@memory_bp.route("/preferences/<path:key>", methods=["DELETE"])
@require_auth
def delete_preference(key: str):
    project_id = request.args.get("project_id", GLOBAL_SCOPE)
    deleted = _store.delete_preference(current_user_id(), key, project_id)
    if not deleted:
        return _err("preference not found", 404)
    return _ok({"ok": True})


@memory_bp.route("/search", methods=["GET"])
@require_auth
def search_memory():
    q = request.args.get("q", "")
    results = _store.search(current_user_id(), q)
    return _ok({"results": results})
