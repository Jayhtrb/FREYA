"""
工具分发器：校验参数、调用实现、把异常翻译为工具错误。
"""

from typing import Any, Dict, List, Optional

from .registry import get_registry


class ToolError(Exception):
    """工具执行基类错误。"""


class ToolNotFoundError(ToolError):
    """工具不存在。"""


class ToolArgumentError(ToolError):
    """参数缺失或非法。"""


class ToolExecutionError(ToolError):
    """工具执行失败（包装底层异常，不外泄内部细节）。"""


def list_tools() -> List[Dict[str, Any]]:
    """返回所有已注册工具的公开描述。"""
    return get_registry().public_list()


def dispatch_tool(name: str, args: Dict[str, Any], user_id: Optional[str] = None) -> Dict[str, Any]:
    """执行一个已注册工具并返回其结果字典。"""
    spec = get_registry().get(name)
    if not spec:
        raise ToolNotFoundError(f"unknown tool: {name}")

    args = args or {}
    missing = [key for key in spec.required if not args.get(key)]
    if missing:
        raise ToolArgumentError(f"missing required arguments: {', '.join(missing)}")

    try:
        return spec.handler(args, user_id)
    except ToolError:
        raise
    except Exception as exc:  # noqa: BLE001 —— 工具边界，不向客户端泄漏堆栈
        raise ToolExecutionError(str(exc)) from exc
