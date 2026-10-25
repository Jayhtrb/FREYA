"""
Voicebox TTS proxy tests: no network — httpx is patched with a fake transport.
"""

import struct

import httpx
import pytest
from flask import Flask

from app.config import Config
from app.freya import db
from app.freya.api import register_freya_blueprints
from app.freya.observability.rate_limit import get_rate_limiter


def _wav(n_samples: int = 2400) -> bytes:
    data = b"\x00\x10" * n_samples
    header = b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVE"
    fmt = b"fmt " + struct.pack("<IHHIIHH", 16, 1, 1, 24000, 48000, 2, 16)
    return header + fmt + b"data" + struct.pack("<I", len(data)) + data


@pytest.fixture()
def client(tmp_path, monkeypatch):
    db.set_db_path(str(tmp_path / "freya.sqlite3"))
    db.init_db()
    get_rate_limiter().limit = 1000
    app = Flask("freya-test")
    app.config.from_object(Config)
    app.config.update(TESTING=True)
    register_freya_blueprints(app)
    return app.test_client()


_tokens = {}


def _auth(client):
    if id(client) not in _tokens:
        resp = client.post("/api/freya/auth/register", json={"email": f"v{len(_tokens)}@example.com", "password": "pw123456"})
        _tokens[id(client)] = resp.get_json()["data"]["token"]
    return {"Authorization": f"Bearer {_tokens[id(client)]}"}


_REAL_CLIENT = httpx.Client


def _patch_httpx(monkeypatch, handler):
    real = _REAL_CLIENT

    def fake_client(*args, **kwargs):
        kwargs["transport"] = httpx.MockTransport(handler)
        return real(*args, **kwargs)

    monkeypatch.setattr(httpx, "Client", fake_client)


def test_requires_auth(client):
    assert client.post("/api/freya/tts", json={"text": "hi"}).status_code == 401


def test_not_configured_returns_503(client, monkeypatch):
    monkeypatch.setattr(Config, "VOICEBOX_URL", "")
    resp = client.post("/api/freya/tts", json={"text": "Hello"}, headers=_auth(client))
    assert resp.status_code == 503
    status = client.get("/api/freya/tts/status", headers=_auth(client)).get_json()["data"]
    assert status["configured"] is False


def test_proxies_wav_with_configured_voice(client, monkeypatch):
    monkeypatch.setattr(Config, "VOICEBOX_URL", "http://voicebox.local:17493/")
    monkeypatch.setattr(Config, "VOICEBOX_VOICE", "FREYA")
    seen = {}

    def handler(request: httpx.Request):
        seen["url"] = str(request.url)
        seen["json"] = request.read().decode()
        return httpx.Response(200, content=_wav(), headers={"content-type": "audio/wav"})

    _patch_httpx(monkeypatch, handler)
    resp = client.post("/api/freya/tts", json={"text": "Hello there.", "speed": 1.2}, headers=_auth(client))
    assert resp.status_code == 200
    assert resp.mimetype == "audio/wav"
    assert resp.data[:4] == b"RIFF"
    assert seen["url"] == "http://voicebox.local:17493/v1/audio/speech"
    assert '"voice": "FREYA"' in seen["json"] or '"voice":"FREYA"' in seen["json"]
    assert '"response_format": "wav"' in seen["json"] or '"response_format":"wav"' in seen["json"]


def test_rejects_non_wav_and_upstream_errors(client, monkeypatch):
    monkeypatch.setattr(Config, "VOICEBOX_URL", "http://voicebox.local:17493")
    _patch_httpx(monkeypatch, lambda r: httpx.Response(200, content=b"not audio"))
    assert client.post("/api/freya/tts", json={"text": "x"}, headers=_auth(client)).status_code == 502
    _patch_httpx(monkeypatch, lambda r: httpx.Response(404, json={"error": {"message": "Voice profile 'FREYA' not found"}}))
    resp = client.post("/api/freya/tts", json={"text": "x"}, headers=_auth(client))
    assert resp.status_code == 502
    assert "not found" in resp.get_json()["error"]


def test_rejects_long_text(client, monkeypatch):
    monkeypatch.setattr(Config, "VOICEBOX_URL", "http://voicebox.local:17493")
    resp = client.post("/api/freya/tts", json={"text": "a" * 5000}, headers=_auth(client))
    assert resp.status_code == 413


def test_unreachable_reports_cleanly(client, monkeypatch):
    monkeypatch.setattr(Config, "VOICEBOX_URL", "http://voicebox.local:17493")

    def boom(request):
        raise httpx.ConnectError("refused")

    _patch_httpx(monkeypatch, boom)
    resp = client.post("/api/freya/tts", json={"text": "hi"}, headers=_auth(client))
    assert resp.status_code == 502
    status = client.get("/api/freya/tts/status", headers=_auth(client)).get_json()["data"]
    assert status == {**status, "configured": True, "reachable": False}
