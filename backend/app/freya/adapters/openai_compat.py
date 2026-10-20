"""
OpenAI 兼容适配器。

使用 OpenAI SDK 调用任意 OpenAI 兼容 API（含 DeepSeek）。
"""

from typing import Any, Dict, Iterator, List, Optional

from openai import OpenAI

from .base import ChatResponse, ModelAdapter


class OpenAICompatAdapter(ModelAdapter):
    """基于 OpenAI SDK 的聊天补全适配器。"""

    def __init__(
        self,
        api_key: str,
        base_url: Optional[str] = None,
        model: str = "gpt-4o-mini",
        timeout: float = 60.0,
    ):
        self._model = model
        self._client = OpenAI(
            api_key=api_key,
            base_url=base_url or None,
            timeout=timeout,
        )

    @property
    def model_name(self) -> str:
        return self._model

    def chat(self, messages: List[Dict[str, str]], **kwargs) -> ChatResponse:
        completion = self._client.chat.completions.create(
            model=self._model,
            messages=messages,
            stream=False,
        )
        choice = completion.choices[0]
        text = choice.message.content or ""
        tool_calls = []
        if choice.message.tool_calls:
            tool_calls = [
                {
                    "name": tc.function.name,
                    "arguments": tc.function.arguments,
                }
                for tc in choice.message.tool_calls
            ]
        return ChatResponse(text=text, model=self._model, tool_calls=tool_calls)

    def stream(self, messages: List[Dict[str, str]], **kwargs) -> Iterator[str]:
        stream = self._client.chat.completions.create(
            model=self._model,
            messages=messages,
            stream=True,
        )
        for chunk in stream:
            if not chunk.choices:
                continue
            delta = chunk.choices[0].delta
            if delta and delta.content:
                yield delta.content
