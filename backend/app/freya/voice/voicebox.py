"""
Minimal client for a self-hosted Voicebox server (OpenAI-compatible speech API).

Only the server operator configures the URL (``VOICEBOX_URL``); clients can never pass a
URL, so this endpoint can't be turned into a request forwarder (no SSRF surface).
"""

from typing import Any, Dict, Optional

import httpx

from ...config import Config

MAX_INPUT_CHARS = 1200


class VoiceboxError(Exception):
    """Voicebox is not configured, unreachable, or rejected the request."""

    def __init__(self, message: str, status: int = 502):
        super().__init__(message)
        self.status = status


def is_configured() -> bool:
    return bool(Config.VOICEBOX_URL)


def _base() -> str:
    return Config.VOICEBOX_URL.rstrip("/")


def status() -> Dict[str, Any]:
    """Reachability and configuration summary (never exposes anything secret)."""
    if not is_configured():
        return {"configured": False, "reachable": False, "voice": None, "model": None}
    try:
        with httpx.Client(timeout=4.0) as client:
            resp = client.get(f"{_base()}/v1/models")
        reachable = resp.status_code < 500
        models = []
        if resp.status_code == 200:
            models = [m.get("id") for m in (resp.json().get("data") or []) if isinstance(m, dict)]
        return {
            "configured": True,
            "reachable": reachable,
            "voice": Config.VOICEBOX_VOICE or None,
            "model": Config.VOICEBOX_MODEL,
            "models": models[:20],
        }
    except httpx.HTTPError as exc:
        return {
            "configured": True,
            "reachable": False,
            "voice": Config.VOICEBOX_VOICE or None,
            "model": Config.VOICEBOX_MODEL,
            "error": type(exc).__name__,
        }


def synthesize(text: str, speed: float = 1.0, voice: Optional[str] = None) -> bytes:
    """Returns 16-bit PCM WAV bytes for ``text``."""
    if not is_configured():
        raise VoiceboxError("Voicebox is not configured on the server (VOICEBOX_URL).", 503)
    text = (text or "").strip()
    if not text:
        raise VoiceboxError("text is required", 400)
    if len(text) > MAX_INPUT_CHARS:
        raise VoiceboxError(f"text is longer than {MAX_INPUT_CHARS} characters; send sentences", 413)
    payload: Dict[str, Any] = {
        "model": Config.VOICEBOX_MODEL,
        "input": text,
        "response_format": "wav",
        "speed": max(0.25, min(4.0, float(speed or 1.0))),
    }
    chosen = (voice or Config.VOICEBOX_VOICE or "").strip()
    if chosen:
        payload["voice"] = chosen
    try:
        with httpx.Client(timeout=Config.VOICEBOX_TIMEOUT_SECONDS) as client:
            resp = client.post(f"{_base()}/v1/audio/speech", json=payload)
    except httpx.TimeoutException as exc:
        raise VoiceboxError("Voicebox took too long to respond.", 504) from exc
    except httpx.HTTPError as exc:
        raise VoiceboxError("Can't reach Voicebox at the configured address.", 502) from exc
    if resp.status_code != 200:
        message = "Voicebox rejected the request"
        try:
            message = resp.json().get("error", {}).get("message") or message
        except ValueError:
            pass
        raise VoiceboxError(message, 502)
    body = resp.content
    if len(body) < 44 or body[:4] != b"RIFF":
        raise VoiceboxError("Voicebox returned audio that isn't WAV.", 502)
    return body
