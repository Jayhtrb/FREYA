# FREYA Feature-Parity Matrix

Legend:
- ✅ **Verified** — built and/or tested (CI), with the result recorded below.
- 🔶 **Implemented, not runtime-verified** — code is wired and compiles/passes unit tests, but end-to-end
  runtime verification requires a device/emulator and/or live API keys (DeepSeek, Zep, simulation LLM).
- ⬜ **Planned / not yet implemented**.
- ❌ Not applicable.

> Status reflects actual work, not intent. CI build/tests are the verification authority.

## MiroFish engine capabilities (preserved)

| Capability | MiroFish | FREYA backend | FREYA Android | Status | Verification |
|------------|----------|---------------|---------------|--------|--------------|
| Document ingestion & parsing (pdf/md/txt) | ✅ | preserved | upload UI | 🔶 | engine unchanged; Android multipart upload wired |
| Knowledge graph construction | ✅ | preserved + per-user | inspect view | 🔶 | existing tests pass; not run E2E |
| GraphRAG & entity relationships | ✅ | preserved | relationships view | 🔶 | — |
| Agent persona generation | ✅ | preserved | review screen | 🔶 | — |
| Simulation environment configuration | ✅ | preserved | config screen | 🔶 | — |
| Multi-agent simulation execution | ✅ | preserved | run + live status | 🔶 | needs LLM/Zep keys |
| Scenario interventions | ✅ | preserved | intervene UI | 🔶 | — |
| Scenario comparisons | 🔶 (partial upstream) | `compare_scenarios` tool | compare view | 🔶 | tool wrapper wired |
| ReportAgent & report generation | ✅ | preserved | report view | 🔶 | needs keys |
| Interactive conversations with simulated agents | ✅ | preserved | interview chat | 🔶 | — |
| Interactive report investigation | ✅ | preserved | report chat | 🔶 | — |
| Project / run / result management | ✅ | preserved + per-user | project list | 🔶 | — |

## FREYA assistant platform

| Feature | Backend | Android | Status | Verification |
|---------|---------|---------|--------|--------------|
| Persona (versioned config + loader + prompt) | ✅ | ✅ | ✅ | `test_freya_persona.py` |
| Time-adaptive signature greeting | ✅ | ✅ | ✅ | `test_freya_persona.py` |
| Text chat with persona (mock adapter) | ✅ | ✅ | ✅ | `test_freya_mock_chat.py` |
| Text chat with live DeepSeek | ✅ (adapter) | ✅ | 🔶 | adapter coded; no key supplied yet |
| Streaming responses (SSE) | ✅ | ✅ | 🔶 | endpoint coded; not device-tested |
| Model adapters (deepseek / compat / mock) | ✅ | n/a | ✅ | factory + mock tested |
| Tool orchestration → MiroFish (7 tools) | ✅ | ✅ | ✅ | `test_freya_tools.py` |
| Speech-to-text (system STT) | n/a | ✅ | 🔶 | not device-tested |
| Text-to-speech (interrupt/stop) | n/a | ✅ | 🔶 | not device-tested |
| Hands-free mode (explicit activation) | n/a | ✅ | 🔶 | not device-tested |
| Voice state indicators | n/a | ✅ | ✅ | UI states coded |
| Text-only fallback | ✅ | ✅ | ✅ | silent providers |
| Persistent conversation history | ✅ | ✅ | ✅ | SQLite + `test_freya_mock_chat.py` |
| Memory & preferences (user-controlled) | ✅ | ✅ | ✅ | `test_freya_memory.py` |
| Search (scoped) | ✅ | ✅ | 🔶 | endpoint coded; not E2E |
| Notifications & persistent jobs | ✅ | ✅ | ✅ | SQLite jobs store |
| Settings (personality/model/voice) | ✅ | ✅ | ✅ | settings endpoint tested |
| Light & dark themes, accessibility | n/a | ✅ | ✅ | Compose themes + content descriptions |
| Offline-friendly states & recovery | n/a | ✅ | 🔶 | error/empty/loading states coded |

## Security & delivery

| Item | Status | Verification |
|------|--------|--------------|
| JWT authentication (register/login/me) | ✅ | `test_freya_auth.py` |
| Per-user data isolation (404 cross-user) | ✅ | `test_freya_isolation.py`, `test_freya_engine_isolation.py` |
| Secret handling (no keys in app) | ✅ | keys read from backend env only |
| Rate limiting / timeouts / retries | ✅ | rate limiter coded; bounded by design |
| Redacted logs | ✅ | redacting logger |
| Backend test suite (CI, 3.11 + 3.12) | ✅ | **164 passed** |
| Frontend build (CI) | ✅ | Vite build succeeds |
| Android debug APK (CI) | ✅ | **18 MB APK built** (run `39441899072`) |
| Deployment & troubleshooting docs | ✅ | `docs/deployment.md`, `docs/troubleshooting.md` |
| AGPL-3.0 notices & corresponding-source | ✅ | license preserved; upstream history retained |

## What still requires live verification

- **Running the APK** on a physical device/emulator (none available in this environment).
- **Live assistant reasoning** via DeepSeek (requires `DEEPSEEK_API_KEY`).
- **A complete MiroFish simulation** end-to-end (requires `LLM_API_KEY` + `ZEP_API_KEY`; the engine
  refuses to start without them by design). Tool wrappers call the real engine and will return the
  engine's own errors when prerequisites are absent — they are not dummies.
- **On-device voice** STT/TTS behavior (uses the Android system speech services; not exercisable headless).
