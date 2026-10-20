"""FREYA 模型适配器包。"""

from .base import ModelAdapter, ChatResponse
from .openai_compat import OpenAICompatAdapter
from .deepseek import DeepSeekAdapter
from .mock import MockAdapter
from .factory import get_adapter

__all__ = [
    "ModelAdapter",
    "ChatResponse",
    "OpenAICompatAdapter",
    "DeepSeekAdapter",
    "MockAdapter",
    "get_adapter",
]
