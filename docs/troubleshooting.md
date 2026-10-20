# FREYA Backend — Troubleshooting

Common backend issues and how to resolve them.

## 1. "Distribution torch can't be installed ... no wheel for the current platform"

`camel-ai` (a MiroFish engine dependency) pulls in `torch`, which has no wheel for
some platforms (notably Intel macOS). This is a pre-existing engine dependency
issue, not a FREYA issue.

- On **Linux CI** (`.github/workflows/backend-tests.yml`) this does not occur.
- Locally, install only the FREYA-relevant subset if you only need the
  assistant layer:

  ```bash
  uv venv /tmp/freya-venv --python 3.11
  uv pip install --python /tmp/freya-venv/bin/python \
      flask flask-cors python-dotenv PyJWT openai pytest
  ```

  Then run the FREYA tests that don't require the MiroFish engine:

  ```bash
  /tmp/freya-venv/bin/python -m pytest \
      tests/test_freya_persona.py tests/test_freya_tools.py \
      tests/test_freya_auth.py tests/test_freya_mock_chat.py \
      tests/test_freya_memory.py tests/test_freya_isolation.py -q
  ```

## 2. `401 Authentication required`

- FREYA endpoints (`/api/freya/*`) always require a valid `Authorization: Bearer <JWT>`.
- Obtain a token via `POST /api/freya/auth/register` or `POST /api/freya/auth/login`.
- An expired/malformed token also yields 401.

## 3. Engine endpoints (`/api/graph`, `/api/simulation`, `/api/report`) return 404

- With `FREYA_REQUIRE_AUTH=true`, cross-user resource access returns **404**
  (intentionally, not 403) to avoid leaking existence.
- If you expected access: confirm the resource's owning `user_id` matches the
  token's `sub` claim. Projects created before the FREYA layer have no
  `user_id` and are treated as unowned (visible to any authenticated user).

## 4. Chat always returns the mock reply

The assistant uses the **mock adapter** whenever `DEEPSEEK_API_KEY` is unset.
Set `DEEPSEEK_API_KEY` (and optionally `DEEPSEEK_BASE_URL`/`DEEPSEEK_MODEL`) and
restart to use DeepSeek.

## 5. `429 rate limit exceeded`

`FREYA_RATE_LIMIT_PER_MINUTE` (default `20`) limits per-user chat requests.
Raise it or wait for the window to reset.

## 6. JWT `InsecureKeyLengthWarning`

The default `FREYA_JWT_SECRET` falls back to the short development `SECRET_KEY`.
Set `FREYA_JWT_SECRET` to a random value of at least 32 bytes in production.

## 7. "No module named zep_cloud" (or camel) in tests

The engine-integration isolation tests (`test_freya_engine_isolation.py`) skip
automatically when engine deps are absent; they run in CI where `uv sync`
succeeds.

## 8. Database errors / lock contention

`FREYA_DB_PATH` defaults to `backend/data/freya.sqlite3`. Each operation opens a
short-lived connection; if you see `database is locked`, reduce concurrent
writers or move the DB to a filesystem that supports proper locking (not NFS).
