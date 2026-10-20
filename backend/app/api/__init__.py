"""
API路由模块
"""

from flask import Blueprint

graph_bp = Blueprint('graph', __name__)
simulation_bp = Blueprint('simulation', __name__)
report_bp = Blueprint('report', __name__)

from . import graph  # noqa: E402, F401
from . import simulation  # noqa: E402, F401
from . import report  # noqa: E402, F401

# FREYA 按用户隔离：给引擎蓝图挂载 JWT 门禁 + 所有权校验（§10）。
# 默认 FREYA_REQUIRE_AUTH=false，保持既有引擎测试/前端兼容。
from ..freya.isolation import install_engine_isolation  # noqa: E402

install_engine_isolation(graph_bp, simulation_bp, report_bp)

