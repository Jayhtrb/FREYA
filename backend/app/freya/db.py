"""
FREYA SQLite 持久化层。

单一 SQLite 数据库（默认 ``backend/data/freya.sqlite3``）承载：
认证用户、会话、消息、偏好、任务、通知、设置。

使用标准库 ``sqlite3``，无第三方依赖；每次操作打开独立连接，
避免跨线程共享连接的隐患。建表幂等（CREATE TABLE IF NOT EXISTS）。
"""

import os
import sqlite3
import threading
from contextlib import contextmanager
from typing import Any, Iterator, List, Optional, Sequence

from ..config import Config

_SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    user_id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS conversations (
    conversation_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conversations_user
    ON conversations(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS messages (
    message_id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    model TEXT,
    tool_calls TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation
    ON messages(conversation_id, message_id);

CREATE TABLE IF NOT EXISTS preferences (
    user_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    key TEXT NOT NULL,
    value TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (user_id, project_id, key)
);

CREATE INDEX IF NOT EXISTS idx_preferences_user
    ON preferences(user_id, project_id);

CREATE TABLE IF NOT EXISTS jobs (
    job_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    kind TEXT NOT NULL,
    status TEXT NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    message TEXT,
    result TEXT,
    error TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_jobs_user ON jobs(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT,
    kind TEXT NOT NULL DEFAULT 'info',
    read INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notifications_user
    ON notifications(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS settings (
    user_id TEXT PRIMARY KEY,
    personality TEXT,
    model TEXT,
    voice TEXT,
    updated_at TEXT NOT NULL
);
"""

# 允许测试通过环境变量/调用方覆盖数据库路径，而不污染真实数据文件。
_db_path_override: Optional[str] = None
_init_lock = threading.Lock()
_initialized_paths = set()


def set_db_path(path: str) -> None:
    """覆盖数据库路径（主要用于测试隔离）。"""
    global _db_path_override
    _db_path_override = path


def get_db_path() -> str:
    """返回当前生效的 SQLite 数据库路径。"""
    if _db_path_override:
        return _db_path_override
    return getattr(Config, "FREYA_DB_PATH", "freya.sqlite3")


def init_db(path: Optional[str] = None) -> None:
    """幂等创建表结构。"""
    db_path = path or get_db_path()
    parent = os.path.dirname(os.path.abspath(db_path))
    os.makedirs(parent, exist_ok=True)
    with _init_lock:
        if db_path in _initialized_paths:
            return
        conn = sqlite3.connect(db_path)
        try:
            conn.executescript(_SCHEMA)
            conn.commit()
        finally:
            conn.close()
        _initialized_paths.add(db_path)


@contextmanager
def connect() -> Iterator[sqlite3.Connection]:
    """打开一个启用了外键与 Row 工厂的连接，事务结束后提交/回滚。"""
    db_path = get_db_path()
    parent = os.path.dirname(os.path.abspath(db_path))
    os.makedirs(parent, exist_ok=True)
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    try:
        conn.execute("PRAGMA foreign_keys = ON")
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def query_all(sql: str, params: Sequence[Any] = ()) -> List[sqlite3.Row]:
    """执行查询并返回全部行。"""
    init_db()
    with connect() as conn:
        return conn.execute(sql, params).fetchall()


def query_one(sql: str, params: Sequence[Any] = ()) -> Optional[sqlite3.Row]:
    """执行查询并返回单行（无结果时为 None）。"""
    init_db()
    with connect() as conn:
        return conn.execute(sql, params).fetchone()


def execute(sql: str, params: Sequence[Any] = ()) -> int:
    """执行写语句并返回受影响行数（或 lastrowid）。"""
    init_db()
    with connect() as conn:
        cur = conn.execute(sql, params)
        return cur.lastrowid if cur.lastrowid else cur.rowcount
