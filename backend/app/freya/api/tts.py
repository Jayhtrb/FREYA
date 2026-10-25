"""
FREYA voice output via a self-hosted Voicebox (§ voice).

- GET  /api/freya/tts/status   Voicebox configuration + reachability
- POST /api/freya/tts          {text, speed?} -> audio/wav (one sentence-sized chunk)

The Android app synthesizes reply sentences through this endpoint, plays the WAV and drives
the FREYA Core from the measured loudness of that audio. If this fails, the app falls back
to the phone's own TTS.
"""

from typing import Any, Dict

from flask import Blueprint, Response, jsonify, request

from ..auth.decorators import require_auth
from ..voice import voicebox

tts_bp = Blueprint("freya_tts", __name__)


def _ok(data: Dict[str, Any]):
    return jsonify({"success": True, "data": data, "error": None})


def _err(message: str, status: int = 400):
    return jsonify({"success": False, "data": None, "error": message}), status


@tts_bp.route("/status", methods=["GET"])
@require_auth
def tts_status():
    return _ok(voicebox.status())


@tts_bp.route("", methods=["POST"])
@require_auth
def tts_speak():
    data = request.get_json(silent=True) or {}
    try:
        wav = voicebox.synthesize(data.get("text", ""), speed=data.get("speed", 1.0))
    except voicebox.VoiceboxError as exc:
        return _err(str(exc), exc.status)
    return Response(wav, mimetype="audio/wav", headers={"Cache-Control": "no-store"})
