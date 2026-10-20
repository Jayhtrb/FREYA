"""
Persona 端点（§9）：只读，供客户端展示。
"""

from flask import Blueprint, jsonify

from ..persona.loader import get_persona

persona_bp = Blueprint("freya_persona", __name__)


@persona_bp.route("", methods=["GET"])
def get_public_persona():
    persona = get_persona()
    return jsonify({"success": True, "data": persona.public_view(), "error": None})
