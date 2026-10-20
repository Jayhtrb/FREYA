"""
引擎蓝图 JWT 门禁与跨用户 404 测试（§10）—— 需要 MiroFish 引擎依赖。

在 CI（Linux，``uv sync`` 成功）下运行；本机若缺少引擎依赖（zep-cloud 等）则整模块跳过。
"""

import pytest

pytest.importorskip("zep_cloud", reason="MiroFish 引擎依赖未安装，跳过引擎门禁测试")

from app import create_app  # noqa: E402
from app.freya import db  # noqa: E402
from app.models.project import ProjectManager  # noqa: E402


@pytest.fixture()
def projects_dir(tmp_path, monkeypatch):
    monkeypatch.setattr(ProjectManager, "PROJECTS_DIR", str(tmp_path / "projects"))
    return str(tmp_path / "projects")


@pytest.fixture()
def client(tmp_path, projects_dir):
    db.set_db_path(str(tmp_path / "freya.sqlite3"))
    db.init_db()
    app = create_app()
    app.config.update(TESTING=True)
    return app.test_client()


def _register(client, email):
    return client.post(
        "/api/freya/auth/register",
        json={"email": email, "password": "pw123456", "display_name": email},
    ).get_json()["data"]


def test_engine_endpoint_returns_404_for_cross_user(client, projects_dir):
    user_a = _register(client, "a@example.com")
    user_b = _register(client, "b@example.com")

    project = ProjectManager.create_project(name="Secret", user_id=user_a["user"]["user_id"])

    a_headers = {"Authorization": f"Bearer {user_a['token']}"}
    b_headers = {"Authorization": f"Bearer {user_b['token']}"}

    as_a = client.get(f"/api/graph/project/{project.project_id}", headers=a_headers)
    assert as_a.status_code == 200

    as_b = client.get(f"/api/graph/project/{project.project_id}", headers=b_headers)
    assert as_b.status_code == 404
    assert as_b.get_json()["success"] is False


def test_engine_endpoint_jwt_gate_when_enforced(client, projects_dir):
    user_a = _register(client, "c@example.com")
    project = ProjectManager.create_project(name="P", user_id=user_a["user"]["user_id"])

    # 默认 FREYA_REQUIRE_AUTH=false：无 token 仍可访问（向后兼容）。
    anon = client.get(f"/api/graph/project/{project.project_id}")
    assert anon.status_code == 200

    # 开启强制认证后：无 token 返回 401。
    client.application.config["FREYA_REQUIRE_AUTH"] = True
    anon_enforced = client.get(f"/api/graph/project/{project.project_id}")
    assert anon_enforced.status_code == 401

    # 携带正确 token 仍可访问。
    ok = client.get(
        f"/api/graph/project/{project.project_id}",
        headers={"Authorization": f"Bearer {user_a['token']}"},
    )
    assert ok.status_code == 200
