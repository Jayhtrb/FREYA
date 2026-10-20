"""FREYA 工具包：类型化注册表、分发器与 MiroFish 工具封装。"""

from .registry import ToolSpec, ToolRegistry, get_registry
from .dispatcher import (
    dispatch_tool,
    list_tools,
    ToolNotFoundError,
    ToolArgumentError,
    ToolExecutionError,
)

__all__ = [
    "ToolSpec",
    "ToolRegistry",
    "get_registry",
    "dispatch_tool",
    "list_tools",
    "ToolNotFoundError",
    "ToolArgumentError",
    "ToolExecutionError",
]
