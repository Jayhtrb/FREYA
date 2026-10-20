# FREYA Backend — Deployment

This document covers deploying the **backend** (the Flask app plus the `app/freya`
orchestration layer). The Android client and frontend are covered elsewhere.

## 1. Prerequisites

- Python 3.11 or 3.12 (the project pins `>=3.11,<3.13`).
- `uv` (recommended) or `pip`.
- Optional: a DeepSeek API key for real assistant reasoning. Without it the
  backend runs the deterministic **mock adapter**.

## 2. Install

```bash
cd backend
uv sync --extra dev          # installs runtime + test deps (managed Python)
```

If you prefer pip:

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
pip install pytest          # dev only
```

## 3. Environment variables

Create a `.env` at the repository root (see `.env.example`). FREYA-specific
variables (never ship these in the Android app):

| Variable | Purpose | Default |
|----------|---------|---------|
| `DEEPSEEK_API_KEY` | Assistant reasoning provider | unset → mock adapter |
| `DEEPSEEK_BASE_URL` | DeepSeek OpenAI-compatible base | `https://api.deepseek.com/v1` |
| `DEEPSEEK_MODEL` | Assistant model | `deepseek-chat` |
| `FREYA_JWT_SECRET` | JWT signing secret | falls back to `SECRET_KEY` |
| `FREYA_JWT_TTL_SECONDS` | JWT expiry | `604800` (7 days) |
| `FREYA_DB_PATH` | SQLite path for auth/chat/memory/jobs | `backend/data/freya.sqlite3` |
| `FREYA_RATE_LIMIT_PER_MINUTE` | Per-user chat rate limit | `20` |
| `FREYA_REQUIRE_AUTH` | Enforce JWT on `/api/graph`, `/api/simulation`, `/api/report` | `false` |

The existing engine variables (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL_NAME`,
`ZEP_API_KEY`) are unchanged.

> **Important:** set `FREYA_JWT_SECRET` to a long random value in production.
> The default secret is a development fallback only.

## 4. Run

```bash
cd backend
uv run python run.py
# or, from a venv:
python run.py
```

The service listens on `http://0.0.0.0:5001` by default (`FLASK_HOST` /
`FLASK_PORT`). Health check: `GET /health`.

## 5. Assistant model selection

- `DEEPSEEK_API_KEY` **set** → `DeepSeekAdapter` (OpenAI-compatible chat).
- `DEEPSEEK_API_KEY` **unset** → `MockAdapter` (deterministic, persona-consistent,
  no network). This is intentional so the assistant works without keys.

## 6. Enabling per-user isolation on the engine (§10)

By default the pre-existing `/api/graph`, `/api/simulation`, `/api/report`
endpoints remain unauthenticated for backward compatibility with the legacy
engine and its tests. To enable the JWT gate and per-user scoping described in
`docs/api-contract.md` §10:

```bash
FREYA_REQUIRE_AUTH=true
```

When enabled, every such endpoint requires a valid Bearer JWT; cross-user access
returns **404** (never 403) to avoid existence leaks. Projects created via the
API are stamped with the caller's `user_id`.

## 7. Database

`FREYA_DB_PATH` points to a single SQLite file (auth users, conversations,
messages, preferences, jobs, notifications, settings). The schema is created
idempotently on startup. The MiroFish graph stays in Zep Cloud; FREYA memory is
local and separate from Zep.

## 8. Tests

```bash
cd backend
uv run pytest -q
```

CI (`.github/workflows/backend-tests.yml`) runs this matrix on Python 3.11 and
3.12. FREYA tests use the mock adapter and require no live keys or network.
