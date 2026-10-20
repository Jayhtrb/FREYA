"""
契约形状回归测试（§7/§8/§9）。

锁定 FREYA 端点返回的字段名，防止 Android 客户端与后端契约漂移：
- persona: 平铺字段（name/acronym/tagline/role/voice_presentation/greeting/voice.states）
- settings: personality/model/voice 均为字符串
- notifications: {id, title, body, type, read, created_at}
- jobs: {id, job_id, type, status, progress, message, result, error, created_at, updated_at}

无网络、无密钥；使用临时 SQLite。
"""

import pytest
from flask import Flask

from app.config import Config
from app.freya import db
from app.freya.api import register_freya_blueprints
from app.freya.jobs.store import JobStore


def _make_app(tmp_path) -> Flask:
    db.set_db_path(str(tmp_path / "freya.sqlite3"))
    db.init_db()
    app = Flask("freya-contract-test")
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
        json={"email": "c@example.com", "password": "secret123", "display_name": "C"},
    ).get_json()["data"]
    return {"Authorization": f"Bearer {body['token']}"}, body["user"]["user_id"]


def test_persona_shape(client):
    data = client.get("/api/freya/persona").get_json()["data"]
    assert data["name"] == "FREYA"
    assert data["acronym"]
    assert data["tagline"]
    assert data["role"]
    assert data["voice_presentation"]
    assert data["greeting"]["title"] == "FREYA ONLINE"
    assert data["greeting"]["body_template"]
    assert "time_greetings" in data["greeting"]
    assert "time_boundaries_hour" in data["greeting"]
    assert isinstance(data["voice"]["states"], list)
    assert "listening" in data["voice"]["states"]


def test_settings_string_shape(client):
    headers, _ = _register_and_auth(client)

    data = client.get("/api/freya/settings", headers=headers).get_json()["data"]
    assert isinstance(data["personality"], str)
    assert isinstance(data["model"], str)
    assert isinstance(data["voice"], str)

    upd = client.put(
        "/api/freya/settings",
        headers=headers,
        json={"personality": "witty", "model": "deepseek-reasoner", "voice": "system_tts"},
    )
    assert upd.status_code == 200
    upd_data = upd.get_json()["data"]
    assert upd_data["personality"] == "witty"
    assert upd_data["model"] == "deepseek-reasoner"
    assert upd_data["voice"] == "system_tts"


def test_notification_and_job_shapes(client):
    headers, user_id = _register_and_auth(client)
    store = JobStore()

    job_id = store.create_job(user_id, "simulation", "running")
    store.update_job(job_id, status="processing", progress=42, message="in progress")
    notification_id = store.create_notification(user_id, "Done", "finished", kind="success")

    notifs = client.get("/api/freya/notifications", headers=headers).get_json()["data"]["notifications"]
    assert notifs
    n = notifs[0]
    assert n["id"] == notification_id
    assert n["title"] == "Done"
    assert n["type"] == "success"
    assert n["read"] is False

    jobs = client.get("/api/freya/jobs", headers=headers).get_json()["data"]["jobs"]
    assert jobs
    j = jobs[0]
    assert j["id"] == job_id
    assert j["job_id"] == job_id
    assert j["type"] == "simulation"
    assert j["status"] == "processing"
    assert j["progress"] == 42

    assert client.post(f"/api/freya/notifications/{notification_id}/read", headers=headers).status_code == 200
