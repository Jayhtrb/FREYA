"""FREYA persona 包：加载、提示词构建、时间自适应问候。"""

from .loader import Persona, load_persona, get_persona
from .prompt import build_system_prompt
from .greeting import time_greeting, build_greeting

__all__ = [
    "Persona",
    "load_persona",
    "get_persona",
    "build_system_prompt",
    "time_greeting",
    "build_greeting",
]
