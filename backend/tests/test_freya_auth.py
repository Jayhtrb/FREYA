"""
认证端点测试（§2）。

覆盖：注册、登录、me、logout、JWT 签发/校验、密码哈希、require_auth 保护。
使用最小 FREYA 应用（不加载 MiroFish 引擎），测试用临时 SQLite，无网络、无密钥。
"""

import pytest
from flask import Flask

from app.config import Config
from app.freya import db
from app.freya.api import register_freya_blueprints
from app.freya.auth.security import hash_password, verify_password, issue_token, verify_token


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


def _register(client, email="a@example.com", password="secret123", display_name="Ada"):
    resp = client.post(
        "/api/freya/auth/register",
        json={"email": email, "password": password, "display_name": display_name},
    )
    return resp


def test_password_hashing_roundtrip():
    stored = hash_password("s3cret!")
    assert stored.startswith("pbkdf2_sha256$")
    assert verify_password("s3cret!", stored)
    assert not verify_password("wrong", stored)
    assert not verify_password("s3cret!", "malformed$hash")


def test_jwt_issue_and_verify():
    token = issue_token("user_abc123", ttl_seconds=3600)
    assert verify_token(token) == "user_abc123"
    assert verify_token("not-a-token") is None


def test_jwt_secret_is_not_weak_default():
    # 未显式配置时，密钥必须是随机值而非可预测的硬编码默认值。
    from app.config import Config

    assert Config.FREYA_JWT_SECRET != "mirofish-secret-key"
    assert len(Config.FREYA_JWT_SECRET) >= 32


def test_register_and_login(client):
    reg = _register(client)
    assert reg.status_code == 201
    body = reg.get_json()
    assert body["success"] is True
    assert body["data"]["token"]
    assert body["data"]["user"]["user_id"].startswith("user_")
    assert body["data"]["user"]["email"] == "a@example.com"

    # 重复注册
    dup = _register(client)
    assert dup.status_code == 409

    # 登录
    login = client.post(
        "/api/freya/auth/login",
        json={"email": "a@example.com", "password": "secret123"},
    )
    assert login.status_code == 200
    assert login.get_json()["data"]["token"]


def test_login_rejects_bad_credentials(client):
    _register(client)
    resp = client.post(
        "/api/freya/auth/login",
        json={"email": "a@example.com", "password": "wrong"},
    )
    assert resp.status_code == 401
    assert resp.get_json()["success"] is False


def test_me_requires_auth_and_returns_user(client):
    reg = _register(client).get_json()
    token = reg["data"]["token"]

    anon = client.get("/api/freya/auth/me")
    assert anon.status_code == 401

    me = client.get(
        "/api/freya/auth/me", headers={"Authorization": f"Bearer {token}"}
    )
    assert me.status_code == 200
    assert me.get_json()["data"]["user"]["user_id"] == reg["data"]["user"]["user_id"]


def test_logout(client):
    token = _register(client).get_json()["data"]["token"]
    resp = client.post(
        "/api/freya/auth/logout", headers={"Authorization": f"Bearer {token}"}
    )
    assert resp.status_code == 200
    assert resp.get_json()["data"]["ok"] is True


def test_invalid_token_rejected(client):
    resp = client.get(
        "/api/freya/auth/me", headers={"Authorization": "Bearer bogus.token.value"}
    )
    assert resp.status_code == 401
