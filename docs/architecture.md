# FREYA Architecture

FREYA is a voice-enabled personal AI assistant with a native Android app as its primary interface and a
shared intelligence layer built on top of the **MiroFish** swarm-intelligence engine (AGPL-3.0).

## Layered design

```
┌─────────────────────────────────────────────────────────────────────────┐
│  android/  Native app (Kotlin + Jetpack Compose + Material 3)          │
│  Conversation · Voice (STT/TTS) · Projects · Simulations · Reports     │
│  Search · Settings · Auth · Offline-friendly states                    │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ HTTPS + JWT (REST + SSE)
┌───────────────────────────────▼─────────────────────────────────────────┐
│  backend/  Flask application factory (create_app)                      │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ app/freya/   NEW assistant orchestration layer                   │  │
│  │  persona/    versioned persona config + system-prompt builder    │  │
│  │  adapters/   model-provider adapters (deepseek, compat, mock)    │  │
│  │  chat/       conversation orchestration + streaming (SSE)        │  │
│  │  tools/      typed tool registry → MiroFish capabilities         │  │
│  │  memory/     local preference/project-context store (SQLite)     │  │
│  │  auth/       JWT auth + per-user isolation                       │  │
│  │  jobs/       persistent job status + notifications               │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│  app/api/  graph · simulation · report  (preserved, now per-user)      │
│  app/services/  graph_builder · simulation_runner · report_agent ·     │
│                 ontology_generator · zep_* (memory graph)               │
└───────────────────────────────┬─────────────────────────────────────────┘
              │                          │
     DeepSeek (OpenAI-compat)      Zep Cloud (simulation memory graph)
```

## Separation of concerns

| Layer | Responsibility | Location |
|-------|----------------|----------|
| Persona & conversation | identity, tone, system prompt, chat orchestration | `backend/app/freya/persona`, `chat` |
| Model adapters | provider-specific call/stream | `backend/app/freya/adapters` |
| Tools | registry + dispatcher → engine capabilities | `backend/app/freya/tools` |
| Memory & preferences | user/project scoped local store | `backend/app/freya/memory` |
| MiroFish integration | unchanged engine behind a typed boundary | `backend/app/services`, `api` |
| Auth & authorization | JWT, per-user isolation | `backend/app/freya/auth` |
| Notifications & scheduling | persistent jobs | `backend/app/freya/jobs` |
| Observability & cost | redacted logs, usage counters | `backend/app/freya/observability` |

## Key decisions

1. **Assistant reasoning** uses DeepSeek through an OpenAI-compatible adapter (DeepSeek exposes an
   OpenAI-compatible endpoint). The existing `LLMClient` pattern is reused; simulation LLM config stays
   independent (`LLM_*`) so each workload keeps a compatible provider.
2. **Not a generic chatbot**: the tool registry maps natural-language intents to real MiroFish capabilities
   (run simulation, inspect graph, interview agents, generate report, compare scenarios, summarize).
3. **Auth is new** (upstream has none). `user_id` is added to projects/tasks/reports; cross-user access
   returns 404 to avoid existence leaks.
4. **Zep stays** for the simulation memory graph only. FREYA persona memory is local (SQLite) and
   user-controlled.
5. **Android is native** (Compose). The Vue web UI is preserved for desktop/browser reuse, not substituted
   for the mobile experience.

## Security model

- HTTPS for deployed environments (terminated at a reverse proxy in deployment).
- JWT auth; bcrypt/argon2 password hashing.
- Per-user data isolation (projects, simulations, reports, conversations, memory, jobs).
- API keys live only in backend environment/secrets; **never** in the Android app.
- Rate limits, timeouts, and bounded retries; redacted logs (no keys, tokens, or request bodies).

## Delivery

- Android debug APK built by GitHub Actions (`android-build.yml`) and uploaded as an artifact.
- Backend tests run on GitHub Actions (`backend-tests.yml`); frontend build verified (`frontend-build.yml`).
- Feature-parity matrix: `docs/feature-parity-matrix.md`.
- Deployment/troubleshooting: `docs/deployment.md`, `docs/troubleshooting.md`.
