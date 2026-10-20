"""
Persona 加载器。

从 ``persona/freya.persona.json``（仓库根目录）读取 FREYA 人格定义。
文件路径可用 ``FREYA_PERSONA_PATH`` 环境变量覆盖；解析结果按内容缓存。
"""

import json
import os
import threading
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

_DEFAULT_PERSONA_REL = os.path.join(
    os.path.dirname(__file__), "..", "..", "..", "..", "persona", "freya.persona.json"
)

_load_lock = threading.Lock()
_cache: Dict[str, "Persona"] = {}


@dataclass(frozen=True)
class Persona:
    """已加载的人格定义（只读视图 + 便捷访问器）。"""

    raw: Dict[str, Any] = field(default_factory=dict)

    @property
    def name(self) -> str:
        return self.raw.get("identity", {}).get("name", "FREYA")

    @property
    def identity(self) -> Dict[str, Any]:
        return self.raw.get("identity", {})

    @property
    def tagline(self) -> str:
        return self.identity.get("tagline", "")

    @property
    def personality(self) -> Dict[str, Any]:
        return self.raw.get("personality", {})

    @property
    def system_instructions(self) -> List[str]:
        return list(self.raw.get("system_instructions", []))

    @property
    def behavioral_boundaries(self) -> List[str]:
        return list(self.raw.get("behavioral_boundaries", []))

    @property
    def voice(self) -> Dict[str, Any]:
        return self.raw.get("voice", {})

    @property
    def greeting(self) -> Dict[str, Any]:
        return self.identity.get("greeting", {})

    @property
    def model_defaults(self) -> Dict[str, Any]:
        return self.raw.get("model_defaults", {})

    @property
    def unsupported_claims(self) -> List[str]:
        return list(self.raw.get("unsupported_claims", []))

    def public_view(self) -> Dict[str, Any]:
        """面向客户端的公开字段（§9：identity、tagline、greeting、voice states）。"""
        return {
            "name": self.name,
            "acronym": self.identity.get("acronym", ""),
            "tagline": self.tagline,
            "role": self.identity.get("role", ""),
            "voice_presentation": self.identity.get("voice_presentation", ""),
            "greeting": self.greeting,
            "voice": {
                "provider": self.voice.get("provider"),
                "default_android": self.voice.get("default_android"),
                "states": self.voice.get("states", []),
            },
        }


def _persona_path() -> str:
    override = os.environ.get("FREYA_PERSONA_PATH")
    if override:
        return override
    return os.path.abspath(_DEFAULT_PERSONA_REL)


def load_persona(path: Optional[str] = None) -> Persona:
    """读取并解析人格 JSON 文件。"""
    resolved = os.path.abspath(path or _persona_path())
    if not os.path.exists(resolved):
        raise FileNotFoundError(f"Persona file not found: {resolved}")

    with open(resolved, "r", encoding="utf-8") as f:
        raw = json.load(f)

    if not isinstance(raw, dict):
        raise ValueError(f"Persona file is not a JSON object: {resolved}")

    return Persona(raw=raw)


def get_persona() -> Persona:
    """返回缓存的默认人格（按路径缓存）。"""
    path = os.path.abspath(_persona_path())
    with _load_lock:
        if path not in _cache:
            _cache[path] = load_persona(path)
        return _cache[path]
