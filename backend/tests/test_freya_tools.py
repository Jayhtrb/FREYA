"""
工具注册表与分发器测试（§4）。

只验证注册表/分发器结构（列出、schema、未知工具、参数校验），
不实际执行需要 Zep/LLM 密钥的 MiroFish 引擎。
"""

import pytest

from app.freya.tools.registry import get_registry
from app.freya.tools.dispatcher import (
    list_tools,
    dispatch_tool,
    ToolNotFoundError,
    ToolArgumentError,
)


def test_tools_are_registered():
    tools = list_tools()
    names = {t["name"] for t in tools}
    assert names == {
        "run_simulation",
        "inspect_graph",
        "explain_relationships",
        "interview_agents",
        "generate_report",
        "compare_scenarios",
        "summarize_findings",
    }


def test_tool_specs_have_schema():
    for tool in list_tools():
        assert tool["name"]
        assert tool["description"]
        assert tool["input_schema"]["type"] == "object"


def test_registry_singleton():
    assert get_registry() is get_registry()


def test_dispatch_unknown_tool():
    with pytest.raises(ToolNotFoundError):
        dispatch_tool("does_not_exist", {})


def test_dispatch_requires_arguments():
    # inspect_graph 缺少 graph_id，应抛出参数错误（在触碰引擎之前）。
    with pytest.raises(ToolArgumentError):
        dispatch_tool("inspect_graph", {})
