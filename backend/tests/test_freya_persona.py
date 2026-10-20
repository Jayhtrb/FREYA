"""
Persona 加载器、系统提示词与时间自适应问候测试。
"""

from datetime import datetime

from app.freya.persona import (
    Persona,
    load_persona,
    build_system_prompt,
    time_greeting,
    build_greeting,
)


def test_load_persona():
    persona = load_persona()
    assert isinstance(persona, Persona)
    assert persona.name == "FREYA"
    assert persona.tagline == "One intelligence. Every interface. Your world."
    assert persona.system_instructions
    assert persona.behavioral_boundaries


def test_system_prompt_contains_identity_and_instructions():
    prompt = build_system_prompt()
    assert "FREYA" in prompt
    assert "not a generic chatbot" in prompt
    assert "proactive" in prompt.lower()


def test_time_greeting_boundaries():
    # night: 0-4 和 21-23
    assert time_greeting(2) == "Good evening"
    assert time_greeting(23) == "Good evening"
    assert time_greeting(5) == "Good morning"
    assert time_greeting(11) == "Good morning"
    assert time_greeting(12) == "Good afternoon"
    assert time_greeting(16) == "Good afternoon"
    assert time_greeting(17) == "Good evening"
    assert time_greeting(20) == "Good evening"


def test_build_greeting_fills_template():
    greeting = build_greeting(now=datetime(2026, 10, 20, 9, 0, 0))
    assert greeting["title"] == "FREYA ONLINE"
    assert greeting["text"].startswith("Good morning.")
    assert "FREYA is online and ready" in greeting["text"]


def test_persona_public_view():
    view = load_persona().public_view()
    assert view["name"] == "FREYA"
    assert "states" in view["voice"]
    assert "greeting" in view
