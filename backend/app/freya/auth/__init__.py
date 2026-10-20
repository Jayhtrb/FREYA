"""FREYA 认证包：JWT、密码哈希、用户存储、装饰器与端点。"""

from .security import hash_password, verify_password, issue_token, verify_token
from .store import UserStore, UserNotFoundError, EmailTakenError
from .decorators import require_auth, current_user_id
from .routes import auth_bp

__all__ = [
    "hash_password",
    "verify_password",
    "issue_token",
    "verify_token",
    "UserStore",
    "UserNotFoundError",
    "EmailTakenError",
    "require_auth",
    "current_user_id",
    "auth_bp",
]
