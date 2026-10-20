"""
脱敏日志：绝不记录密码、token、密钥等敏感信息。

提供 ``redact_text`` 纯函数与一个包装标准 logging.Logger 的
``RedactingLogger``，在日志落盘前对消息参数做替换。
"""

import logging
import re
from typing import Any, Optional

# 敏感键名（大小写不敏感）。
_SENSITIVE_KEYS = (
    "password",
    "passwd",
    "token",
    "secret",
    "api_key",
    "apikey",
    "authorization",
    "credential",
    "cookie",
    "private_key",
    "access_key",
    "client_secret",
)

# key=value / key: value / "key": "value" 中的敏感键。
_KV_PATTERN = re.compile(
    r"(?i)(?P<key>(?:authorization|password|passwd|token|secret|api_?key|credential|"
    r"cookie|private_?key|access_?key|client_?secret))\s*[:=]\s*(?P<value>[^,\s\"']+)"
)

# Bearer 令牌。
_BEARER_PATTERN = re.compile(r"(?i)Bearer\s+[A-Za-z0-9._\-]+")

# PBKDF2 哈希字符串。
_PBKDF2_PATTERN = re.compile(r"pbkdf2_sha256\$[^\s\"']+")

# 三段式 JWT。
_JWT_PATTERN = re.compile(
    r"\beyJ[A-Za-z0-9._\-]+\.[A-Za-z0-9._\-]+\.[A-Za-z0-9._\-]+\b"
)


def redact_text(text: Any) -> str:
    """对任意可字符串化对象做敏感信息替换。"""
    if text is None:
        return ""
    if not isinstance(text, str):
        try:
            text = str(text)
        except Exception:
            return "<unprintable>"

    text = _BEARER_PATTERN.sub("Bearer [REDACTED]", text)
    text = _PBKDF2_PATTERN.sub("pbkdf2_sha256$[REDACTED]", text)
    text = _JWT_PATTERN.sub("[REDACTED]", text)
    text = _KV_PATTERN.sub(r"\g<key>=[REDACTED]", text)
    return text


class RedactingLogger:
    """包装标准 logger，所有输出前脱敏。"""

    def __init__(self, name: str = "freya"):
        self._logger = logging.getLogger(name)

    def _redact_args(self, msg: Any, args: Any) -> tuple:
        redacted_msg = redact_text(msg)
        if args:
            redacted_args = tuple(redact_text(a) for a in args)
            return redacted_msg, redacted_args
        return redacted_msg, ()

    def debug(self, msg: Any, *args, **kwargs) -> None:
        m, a = self._redact_args(msg, args)
        self._logger.debug(m, *a, **kwargs)

    def info(self, msg: Any, *args, **kwargs) -> None:
        m, a = self._redact_args(msg, args)
        self._logger.info(m, *a, **kwargs)

    def warning(self, msg: Any, *args, **kwargs) -> None:
        m, a = self._redact_args(msg, args)
        self._logger.warning(m, *a, **kwargs)

    def error(self, msg: Any, *args, **kwargs) -> None:
        m, a = self._redact_args(msg, args)
        self._logger.error(m, *a, **kwargs)

    def exception(self, msg: Any, *args, **kwargs) -> None:
        m, a = self._redact_args(msg, args)
        self._logger.exception(m, *a, **kwargs)


_loggers: dict = {}


def get_redacting_logger(name: str = "freya") -> RedactingLogger:
    if name not in _loggers:
        _loggers[name] = RedactingLogger(name)
    return _loggers[name]
