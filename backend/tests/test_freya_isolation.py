"""
按用户隔离测试（§10）—— 存储层与所有权解析（不加载 MiroFish 引擎）。

覆盖：Project 模型 user_id 持久化与 scoping、所有权解析函数。
引擎 HTTP 门禁（401/404）见 test_freya_engine_isolation.py（需引擎依赖，CI 运行）。
"""

import pytest

from app.freya import db
from app.freya.isolation import _resolve_owner
from app.models.project import ProjectManager


@pytest.fixture()
def projects_dir(tmp_path, monkeypatch):
    monkeypatch.setattr(ProjectManager, "PROJECTS_DIR", str(tmp_path / "projects"))
    return str(tmp_path / "projects")


def test_project_manager_scopes_by_user(projects_dir):
    project_a = ProjectManager.create_project(name="A", user_id="user_a")
    project_b = ProjectManager.create_project(name="B", user_id="user_b")

    assert project_a.user_id == "user_a"

    # 拥有者过滤
    assert ProjectManager.get_project(project_a.project_id, user_id="user_a") is not None
    assert ProjectManager.get_project(project_a.project_id, user_id="user_b") is None

    listed_a = [p.project_id for p in ProjectManager.list_projects(user_id="user_a")]
    assert project_a.project_id in listed_a
    assert project_b.project_id not in listed_a


def test_project_user_id_roundtrip_persisted(projects_dir):
    project = ProjectManager.create_project(name="Persisted", user_id="user_zz")
    loaded = ProjectManager.get_project(project.project_id)
    assert loaded.user_id == "user_zz"
    assert loaded.to_dict()["user_id"] == "user_zz"


def test_resolve_owner_for_project(projects_dir):
    project = ProjectManager.create_project(name="Owned", user_id="user_a")
    assert _resolve_owner("graph", {"project_id": project.project_id}) == "user_a"


def test_resolve_owner_unknown_project_returns_none(projects_dir):
    assert _resolve_owner("graph", {"project_id": "proj_nope"}) is None
