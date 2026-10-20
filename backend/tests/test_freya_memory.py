"""
记忆与偏好端点测试（§5）。

覆盖：偏好 upsert/删除、项目上下文、搜索、敏感键拒绝、按用户隔离。
存储层直接测试 + 最小 FREYA 应用 HTTP 测试。
"""

import pytest
from flask import Flask

from app.config import Config
from app.freya import db
from app.freya.api import register_freya_blueprints
from app.freya.memory.store import MemoryStore, SensitiveKeyError, is_sensitive_key


@pytest.fixture()
def store(tmp_path):
    db.set_db_path(str(tmp_path / "freya.sqlite3"))
    db.init_db()
    return MemoryStore()


def _make_app(tmp_path) -> Flask:
    db.set_db_path(str(tmp_path / "freya.sqlite3"))
    db.init_db()
    app = Flask("freya-test")
    app.config.from_object(Config)
    app.config.update(TESTING=True)
    register_freya_blueprints(app)
    return app


@pytest.fixture()
def client(tmp_path):
    return _make_app(tmp_path).test_client()


def test_is_sensitive_key():
    assert is_sensitive_key("api_key")
    assert is_sensitive_key("PASSWORD")
    assert is_sensitive_key("client_secret")
    assert not is_sensitive_key("theme")
    assert not is_sensitive_key("language_preference")


def test_preference_crud_and_search(store):
    store.set_preference("user_1", "theme", "dark")
    store.set_preference("user_1", "language", "en")
    store.set_preference("user_1", "focus", ["simulation"], project_id="proj_1")

    assert store.get_preference("user_1", "theme") == "dark"
    assert store.get_preferences("user_1") == {"theme": "dark", "language": "en"}
    assert store.get_project_context("user_1") == {"proj_1": {"focus": ["simulation"]}}

    results = store.search("user_1", "dark")
    assert any(r["key"] == "theme" for r in results)

    assert store.delete_preference("user_1", "theme") is True
    assert store.delete_preference("user_1", "theme") is False


def test_sensitive_key_refused(store):
    with pytest.raises(SensitiveKeyError):
        store.set_preference("user_1", "api_key", "sk-123")


def test_memory_is_per_user(store):
    store.set_preference("user_a", "theme", "dark")
    store.set_preference("user_b", "theme", "light")
    assert store.get_preferences("user_a") == {"theme": "dark"}
    assert store.get_preferences("user_b") == {"theme": "light"}


def test_memory_http_endpoints(client):
    token = client.post(
        "/api/freya/auth/register",
        json={"email": "m@example.com", "password": "pw123456", "display_name": "M"},
    ).get_json()["data"]["token"]
    headers = {"Authorization": f"Bearer {token}"}

    put = client.put(
        "/api/freya/memory/preferences",
        json={"key": "theme", "value": "dark"},
        headers=headers,
    )
    assert put.status_code == 200

    get = client.get("/api/freya/memory", headers=headers)
    assert get.status_code == 200
    assert get.get_json()["data"]["preferences"]["theme"] == "dark"

    bad = client.put(
        "/api/freya/memory/preferences",
        json={"key": "password", "value": "x"},
        headers=headers,
    )
    assert bad.status_code == 400

    search = client.get("/api/freya/memory/search?q=dark", headers=headers)
    assert search.status_code == 200
    assert search.get_json()["data"]["results"]
