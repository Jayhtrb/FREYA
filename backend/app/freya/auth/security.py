"""
密码哈希与 JWT 签发/校验。

密码使用标准库 PBKDF2-HMAC-SHA256（无第三方编译依赖；契约允许
bcrypt/argon2，此处选择可离线安装且无本地编译的 stdlib 实现）。
JWT 使用 PyJWT，HS256 签名，密钥来自 ``FREYA_JWT_SECRET``（回退 ``SECRET_KEY``）。
"""

import base64
import hashlib
import hmac
import secrets
import time
from typing import Optional

import jwt

from ...config import Config

PBKDF2_ALGO = "pbkdf2_sha256"
PBKDF2_ITERATIONS = 260_000
JWT_ALGORITHM = "HS256"


def hash_password(password: str) -> str:
    """生成带盐 PBKDF2 哈希字符串（格式：pbkdf2_sha256$iter$salt$hash）。"""
    salt = secrets.token_bytes(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS)
    return "$".join(
        [
            PBKDF2_ALGO,
            str(PBKDF2_ITERATIONS),
            base64.b64encode(salt).decode("ascii"),
            base64.b64encode(dk).decode("ascii"),
        ]
    )


def verify_password(password: str, stored: str) -> bool:
    """以常数时间比较校验密码。任何格式错误都返回 False。"""
    try:
        algo, iterations_s, salt_s, hash_s = stored.split("$", 3)
        if algo != PBKDF2_ALGO:
            return False
        iterations = int(iterations_s)
        salt = base64.b64decode(salt_s)
        expected = base64.b64decode(hash_s)
    except Exception:
        return False

    dk = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, iterations)
    return hmac.compare_digest(dk, expected)


def _jwt_secret() -> str:
    return getattr(Config, "FREYA_JWT_SECRET", None) or Config.SECRET_KEY


def issue_token(user_id: str, ttl_seconds: Optional[int] = None) -> str:
    """签发 JWT，sub 为用户 ID。"""
    ttl = ttl_seconds if ttl_seconds is not None else getattr(
        Config, "FREYA_JWT_TTL_SECONDS", 604800
    )
    now = int(time.time())
    payload = {"sub": user_id, "iat": now, "exp": now + ttl}
    return jwt.encode(payload, _jwt_secret(), algorithm=JWT_ALGORITHM)


def verify_token(token: str) -> Optional[str]:
    """校验 JWT，成功返回 user_id，失败/过期返回 None。"""
    try:
        payload = jwt.decode(token, _jwt_secret(), algorithms=[JWT_ALGORITHM])
    except jwt.PyJWTError:
        return None
    return payload.get("sub")
