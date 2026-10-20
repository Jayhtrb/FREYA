"""
认证端点（§2）。

注册于 ``/api/freya/auth``：
- POST /register
- POST /login
- GET  /me
- POST /logout（无状态；客户端丢弃 token）
"""

from flask import Blueprint, jsonify, request

from .decorators import require_auth, current_user_id
from .security import issue_token
from .store import EmailTakenError, get_user_store

auth_bp = Blueprint("freya_auth", __name__)


def _ok(data):
    return jsonify({"success": True, "data": data, "error": None})


def _err(message, status=400):
    return jsonify({"success": False, "data": None, "error": message}), status


def _token_and_user(user):
    token = issue_token(user["user_id"])
    return {"token": token, "user": user}


@auth_bp.route("/register", methods=["POST"])
def register():
    data = request.get_json(silent=True) or {}
    email = data.get("email")
    password = data.get("password")
    display_name = data.get("display_name", "")

    if not email or not password:
        return _err("email and password are required", 400)

    try:
        user = get_user_store().create_user(email, password, display_name)
    except EmailTakenError:
        return _err("email already registered", 409)
    except ValueError as exc:
        return _err(str(exc), 400)

    return _ok(_token_and_user(user)), 201


@auth_bp.route("/login", methods=["POST"])
def login():
    data = request.get_json(silent=True) or {}
    email = data.get("email")
    password = data.get("password")

    if not email or not password:
        return _err("email and password are required", 400)

    user = get_user_store().authenticate(email, password)
    if not user:
        return _err("invalid email or password", 401)

    return _ok(_token_and_user(user))


@auth_bp.route("/me", methods=["GET"])
@require_auth
def me():
    user = get_user_store().get(current_user_id())
    if not user:
        return _err("user not found", 404)
    return _ok({"user": user})


@auth_bp.route("/logout", methods=["POST"])
@require_auth
def logout():
    # JWT 为无状态 token；服务端不维护会话，客户端自行丢弃。
    return _ok({"ok": True})
