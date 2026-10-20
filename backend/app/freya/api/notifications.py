"""
通知与任务端点（§7）。

- GET  /api/freya/notifications
- POST /api/freya/notifications/<id>/read
- GET  /api/freya/jobs
- GET  /api/freya/jobs/<id>
"""

from typing import Any, Dict

from flask import Blueprint, jsonify, request

from ..auth.decorators import require_auth, current_user_id
from ..jobs.store import JobStore

notifications_bp = Blueprint("freya_notifications", __name__)

_store = JobStore()


def _ok(data: Dict[str, Any], status: int = 200):
    return jsonify({"success": True, "data": data, "error": None}), status


def _err(message: str, status: int = 400):
    return jsonify({"success": False, "data": None, "error": message}), status


@notifications_bp.route("/notifications", methods=["GET"])
@require_auth
def list_notifications():
    limit = request.args.get("limit", 50, type=int)
    offset = request.args.get("offset", 0, type=int)
    return _ok(
        {"notifications": _store.list_notifications(current_user_id(), limit, offset)}
    )


@notifications_bp.route("/notifications/<notification_id>/read", methods=["POST"])
@require_auth
def mark_notification_read(notification_id: str):
    marked = _store.mark_read(current_user_id(), notification_id)
    if not marked:
        return _err("notification not found", 404)
    return _ok({"ok": True})


@notifications_bp.route("/jobs", methods=["GET"])
@require_auth
def list_jobs():
    limit = request.args.get("limit", 20, type=int)
    offset = request.args.get("offset", 0, type=int)
    return _ok({"jobs": _store.list_jobs(current_user_id(), limit, offset)})


@notifications_bp.route("/jobs/<job_id>", methods=["GET"])
@require_auth
def get_job(job_id: str):
    job = _store.get_job(current_user_id(), job_id)
    if not job:
        return _err("job not found", 404)
    return _ok({"job": job})
