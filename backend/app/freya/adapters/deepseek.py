"""
DeepSeek 适配器（OpenAI 兼容格式）。

配置来源：``DEEPSEEK_API_KEY`` / ``DEEPSEEK_BASE_URL`` / ``DEEPSEEK_MODEL``。
"""

from ...config import Config
from .openai_compat import OpenAICompatAdapter


class DeepSeekAdapter(OpenAICompatAdapter):
    """DeepSeek 推理模型适配器。"""

    def __init__(self, api_key=None, base_url=None, model=None):
        super().__init__(
            api_key=api_key or Config.DEEPSEEK_API_KEY,
            base_url=base_url or Config.DEEPSEEK_BASE_URL,
            model=model or Config.DEEPSEEK_MODEL,
        )
