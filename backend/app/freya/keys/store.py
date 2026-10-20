"""
按用户密钥存储（Fernet 加密，静态加密）。

加密密钥由 ``FREYA_JWT_SECRET`` 经 SHA-256 派生；因此：
- 生产环境显式设置 FREYA_JWT_SECRET 时，密钥跨重启可解密；
- 开发环境（进程随机 secret）重启后已存密钥不可解密，属可接受行为。
"""

import base64
import hashlib
import json
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from cryptography.fernet import Fernet, InvalidToken

from ...config import Config
from ..db import execute, query_one

# 可写入的字段；secret 字段绝不回显明文。
KEY_FIELDS = ("deepseek_api_key", "llm_api_key", "llm_base_url", "llm_model_name", "zep_api_key")
SECRET_FIELDS = ("deepseek_api_key", "llm_api_key", "zep_api_key")


def _fernet() -> Fernet:
    derived = hashlib.sha256(Config.FREYA_JWT_SECRET.encode("utf-8")).digest()
    return Fernet(base64.urlsafe_b64encode(derived))


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def get_keys(user_id: str) -> Dict[str, Any]:
    """返回用户已存密钥（解密后）；无记录或解密失败返回空 dict。"""
    row = query_one("SELECT payload FROM user_keys WHERE user_id = ?", (user_id,))
    if not row or not row["payload"]:
        return {}
    try:
        plain = _fernet().decrypt(row["payload"].encode("ascii")).decode("utf-8")
        value = json.loads(plain)
        return value if isinstance(value, dict) else {}
    except (InvalidToken, ValueError, json.JSONDecodeError):
        return {}


def set_keys(user_id: str, values: Dict[str, Any]) -> Dict[str, Any]:
    """合并写入用户密钥（仅接受 KEY_FIELDS，覆盖已存在项）。"""
    current = get_keys(user_id)
    for key, value in (values or {}).items():
        if key in KEY_FIELDS and value:
            current[key] = str(value)
    payload = json.dumps(current, ensure_ascii=False)
    encrypted = _fernet().encrypt(payload.encode("utf-8")).decode("ascii")
    execute(
        "INSERT INTO user_keys (user_id, payload, updated_at) VALUES (?, ?, ?) "
        "ON CONFLICT(user_id) DO UPDATE SET "
        "payload = excluded.payload, updated_at = excluded.updated_at",
        (user_id, encrypted, _now()),
    )
    return current


def clear_keys(user_id: str) -> bool:
    execute("DELETE FROM user_keys WHERE user_id = ?", (user_id,))
    return True


def masked(user_id: str) -> Dict[str, Any]:
    """返回脱敏状态（绝不包含密钥明文，仅标记是否已设置 + 非敏感配置）。"""
    keys = get_keys(user_id)
    return {
        "deepseek": {"set": bool(keys.get("deepseek_api_key"))},
        "llm": {
            "set": bool(keys.get("llm_api_key")),
            "base_url": keys.get("llm_base_url") or "",
            "model": keys.get("llm_model_name") or "",
        },
        "zep": {"set": bool(keys.get("zep_api_key"))},
    }


def resolve(user_id: str, field: str, default: Optional[str]) -> Optional[str]:
    """解析某字段：用户密钥优先，缺失回退默认值。"""
    return get_keys(user_id).get(field) or default
