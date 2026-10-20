"""
FREYA-only 运行入口（轻量模式，无需 MiroFish 引擎依赖）。

启动仅包含 FREYA 编排层的 Flask 应用：认证、会话、模型适配器（DeepSeek/mock）、
按用户密钥（BYOK）、记忆、设置、通知、任务、搜索、工具注册。

模拟引擎端点（/api/graph、/api/simulation、/api/report）不在此模式提供；
运行完整引擎请使用 ``docker compose up --build``（见 run.py 与 docs/deployment.md）。
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from flask import Flask, jsonify

from app.config import Config
from app.freya import db as freya_db
from app.freya.api import register_freya_blueprints


def create_freya_app() -> Flask:
    """创建仅含 FREYA 编排层的应用（不加载 MiroFish 引擎）。"""
    app = Flask("freya")
    app.config.from_object(Config)
    if hasattr(app, "json") and hasattr(app.json, "ensure_ascii"):
        app.json.ensure_ascii = False

    freya_db.init_db()
    register_freya_blueprints(app)

    @app.route("/health")
    def health():
        return jsonify(
            {"status": "ok", "service": "FREYA Backend", "mode": "assistant-only"}
        )

    return app


def main() -> None:
    host = os.environ.get("FLASK_HOST", "0.0.0.0")
    port = int(os.environ.get("FLASK_PORT", 5001))
    create_freya_app().run(host=host, port=port, debug=Config.DEBUG, threaded=True)


if __name__ == "__main__":
    main()
