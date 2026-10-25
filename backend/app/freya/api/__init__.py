"""
FREYA API 蓝图注册。

所有 FREYA 端点统一挂载在 ``/api/freya`` 之下（契约 §1）。
"""

from flask import Flask

from ..auth.routes import auth_bp
from .chat import chat_bp
from .tools import tools_bp
from .memory import memory_bp
from .search import search_bp
from .notifications import notifications_bp
from .settings import settings_bp
from .persona import persona_bp
from ..keys.routes import keys_bp
from .tts import tts_bp


def register_freya_blueprints(app: Flask) -> None:
    """把全部 FREYA 蓝图注册到应用。"""
    app.register_blueprint(auth_bp, url_prefix="/api/freya/auth")
    app.register_blueprint(chat_bp, url_prefix="/api/freya")
    app.register_blueprint(tools_bp, url_prefix="/api/freya/tools")
    app.register_blueprint(memory_bp, url_prefix="/api/freya/memory")
    app.register_blueprint(search_bp, url_prefix="/api/freya/search")
    app.register_blueprint(notifications_bp, url_prefix="/api/freya")
    app.register_blueprint(settings_bp, url_prefix="/api/freya/settings")
    app.register_blueprint(persona_bp, url_prefix="/api/freya/persona")
    app.register_blueprint(keys_bp, url_prefix="/api/freya/keys")
    app.register_blueprint(tts_bp, url_prefix="/api/freya/tts")


__all__ = ["register_freya_blueprints"]
