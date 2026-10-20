"""
会话与流式端点（§3）。

- POST /api/freya/chat           非流式
- POST /api/freya/chat/stream   text/event-stream
- GET/DELETE /api/freya/conversations[/<id>]
"""

import json
from typing import Any, Dict

from flask import Blueprint, Response, jsonify, request, stream_with_context

from ..auth.decorators import require_auth, current_user_id
from ..chat.service import ConversationService, ConversationNotFoundError
from ..adapters.factory import get_adapter
from ..observability.rate_limit import get_rate_limiter

chat_bp = Blueprint("freya_chat", __name__)

_service = ConversationService()


def _ok(data: Dict[str, Any], status: int = 200):
    return jsonify({"success": True, "data": data, "error": None}), status


def _err(message: str, status: int = 400):
    return jsonify({"success": False, "data": None, "error": message}), status


def _sse(event: str, data: Dict[str, Any]) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@chat_bp.route("/chat", methods=["POST"])
@require_auth
def chat():
    data = request.get_json(silent=True) or {}
    message = (data.get("message") or "").strip()
    conversation_id = data.get("conversation_id")

    if not message:
        return _err("message is required", 400)

    user_id = current_user_id()
    if not get_rate_limiter().allow(user_id):
        return _err("rate limit exceeded", 429)

    try:
        result = _service.generate_reply(user_id, message, conversation_id)
    except ConversationNotFoundError:
        return _err("conversation not found", 404)

    return _ok(result)


@chat_bp.route("/chat/stream", methods=["POST"])
@require_auth
def chat_stream():
    data = request.get_json(silent=True) or {}
    message = (data.get("message") or "").strip()
    conversation_id = data.get("conversation_id")

    if not message:
        return _err("message is required", 400)

    user_id = current_user_id()
    if not get_rate_limiter().allow(user_id):
        return _err("rate limit exceeded", 429)

    adapter = get_adapter()

    @stream_with_context
    def generate():
        try:
            conversation_id_eff, messages = _service.prepare_stream(
                user_id, message, conversation_id
            )
        except ConversationNotFoundError:
            yield _sse("error", {"error": "conversation not found"})
            return
        except Exception as exc:  # noqa: BLE001
            yield _sse("error", {"error": str(exc)})
            return

        chunks: list = []
        try:
            for delta in adapter.stream(messages):
                chunks.append(delta)
                yield _sse("delta", {"delta": delta})
            full = "".join(chunks)
            _service.finalize_stream(conversation_id_eff, full, adapter.model_name, [])
            yield _sse(
                "done",
                {
                    "conversation_id": conversation_id_eff,
                    "reply": full,
                    "model": adapter.model_name,
                    "finished": True,
                },
            )
        except Exception as exc:  # noqa: BLE001
            yield _sse("error", {"error": str(exc)})

    return Response(generate(), mimetype="text/event-stream")


@chat_bp.route("/conversations", methods=["GET"])
@require_auth
def list_conversations():
    limit = request.args.get("limit", 20, type=int)
    offset = request.args.get("offset", 0, type=int)
    conversations = _service.list_conversations(current_user_id(), limit=limit, offset=offset)
    return _ok({"conversations": conversations})


@chat_bp.route("/conversations/<conversation_id>", methods=["GET"])
@require_auth
def get_conversation(conversation_id: str):
    conv = _service.get_conversation(current_user_id(), conversation_id)
    if not conv:
        return _err("conversation not found", 404)
    conv["messages"] = _service.get_messages(conversation_id)
    return _ok({"conversation": conv})


@chat_bp.route("/conversations/<conversation_id>", methods=["DELETE"])
@require_auth
def delete_conversation(conversation_id: str):
    deleted = _service.delete_conversation(current_user_id(), conversation_id)
    if not deleted:
        return _err("conversation not found", 404)
    return _ok({"ok": True})
