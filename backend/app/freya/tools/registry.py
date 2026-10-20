"""
类型化工具注册表。

每个工具暴露 ``name`` / ``description`` / ``input_schema``（JSON-Schema 风格），
以及一个可调用实现 ``handler(args, user_id)``。
"""

from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Optional


@dataclass
class ToolSpec:
    name: str
    description: str
    input_schema: Dict[str, Any]
    handler: Callable[[Dict[str, Any], Optional[str]], Dict[str, Any]]
    required: List[str] = field(default_factory=list)

    def public_view(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "input_schema": self.input_schema,
        }


class ToolRegistry:
    """按名称注册工具。"""

    def __init__(self) -> None:
        self._tools: Dict[str, ToolSpec] = {}

    def register(self, spec: ToolSpec) -> None:
        self._tools[spec.name] = spec

    def get(self, name: str) -> Optional[ToolSpec]:
        return self._tools.get(name)

    def list(self) -> List[ToolSpec]:
        return list(self._tools.values())

    def public_list(self) -> List[Dict[str, Any]]:
        return [t.public_view() for t in self._tools.values()]


_registry: Optional[ToolRegistry] = None


def get_registry() -> ToolRegistry:
    global _registry
    if _registry is None:
        _registry = ToolRegistry()
        from . import mirofish_tools  # noqa: F401  (副作用：注册 MiroFish 工具)

        mirofish_tools.register_all(_registry)
    return _registry
