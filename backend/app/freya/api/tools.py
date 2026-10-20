"""
工具编排端点（§4）。

- GET  /api/freya/tools  列出工具
- POST /api/freya/tools  直接执行工具
"""

from typing import Any, Dict

from flask import Blueprint, jsonify, request

from ..auth.decorators import require_auth, current_user_id
from ..tools.dispatcher import (
    dispatch_tool,
    list_tools,
    ToolError,
)

tools_bp = Blueprint("freya_tools", __name__)


def _ok(data: Dict[str, Any], status: int = 200):
    return jsonify({"success": True, "data": data, "error": None}), status


def _err(message: str, status: int = 400):
    return jsonify({"success": False, "data": None, "error": message}), status


@tools_bp.route("", methods=["GET"])
@require_auth
def list_tools_endpoint():
    return _ok({"tools": list_tools()})


@tools_bp.route("", methods=["POST"])
@require_auth
def execute_tool():
    data = request.get_json(silent=True) or {}
    name = data.get("tool")
    args = data.get("args") or {}

    if not name:
        return _err("tool is required", 400)

    try:
        result = dispatch_tool(name, args, current_user_id())
    except ToolError as exc:
        return _err(str(exc), 400)

    return _ok({"tool": name, "result": result})
