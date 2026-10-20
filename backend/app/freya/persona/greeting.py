"""
时间自适应问候助手。

依据 persona JSON 中 identity.greeting 块的 time_boundaries_hour 与
time_greetings 生成与当前时刻匹配的问候语。
"""

from datetime import datetime, time
from typing import Optional

from .loader import Persona, get_persona

_DEFAULT_BOUNDARIES = {"morning": 5, "afternoon": 12, "evening": 17, "night": 21}


def _period_for_hour(hour: int, boundaries: dict) -> str:
    """根据小时与 JSON 声明的边界返回时间段名（morning/afternoon/evening/night）。"""
    night = int(boundaries.get("night", 21))
    morning = int(boundaries.get("morning", 5))
    afternoon = int(boundaries.get("afternoon", 12))
    evening = int(boundaries.get("evening", 17))
    # night 覆盖 night:00 至次日 morning:00（含凌晨）。
    if hour >= night or hour < morning:
        return "night"
    if hour >= evening:
        return "evening"
    if hour >= afternoon:
        return "afternoon"
    return "morning"


def time_greeting(hour: Optional[int] = None, persona: Optional[Persona] = None) -> str:
    """返回当前小时对应的问候短语（如 "Good morning"）。"""
    persona = persona or get_persona()
    hour = hour if hour is not None else datetime.now().hour
    greeting_block = persona.greeting or {}

    # 优先使用 JSON 声明的边界；缺省时用硬编码默认值兜底。
    boundaries = greeting_block.get("time_boundaries_hour") or _DEFAULT_BOUNDARIES
    period = _period_for_hour(hour, boundaries)
    greetings = greeting_block.get("time_greetings", {})
    return greetings.get(period) or greetings.get("morning") or "Good morning"


def build_greeting(now: Optional[datetime] = None, persona: Optional[Persona] = None) -> dict:
    """
    返回完整问候对象：

    ``{"title", "text"}`` —— ``text`` 为填充了时间问候语后的 body_template。
    """
    persona = persona or get_persona()
    now = now or datetime.now()
    greeting_block = persona.greeting or {}

    title = greeting_block.get("title", "FREYA ONLINE")
    template = greeting_block.get("body_template", "")
    text = template.format(time_greeting=time_greeting(now.hour, persona))
    return {"title": title, "text": text}
