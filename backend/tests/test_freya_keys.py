"""
按用户密钥保险库测试（BYOK）。

覆盖：初始为空、写入/脱敏回显、静态加密（DB 非明文）、删除、resolve 优先级。
"""

import pytest
from flask import Flask

from app.config import Config
from app.freya import db
from app.freya.api import register_freya_blueprints
from app.freya.keys import store


def _make_app(tmp_path) -> Flask:
    db.set_db_path(str(tmp_path / "freya.sqlite3"))
    db.init_db()
    app = Flask("freya-keys-test")
    app.config.from_object(Config)
    app.config.update(TESTING=True)
    register_freya_blueprints(app)
    return app


@pytest.fixture()
def client(tmp_path):
    return _make_app(tmp_path).test_client()


def _register_and_auth(client):
    body = client.post(
        "/api/freya/auth/register",
        json={"email": "k@example.com", "password": "secret123", "display_name": "K"},
    ).get_json()["data"]
    return {"Authorization": f"Bearer {body['token']}"}, body["user"]["user_id"]


def test_keys_empty_initially(client):
    h, _ = _register_and_auth(client)
    data = client.get("/api/freya/keys", headers=h).get_json()["data"]
    assert data["deepseek"]["set"] is False
    assert data["llm"]["set"] is False
    assert data["zep"]["set"] is False


def test_keys_roundtrip_and_masked(client):
    h, user_id = _register_and_auth(client)

    put = client.put(
        "/api/freya/keys",
        headers=h,
        json={
            "deepseek_api_key": "sk-deepseek-123",
            "llm_api_key": "sk-llm-456",
            "llm_base_url": "https://example.com/v1",
            "llm_model_name": "my-model",
            "zep_api_key": "zep-secret-789",
        },
    )
    assert put.status_code == 200
    masked = put.get_json()["data"]
    assert masked["deepseek"]["set"] is True
    assert masked["llm"]["set"] is True
    assert masked["llm"]["base_url"] == "https://example.com/v1"
    assert masked["llm"]["model"] == "my-model"
    assert masked["zep"]["set"] is True
    # 明文绝不回显
    assert "sk-deepseek-123" not in put.get_data(as_text=True)
    assert "zep-secret-789" not in put.get_data(as_text=True)

    keys = store.get_keys(user_id)
    assert keys["deepseek_api_key"] == "sk-deepseek-123"
    assert keys["zep_api_key"] == "zep-secret-789"

    from app.freya.db import query_one

    row = query_one("SELECT payload FROM user_keys WHERE user_id = ?", (user_id,))
    assert "sk-deepseek-123" not in row["payload"]
    assert "zep-secret-789" not in row["payload"]


def test_keys_delete(client):
    h, user_id = _register_and_auth(client)
    client.put("/api/freya/keys", headers=h, json={"deepseek_api_key": "sk-1"})
    assert client.delete("/api/freya/keys", headers=h).status_code == 200
    assert client.get("/api/freya/keys", headers=h).get_json()["data"]["deepseek"]["set"] is False
    assert store.get_keys(user_id) == {}


def test_resolve_prefers_user_key(client):
    h, user_id = _register_and_auth(client)
    store.set_keys(user_id, {"llm_model_name": "user-model"})
    assert store.resolve(user_id, "llm_model_name", "default-model") == "user-model"
    assert store.resolve(user_id, "missing_field", "default") == "default"
