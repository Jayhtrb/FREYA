"""
按用户密钥端点（BYOK）。

- GET    /api/freya/keys   脱敏状态（set 标志 + base_url/model，绝不含明文密钥）
- PUT    /api/freya/keys   合并写入密钥
- DELETE /api/freya/keys   清空用户密钥
"""

from typing import Any, Dict

from flask import Blueprint, jsonify, request

from ..auth.decorators import require_auth, current_user_id
from . import store

keys_bp = Blueprint("freya_keys", __name__)


def _ok(data: Dict[str, Any], status: int = 200):
    return jsonify({"success": True, "data": data, "error": None}), status


def _err(message: str, status: int = 400):
    return jsonify({"success": False, "data": None, "error": message}), status


@keys_bp.route("", methods=["GET"])
@require_auth
def get_keys():
    return _ok(store.masked(current_user_id()))


@keys_bp.route("", methods=["PUT"])
@require_auth
def put_keys():
    data = request.get_json(silent=True) or {}
    if not isinstance(data, dict):
        return _err("invalid body", 400)
    store.set_keys(current_user_id(), data)
    return _ok(store.masked(current_user_id()))


@keys_bp.route("", methods=["DELETE"])
@require_auth
def delete_keys():
    store.clear_keys(current_user_id())
    return _ok({"ok": True})
