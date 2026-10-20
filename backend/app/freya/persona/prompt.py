"""
系统提示词构建器。

将 persona JSON 中的身份、人格、系统指令、行为边界、工具选择原则、
记忆规则、确认要求与真实性规则组合成一条稳定的系统提示词。
"""

from typing import Optional

from .loader import Persona, get_persona


def build_system_prompt(persona: Optional[Persona] = None) -> str:
    """由人格定义生成系统提示词。"""
    persona = persona or get_persona()

    sections: list[str] = []
    identity = persona.identity
    sections.append(
        f"You are {identity.get('name', 'FREYA')} — {identity.get('role', 'a personal AI assistant')}."
    )
    tagline = identity.get("tagline")
    if tagline:
        sections.append(f"Tagline: {tagline}")

    tone = persona.personality.get("tone", {})
    if tone.get("description"):
        sections.append(f"Tone: {tone['description']}")
    traits = persona.personality.get("traits", [])
    if traits:
        sections.append("Core traits: " + ", ".join(traits))

    instructions = persona.system_instructions
    if instructions:
        sections.append("Instructions:\n- " + "\n- ".join(instructions))

    boundaries = persona.behavioral_boundaries
    if boundaries:
        sections.append("Boundaries:\n- " + "\n- ".join(boundaries))

    tool_principles = persona.raw.get("tool_selection", {}).get("principles", [])
    if tool_principles:
        sections.append("Tool selection:\n- " + "\n- ".join(tool_principles))

    memory_rules = persona.raw.get("memory_and_personalization", {}).get("rules", [])
    if memory_rules:
        sections.append("Memory & personalization:\n- " + "\n- ".join(memory_rules))

    confirmation = persona.raw.get("confirmation_requirements", {})
    if confirmation.get("rule"):
        sections.append(f"Confirmation: {confirmation['rule']}")

    truth_rules = persona.raw.get("truthfulness_and_error_handling", {}).get("rules", [])
    if truth_rules:
        sections.append("Truthfulness & errors:\n- " + "\n- ".join(truth_rules))

    return "\n\n".join(sections)
