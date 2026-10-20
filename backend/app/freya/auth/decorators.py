"""
认证装饰器与当前用户解析。

- ``require_auth``：保护 /api/freya/* 端点，校验 Bearer JWT，设置 ``g.freya_user_id``。
- ``current_user_id``：从请求上下文读取已认证用户。
"""

from functools import wraps
from typing import Optional

from flask import g, jsonify, request

from .security import verify_token


def extract_bearer_token() -> Optional[str]:
    """从 Authorization 头提取 Bearer token。"""
    header = request.headers.get("Authorization", "")
    if header.startswith("Bearer "):
        token = header[7:].strip()
        return token or None
    return None


def current_user_id() -> Optional[str]:
    """返回当前请求已认证的用户 ID（未认证为 None）。"""
    return getattr(g, "freya_user_id", None)


def authenticate_request() -> Optional[str]:
    """校验当前请求的 JWT，成功返回 user_id，否则 None。"""
    token = extract_bearer_token()
    if not token:
        return None
    return verify_token(token)


def require_auth(fn):
    """要求有效 JWT 的装饰器。"""

    @wraps(fn)
    def wrapper(*args, **kwargs):
        user_id = authenticate_request()
        if not user_id:
            return (
                jsonify(
                    {
                        "success": False,
                        "data": None,
                        "error": "Authentication required",
                    }
                ),
                401,
            )
        g.freya_user_id = user_id
        return fn(*args, **kwargs)

    return wrapper
