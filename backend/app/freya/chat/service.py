"""
会话服务：按用户持久化对话历史（SQLite），并经由 persona 系统提示词 +
模型适配器生成回复。
"""

import json
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Tuple

from ..db import execute, query_all, query_one
from ..persona.prompt import build_system_prompt
from ..persona.loader import get_persona
from ..adapters.factory import get_adapter


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _new_conversation_id() -> str:
    return f"conv_{uuid.uuid4().hex[:16]}"


def _derive_title(message: str) -> str:
    collapsed = " ".join((message or "").split())
    return collapsed[:60] if collapsed else "New conversation"


class ConversationService:
    """按用户隔离的会话持久化与回复生成。"""

    def create_conversation(self, user_id: str, title: Optional[str] = None) -> str:
        conversation_id = _new_conversation_id()
        now = _now()
        execute(
            "INSERT INTO conversations (conversation_id, user_id, title, created_at, updated_at) "
            "VALUES (?, ?, ?, ?, ?)",
            (conversation_id, user_id, title or "", now, now),
        )
        return conversation_id

    def get_conversation(self, user_id: str, conversation_id: str) -> Optional[Dict[str, Any]]:
        row = query_one(
            "SELECT conversation_id, user_id, title, created_at, updated_at "
            "FROM conversations WHERE conversation_id = ? AND user_id = ?",
            (conversation_id, user_id),
        )
        if not row:
            return None
        return self._conversation_to_dict(row, user_id)

    def list_conversations(self, user_id: str, limit: int = 20, offset: int = 0) -> List[Dict[str, Any]]:
        rows = query_all(
            "SELECT conversation_id, user_id, title, created_at, updated_at "
            "FROM conversations WHERE user_id = ? ORDER BY updated_at DESC LIMIT ? OFFSET ?",
            (user_id, limit, offset),
        )
        return [self._conversation_to_dict(r, user_id) for r in rows]

    def delete_conversation(self, user_id: str, conversation_id: str) -> bool:
        row = query_one(
            "SELECT conversation_id FROM conversations WHERE conversation_id = ? AND user_id = ?",
            (conversation_id, user_id),
        )
        if not row:
            return False
        execute("DELETE FROM messages WHERE conversation_id = ?", (conversation_id,))
        execute("DELETE FROM conversations WHERE conversation_id = ?", (conversation_id,))
        return True

    def add_message(
        self,
        conversation_id: str,
        role: str,
        content: str,
        model: Optional[str] = None,
        tool_calls: Optional[List[Dict[str, Any]]] = None,
    ) -> None:
        execute(
            "INSERT INTO messages (conversation_id, role, content, model, tool_calls, created_at) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            (
                conversation_id,
                role,
                content,
                model,
                json.dumps(tool_calls) if tool_calls else None,
                _now(),
            ),
        )

    def get_messages(self, conversation_id: str) -> List[Dict[str, Any]]:
        rows = query_all(
            "SELECT role, content, model, tool_calls, created_at FROM messages "
            "WHERE conversation_id = ? ORDER BY message_id ASC",
            (conversation_id,),
        )
        messages = []
        for r in rows:
            tool_calls = json.loads(r["tool_calls"]) if r["tool_calls"] else []
            messages.append(
                {
                    "role": r["role"],
                    "content": r["content"],
                    "model": r["model"],
                    "tool_calls": tool_calls,
                    "created_at": r["created_at"],
                }
            )
        return messages

    def _conversation_to_dict(self, row, user_id: str) -> Dict[str, Any]:
        return {
            "conversation_id": row["conversation_id"],
            "title": row["title"] or "",
            "created_at": row["created_at"],
            "updated_at": row["updated_at"],
        }

    # ---- 回复生成 ----

    def _build_messages(
        self,
        user_id: str,
        conversation_id: Optional[str],
        message: str,
    ) -> Tuple[str, List[Dict[str, str]], bool]:
        """返回 (effective_conversation_id, messages, is_new)。"""
        persona = get_persona()
        system = [{"role": "system", "content": build_system_prompt(persona)}]

        if conversation_id:
            conv = self.get_conversation(user_id, conversation_id)
            if not conv:
                # 会话不存在或不属于该用户：按不存在处理（调用方决定是否 404）。
                raise ConversationNotFoundError(conversation_id)
            history = [
                {"role": m["role"], "content": m["content"]}
                for m in self.get_messages(conversation_id)
            ]
            is_new = False
        else:
            conversation_id = self.create_conversation(user_id)
            history = []
            is_new = True

        messages = system + history + [{"role": "user", "content": message}]

        # 首个用户消息作为会话标题。
        if is_new or not self._has_title(conversation_id):
            execute(
                "UPDATE conversations SET title = ?, updated_at = ? WHERE conversation_id = ?",
                (_derive_title(message), _now(), conversation_id),
            )

        # 持久化用户消息。
        self.add_message(conversation_id, "user", message)

        return conversation_id, messages, is_new

    def _has_title(self, conversation_id: str) -> bool:
        row = query_one(
            "SELECT title FROM conversations WHERE conversation_id = ?", (conversation_id,)
        )
        return bool(row and row["title"])

    def _touch(self, conversation_id: str) -> None:
        execute(
            "UPDATE conversations SET updated_at = ? WHERE conversation_id = ?",
            (_now(), conversation_id),
        )

    def generate_reply(
        self,
        user_id: str,
        message: str,
        conversation_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """生成一次完整回复并持久化。"""
        adapter = get_adapter()
        conversation_id, messages, _ = self._build_messages(user_id, conversation_id, message)

        response = adapter.chat(messages)
        self.add_message(
            conversation_id, "assistant", response.text, model=response.model,
            tool_calls=response.tool_calls,
        )
        self._touch(conversation_id)

        return {
            "conversation_id": conversation_id,
            "reply": response.text,
            "model": response.model,
            "finished": True,
            "tool_calls": response.tool_calls,
        }

    def prepare_stream(
        self,
        user_id: str,
        message: str,
        conversation_id: Optional[str] = None,
    ) -> Tuple[str, List[Dict[str, str]]]:
        """持久化用户消息并返回 (conversation_id, messages) 供流式适配器使用。"""
        return self._build_messages(user_id, conversation_id, message)[:2]

    def finalize_stream(
        self,
        conversation_id: str,
        reply_text: str,
        model: str,
        tool_calls: Optional[List[Dict[str, Any]]] = None,
    ) -> None:
        """流式结束后持久化助手回复。"""
        self.add_message(conversation_id, "assistant", reply_text, model=model, tool_calls=tool_calls)
        self._touch(conversation_id)


class ConversationNotFoundError(Exception):
    """会话不存在或不属于当前用户。"""
