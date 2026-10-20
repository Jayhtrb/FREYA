"""
适配器工厂。

未配置 ``DEEPSEEK_API_KEY`` 时返回 mock 适配器（无网络、确定性）。
"""

import os

from ...config import Config
from .base import ModelAdapter
from .deepseek import DeepSeekAdapter
from .mock import MockAdapter


def get_adapter() -> ModelAdapter:
    """按配置选择模型适配器。"""
    if Config.DEEPSEEK_API_KEY:
        return DeepSeekAdapter()
    return MockAdapter()
