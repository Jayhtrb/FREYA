# FREYA API Contract

Version 1.0.0 · This document is the single source of truth for the FREYA REST API.
The backend (`backend/app/freya`) implements it; the Android app (`android/`) consumes it.

## 1. Conventions

- Base URL: `http(s)://<host>:5001`
- All FREYA endpoints are under `/api/freya/*`.
- Existing MiroFish endpoints remain under `/api/graph`, `/api/simulation`, `/api/report`.
- Content type: `application/json` (except multipart uploads and SSE streams).
- Auth: HTTP header `Authorization: Bearer <JWT>` on every protected endpoint.
- Response envelope (non-streaming):

```json
{ "success": true, "data": {}, "error": null }
```

On failure: HTTP 4xx/5xx plus:

```json
{ "success": false, "data": null, "error": "human-readable, safe message" }
```

- Error messages must be safe to surface to the client (no stack traces, no secrets).
- IDs: `user_<hex>`, `conv_<hex>`, `proj_<hex12>` (existing), `sim_*` (existing), `rep_*` (existing).
- Pagination: query params `limit` (default 20) and `offset` (default 0).

## 2. Authentication

| Method | Path | Body | Returns |
|--------|------|------|---------|
| POST | `/api/freya/auth/register` | `{ "email", "password", "display_name" }` | `{ token, user }` |
| POST | `/api/freya/auth/login` | `{ "email", "password" }` | `{ token, user }` |
| GET  | `/api/freya/auth/me` | — | `{ user }` |
| POST | `/api/freya/auth/logout` | — | `{ ok: true }` (stateless; client discards token) |

- Passwords are hashed (bcrypt/argon2). Never logged or returned.
- `user`: `{ "user_id", "email", "display_name", "created_at" }`.
- JWT: signed with `FREYA_JWT_SECRET` (fallback `SECRET_KEY`), expiry `FREYA_JWT_TTL_SECONDS` (default 604800).

## 3. Assistant chat

| Method | Path | Body | Notes |
|--------|------|------|-------|
| POST | `/api/freya/chat` | `{ "message", "conversation_id?", "stream": false }` | Returns `{ conversation_id, reply, model, finished }` |
| POST | `/api/freya/chat/stream` | `{ "message", "conversation_id?" }` | `text/event-stream`; events: `delta`, `done`, `error` |
| GET  | `/api/freya/conversations` | — | `{ conversations: [...] }` |
| GET  | `/api/freya/conversations/{id}` | — | `{ conversation }` (messages with role/timestamp) |
| DELETE | `/api/freya/conversations/{id}` | — | `{ ok: true }` |

- The reply is generated through the **persona system prompt** (from `persona/freya.persona.json`) plus a
  **model adapter** (`deepseek` by default; `mock` fallback when no key is configured).
- Tool dispatch: the chat layer may resolve an explicit tool intent from the message and execute a registered
  MiroFish tool before answering. Tool use is declared in the reply (`reply.tool_calls`).

## 4. Tool orchestration

| Method | Path | Body | Notes |
|--------|------|------|-------|
| POST | `/api/freya/tools` | `{ "tool", "args": {} }` | Direct, typed tool execution |
| GET  | `/api/freya/tools` | — | `{ tools: [{ name, description, input_schema }] }` |

Registered tools (v1) wrap the MiroFish engine, never a dummy:

- `run_simulation` — `{ project_id, simulation_requirement, max_rounds? }`
- `inspect_graph` — `{ graph_id }`
- `explain_relationships` — `{ graph_id, entity_uuid? }`
- `interview_agents` — `{ simulation_id, prompts: [{ agent_id, prompt }] }`
- `generate_report` — `{ simulation_id }`
- `compare_scenarios` — `{ simulation_ids: [...] }` (v1: side-by-side summary of two runs)
- `summarize_findings` — `{ report_id }`

## 5. Memory & preferences (user-controlled)

| Method | Path | Body | Notes |
|--------|------|------|-------|
| GET  | `/api/freya/memory` | — | `{ preferences: {}, project_context: {} }` |
| PUT  | `/api/freya/memory/preferences` | `{ "key": "...", "value": ... }` | upsert one preference |
| DELETE | `/api/freya/memory/preferences/{key}` | — | delete one preference |
| GET  | `/api/freya/memory/search` | `?q=` | search preferences + project context |

- Storage is local (SQLite) and **separate from Zep** (Zep stays for the simulation graph).
- Memory is per-user and per-project. Credentials/sensitive values are refused by the store.

## 6. Search

| Method | Path | Query | Notes |
|--------|------|-------|-------|
| GET | `/api/freya/search` | `?q=&scope=` | `scope` in `all`, `conversations`, `projects`, `simulations`, `reports` |

Returns scoped matches with `{ type, id, title, snippet, updated_at }`.

## 7. Notifications & jobs

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/freya/notifications` | `{ notifications: [...] }` |
| POST | `/api/freya/notifications/{id}/read` | mark read |
| GET | `/api/freya/jobs` | `{ jobs: [...] }` |
| GET | `/api/freya/jobs/{id}` | job status/progress/result/error |

Jobs persist across backend restarts (unlike the legacy in-memory `TaskManager`).

## 8. Settings (personality / model / voice)

| Method | Path | Body | Notes |
|--------|------|------|-------|
| GET | `/api/freya/settings` | — | `{ personality, model, voice }` |
| PUT | `/api/freya/settings` | partial object | update user-scoped settings |

## 9. Persona (read-only, for client display)

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/freya/persona` | Public persona fields (identity, tagline, greeting, voice states) |

## 10. Per-user isolation on existing MiroFish endpoints

All `/api/graph`, `/api/simulation`, `/api/report` endpoints require a valid JWT and are scoped to the
authenticated `user_id`:

- Projects, simulations, and reports are stored/retrieved with an owning `user_id`.
- Requests referencing a resource owned by another user return **404** (never 403) to avoid existence leaks.

## 11. Environment variables (backend, never in the Android app)

| Variable | Purpose | Default |
|----------|---------|---------|
| `DEEPSEEK_API_KEY` | Assistant reasoning provider | (unset → mock adapter) |
| `DEEPSEEK_BASE_URL` | DeepSeek OpenAI-compatible base | `https://api.deepseek.com/v1` |
| `DEEPSEEK_MODEL` | Assistant model | `deepseek-chat` |
| `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL_NAME` | Simulation engine (unchanged) | OpenAI-compatible |
| `ZEP_API_KEY` | Simulation memory graph (unchanged) | — |
| `FREYA_JWT_SECRET` | JWT signing secret | `SECRET_KEY` fallback |
| `FREYA_DB_PATH` | SQLite path for auth/memory/jobs | `backend/data/freya.sqlite3` |
| `FREYA_RATE_LIMIT_PER_MINUTE` | Per-user chat rate limit | `20` |

## 12. Non-goals (explicit)

- No continuous listening, real-time duplex, premium neural voice, cross-device control, or autonomous
  computer access until implemented and authorized.
- No WebView as the primary Android experience.
