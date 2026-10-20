"""
持久化任务状态 + 通知存储（SQLite，跨重启存活，区别于旧的内存 TaskManager）。

任务：job_<hex>；通知：notif_<hex>。均按用户隔离。
"""

import json
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from ..db import execute, query_all, query_one

VALID_STATUSES = {"pending", "processing", "completed", "failed"}


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _new_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:16]}"


class JobStore:
    """任务与通知的持久化存储。"""

    # ---- 任务 ----

    def create_job(self, user_id: str, kind: str, message: str = "") -> str:
        job_id = _new_id("job")
        now = _now()
        execute(
            "INSERT INTO jobs (job_id, user_id, kind, status, progress, message, created_at, updated_at) "
            "VALUES (?, ?, ?, 'pending', 0, ?, ?, ?)",
            (job_id, user_id, kind, message, now, now),
        )
        return job_id

    def update_job(
        self,
        job_id: str,
        status: Optional[str] = None,
        progress: Optional[int] = None,
        message: Optional[str] = None,
        result: Optional[Dict[str, Any]] = None,
        error: Optional[str] = None,
    ) -> None:
        if status is not None and status not in VALID_STATUSES:
            raise ValueError(f"invalid job status: {status}")

        fields = ["updated_at = ?"]
        params: List[Any] = [_now()]
        if status is not None:
            fields.append("status = ?")
            params.append(status)
        if progress is not None:
            fields.append("progress = ?")
            params.append(int(progress))
        if message is not None:
            fields.append("message = ?")
            params.append(message)
        if result is not None:
            fields.append("result = ?")
            params.append(json.dumps(result, ensure_ascii=False))
        if error is not None:
            fields.append("error = ?")
            params.append(error)

        params.append(job_id)
        execute(f"UPDATE jobs SET {', '.join(fields)} WHERE job_id = ?", params)

    def get_job(self, user_id: str, job_id: str) -> Optional[Dict[str, Any]]:
        row = query_one(
            "SELECT * FROM jobs WHERE job_id = ? AND user_id = ?", (job_id, user_id)
        )
        if not row:
            return None
        return self._job_to_dict(row)

    def list_jobs(self, user_id: str, limit: int = 20, offset: int = 0) -> List[Dict[str, Any]]:
        rows = query_all(
            "SELECT * FROM jobs WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (user_id, limit, offset),
        )
        return [self._job_to_dict(r) for r in rows]

    def _job_to_dict(self, row) -> Dict[str, Any]:
        return {
            "job_id": row["job_id"],
            "kind": row["kind"],
            "status": row["status"],
            "progress": row["progress"],
            "message": row["message"],
            "result": json.loads(row["result"]) if row["result"] else None,
            "error": row["error"],
            "created_at": row["created_at"],
            "updated_at": row["updated_at"],
        }

    # ---- 通知 ----

    def create_notification(
        self,
        user_id: str,
        title: str,
        body: str = "",
        kind: str = "info",
    ) -> str:
        notification_id = _new_id("notif")
        execute(
            "INSERT INTO notifications (notification_id, user_id, title, body, kind, read, created_at) "
            "VALUES (?, ?, ?, ?, ?, 0, ?)",
            (notification_id, user_id, title, body, kind, _now()),
        )
        return notification_id

    def list_notifications(
        self, user_id: str, limit: int = 50, offset: int = 0
    ) -> List[Dict[str, Any]]:
        rows = query_all(
            "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (user_id, limit, offset),
        )
        return [self._notification_to_dict(r) for r in rows]

    def mark_read(self, user_id: str, notification_id: str) -> bool:
        row = query_one(
            "SELECT notification_id FROM notifications WHERE notification_id = ? AND user_id = ?",
            (notification_id, user_id),
        )
        if not row:
            return False
        execute(
            "UPDATE notifications SET read = 1 WHERE notification_id = ?",
            (notification_id,),
        )
        return True

    def _notification_to_dict(self, row) -> Dict[str, Any]:
        return {
            "notification_id": row["notification_id"],
            "title": row["title"],
            "body": row["body"],
            "kind": row["kind"],
            "read": bool(row["read"]),
            "created_at": row["created_at"],
        }
