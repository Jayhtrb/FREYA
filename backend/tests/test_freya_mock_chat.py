"""
Mock 适配器与聊天端点测试（§3）。

使用 mock 适配器（无密钥、无网络、确定性），最小 FREYA 应用。
"""

import pytest
from flask import Flask

from app.config import Config
from app.freya import db
from app.freya.api import register_freya_blueprints
from app.freya.adapters.mock import MockAdapter
from app.freya.adapters.factory import get_adapter
from app.freya.observability.rate_limit import get_rate_limiter


def _make_app(tmp_path) -> Flask:
    db.set_db_path(str(tmp_path / "freya.sqlite3"))
    db.init_db()
    get_rate_limiter().limit = 1000
    app = Flask("freya-test")
    app.config.from_object(Config)
    app.config.update(TESTING=True)
    register_freya_blueprints(app)
    return app


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(Config, "DEEPSEEK_API_KEY", None)
    return _make_app(tmp_path).test_client()


def _token(client):
    resp = client.post(
        "/api/freya/auth/register",
        json={"email": "u@example.com", "password": "pw123456", "display_name": "U"},
    )
    return resp.get_json()["data"]["token"]


def test_mock_adapter_is_deterministic():
    adapter = MockAdapter()
    messages = [{"role": "user", "content": "hello"}]
    first = adapter.chat(messages).text
    second = adapter.chat(messages).text
    assert first == second
    assert "FREYA" in first
    assert adapter.model_name == "freya-mock"


def test_factory_returns_mock_when_no_key(monkeypatch):
    monkeypatch.setattr(Config, "DEEPSEEK_API_KEY", None)
    adapter = get_adapter()
    assert adapter.model_name == "freya-mock"


def test_chat_endpoint_non_streaming(client):
    token = _token(client)
    resp = client.post(
        "/api/freya/chat",
        json={"message": "hello"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 200
    data = resp.get_json()["data"]
    assert data["conversation_id"].startswith("conv_")
    assert data["reply"]
    assert data["model"] == "freya-mock"
    assert data["finished"] is True


def test_chat_endpoint_streaming(client):
    token = _token(client)
    resp = client.post(
        "/api/freya/chat/stream",
        json={"message": "hello"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 200
    assert resp.mimetype == "text/event-stream"
    body = resp.get_data(as_text=True)
    assert "event: delta" in body
    assert "event: done" in body
    assert "FREYA" in body


def test_conversation_crud(client):
    token = _token(client)
    headers = {"Authorization": f"Bearer {token}"}

    chat = client.post(
        "/api/freya/chat", json={"message": "hello"}, headers=headers
    ).get_json()["data"]
    conv_id = chat["conversation_id"]

    listing = client.get("/api/freya/conversations", headers=headers)
    assert listing.status_code == 200
    ids = [c["conversation_id"] for c in listing.get_json()["data"]["conversations"]]
    assert conv_id in ids

    detail = client.get(f"/api/freya/conversations/{conv_id}", headers=headers)
    assert detail.status_code == 200
    messages = detail.get_json()["data"]["conversation"]["messages"]
    roles = [m["role"] for m in messages]
    assert "user" in roles and "assistant" in roles

    deleted = client.delete(f"/api/freya/conversations/{conv_id}", headers=headers)
    assert deleted.status_code == 200

    gone = client.get(f"/api/freya/conversations/{conv_id}", headers=headers)
    assert gone.status_code == 404


def test_chat_requires_auth(client):
    resp = client.post("/api/freya/chat", json={"message": "hi"})
    assert resp.status_code == 401
