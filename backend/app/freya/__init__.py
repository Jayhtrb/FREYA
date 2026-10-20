"""
FREYA 编排层（voice-enabled personal AI assistant on MiroFish）。

本包实现 /api/freya/* 契约（见 docs/api-contract.md），包含：
- persona 加载 / 系统提示词 / 时间自适应问候
- SQLite 持久化（认证、会话、记忆、任务、通知）
- JWT 认证与密码哈希
- 模型适配器（DeepSeek / mock）
- 会话服务与流式接口
- MiroFish 工具编排
- 记忆与偏好
- 持久化任务与通知
- 可观测性（脱敏日志 + 速率限制）
- 现有引擎接口的按用户隔离
"""

from . import db  # noqa: F401
