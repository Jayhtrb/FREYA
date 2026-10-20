"""
模型适配器测试（无网络、无真实密钥）。

覆盖 OpenAI 兼容适配器（DeepSeek 助手路径）的：
- 请求构造（model / messages / stream 标志）
- 响应解析与工具调用提取
- 流式增量解析（跳过空 choices / 空 content）
以及工厂在有无 DEEPSEEK_API_KEY 时的适配器选择。
"""

from types import SimpleNamespace

from app.config import Config
from app.freya.adapters import factory
from app.freya.adapters.deepseek import DeepSeekAdapter
from app.freya.adapters.mock import MockAdapter
from app.freya.adapters.openai_compat import OpenAICompatAdapter


def _msg(content, tool_calls=None):
    return SimpleNamespace(content=content, tool_calls=tool_calls)


def _completion(content, tool_calls=None):
    return SimpleNamespace(choices=[SimpleNamespace(message=_msg(content, tool_calls))])


def _adapter_with_fake(fake_create, model="deepseek-chat"):
    adapter = OpenAICompatAdapter(
        api_key="fake-key", base_url="https://api.deepseek.com/v1", model=model
    )
    adapter._client = SimpleNamespace(
        chat=SimpleNamespace(completions=SimpleNamespace(create=fake_create))
    )
    return adapter


def test_chat_roundtrip_and_request_args():
    captured = {}

    def fake_create(**kwargs):
        captured.update(kwargs)
        return _completion("hello from deepseek")

    adapter = _adapter_with_fake(fake_create)
    resp = adapter.chat([{"role": "user", "content": "hi"}])

    assert resp.text == "hello from deepseek"
    assert resp.model == "deepseek-chat"
    assert resp.tool_calls == []
    assert captured["model"] == "deepseek-chat"
    assert captured["stream"] is False
    assert captured["messages"] == [{"role": "user", "content": "hi"}]


def test_chat_extracts_tool_calls():
    def fake_create(**kwargs):
        tc = SimpleNamespace(
            function=SimpleNamespace(name="run_simulation", arguments='{"project_id":"p1"}')
        )
        return _completion("ok", tool_calls=[tc])

    adapter = _adapter_with_fake(fake_create)
    resp = adapter.chat([{"role": "user", "content": "run it"}])
    assert resp.tool_calls == [{"name": "run_simulation", "arguments": '{"project_id":"p1"}'}]


def test_stream_yields_only_nonempty_deltas():
    def fake_create(**kwargs):
        assert kwargs["stream"] is True
        return iter(
            [
                SimpleNamespace(choices=[SimpleNamespace(delta=_msg("Hello"))]),
                SimpleNamespace(choices=[SimpleNamespace(delta=_msg(" world"))]),
                SimpleNamespace(choices=[]),
                SimpleNamespace(choices=[SimpleNamespace(delta=SimpleNamespace(content=None))]),
            ]
        )

    adapter = _adapter_with_fake(fake_create)
    assert list(adapter.stream([{"role": "user", "content": "hi"}])) == ["Hello", " world"]


def test_factory_selects_mock_without_key(monkeypatch):
    monkeypatch.setattr(Config, "DEEPSEEK_API_KEY", None)
    assert isinstance(factory.get_adapter(), MockAdapter)


def test_factory_selects_deepseek_with_key(monkeypatch):
    monkeypatch.setattr(Config, "DEEPSEEK_API_KEY", "sk-test")
    assert isinstance(factory.get_adapter(), DeepSeekAdapter)
