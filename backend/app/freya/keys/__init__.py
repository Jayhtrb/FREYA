"""
按用户的 API 密钥保险库（BYOK）。

每位用户可在应用内提供自己的 DeepSeek / LLM / Zep 密钥；服务端以
Fernet 加密后持久化（密钥派生自 FREYA_JWT_SECRET），请求级注入使用，
绝不写入 APK 或 .env。
"""

from . import store  # noqa: F401
