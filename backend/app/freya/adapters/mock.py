"""
Mock 适配器（确定性、人格一致、无网络）。

当未配置 ``DEEPSEEK_API_KEY`` 时由工厂选用，保证测试在无密钥、
无网络环境下可运行。回复文本对相同输入完全确定。
"""

from typing import Dict, Iterator, List

from .base import ChatResponse, ModelAdapter

MODEL_NAME = "freya-mock"

_STREAM_CHUNK = 24


def _deterministic_reply(text: str) -> str:
    """基于用户最后一条消息生成确定、人格一致的回复。"""
    lowered = text.lower()

    if "greet" in lowered or "hello" in lowered or "hi" in lowered:
        return (
            "I'm FREYA — Flexible Reasoning Engine for Your Applications. "
            "Online and ready. Where shall we begin?"
        )
    if "simulate" in lowered or "simulation" in lowered:
        return (
            "I can orchestrate a MiroFish simulation for you. Provide a project and "
            "a simulation requirement, and I'll route it through the engine. Note that "
            "simulation outputs are model-generated and labeled as such."
        )
    if "report" in lowered:
        return (
            "Reports are generated from a completed simulation. Share a simulation id "
            "and I'll retrieve or produce its findings."
        )
    if "memory" in lowered or "preference" in lowered:
        return (
            "I keep per-user preferences and project context locally, and I refuse to "
            "store credentials or other sensitive values."
        )
    return (
        "Acknowledged. As your operator, I'll organize this into an actionable next step "
        "while keeping facts, assumptions, and simulations clearly separated."
    )


class MockAdapter(ModelAdapter):
    """确定性 mock 适配器。"""

    @property
    def model_name(self) -> str:
        return MODEL_NAME

    def _last_user_text(self, messages: List[Dict[str, str]]) -> str:
        for message in reversed(messages):
            if message.get("role") == "user":
                return message.get("content", "") or ""
        return ""

    def chat(self, messages: List[Dict[str, str]], **kwargs) -> ChatResponse:
        text = _deterministic_reply(self._last_user_text(messages))
        return ChatResponse(text=text, model=MODEL_NAME, tool_calls=[])

    def stream(self, messages: List[Dict[str, str]], **kwargs) -> Iterator[str]:
        text = _deterministic_reply(self._last_user_text(messages))
        for i in range(0, len(text), _STREAM_CHUNK):
            yield text[i : i + _STREAM_CHUNK]
