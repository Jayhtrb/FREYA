"""
模型适配器抽象基类。

- ``chat(messages)`` 返回一次完整回复。
- ``stream(messages)`` 生成增量文本片段（SSE delta）。

``messages`` 为 ``[{"role": "system"|"user"|"assistant", "content": str}]`` 列表。
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Dict, Iterator, List


@dataclass
class ChatResponse:
    """一次完整回复。"""

    text: str
    model: str
    tool_calls: List[Dict[str, Any]] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "text": self.text,
            "model": self.model,
            "tool_calls": self.tool_calls,
        }


class ModelAdapter(ABC):
    """模型适配器接口。"""

    @property
    @abstractmethod
    def model_name(self) -> str:
        """当前适配器使用的模型标识。"""

    @abstractmethod
    def chat(self, messages: List[Dict[str, str]], **kwargs) -> ChatResponse:
        """生成一次完整回复。"""

    @abstractmethod
    def stream(self, messages: List[Dict[str, str]], **kwargs) -> Iterator[str]:
        """生成增量文本片段。"""
