"""
设置端点（§8）：personality / model / voice 的用户级设置。
"""

import json
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from flask import Blueprint, jsonify, request

from ..auth.decorators import require_auth, current_user_id
from ..db import execute, query_one
from ..persona.loader import get_persona

settings_bp = Blueprint("freya_settings", __name__)

_UPDATABLE = ("personality", "model", "voice")


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _ok(data: Dict[str, Any]):
    return jsonify({"success": True, "data": data, "error": None})


def _err(message: str, status: int = 400):
    return jsonify({"success": False, "data": None, "error": message}), status


def _defaults() -> Dict[str, Any]:
    persona = get_persona()
    assistant = persona.model_defaults.get("assistant", {})
    return {
        "personality": persona.personality,
        "model": {
            "provider": assistant.get("provider", "deepseek"),
            "model": assistant.get("model", "deepseek-chat"),
        },
        "voice": persona.voice,
    }


def _get_settings(user_id: str) -> Dict[str, Any]:
    row = query_one(
        "SELECT personality, model, voice FROM settings WHERE user_id = ?", (user_id,)
    )
    if not row:
        return _defaults()
    return {
        "personality": json.loads(row["personality"]) if row["personality"] else None,
        "model": json.loads(row["model"]) if row["model"] else None,
        "voice": json.loads(row["voice"]) if row["voice"] else None,
    }


def _save_settings(user_id: str, settings: Dict[str, Any]) -> None:
    execute(
        "INSERT INTO settings (user_id, personality, model, voice, updated_at) "
        "VALUES (?, ?, ?, ?, ?) "
        "ON CONFLICT(user_id) DO UPDATE SET "
        "personality = excluded.personality, model = excluded.model, "
        "voice = excluded.voice, updated_at = excluded.updated_at",
        (
            user_id,
            json.dumps(settings.get("personality"), ensure_ascii=False),
            json.dumps(settings.get("model"), ensure_ascii=False),
            json.dumps(settings.get("voice"), ensure_ascii=False),
            _now(),
        ),
    )


@settings_bp.route("", methods=["GET"])
@require_auth
def get_settings():
    return _ok(_get_settings(current_user_id()))


@settings_bp.route("", methods=["PUT"])
@require_auth
def update_settings():
    data = request.get_json(silent=True) or {}
    if not any(key in data for key in _UPDATABLE):
        return _err("no updateable settings provided", 400)

    user_id = current_user_id()
    current = _get_settings(user_id)
    for key in _UPDATABLE:
        if key in data:
            current[key] = data[key]

    _save_settings(user_id, current)
    return _ok(current)
