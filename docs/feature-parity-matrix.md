# FREYA Feature-Parity Matrix

Legend: ✅ verified · 🔶 implemented, unverified · 🚧 in progress · ⬜ planned · ❌ not applicable

Status reflects actual, tested work — never intent alone.

## MiroFish engine capabilities

| Capability | MiroFish (upstream) | FREYA backend | FREYA Android | Status | Verification |
|------------|---------------------|---------------|---------------|--------|--------------|
| Document ingestion & parsing (pdf/md/txt) | ✅ | preserve | upload UI | ⬜ | — |
| Knowledge graph construction | ✅ | preserve + per-user | inspect view | ⬜ | — |
| GraphRAG & entity relationships | ✅ | preserve | relationships view | ⬜ | — |
| Agent persona generation | ✅ | preserve | review screen | ⬜ | — |
| Simulation environment configuration | ✅ | preserve | config screen | ⬜ | — |
| Multi-agent simulation execution | ✅ | preserve | run + live status | ⬜ | — |
| Scenario interventions | ✅ | preserve | intervene UI | ⬜ | — |
| Scenario comparisons | 🔶 (partial upstream) | expose as tool | compare view | ⬜ | — |
| ReportAgent & report generation | ✅ | preserve | report view | ⬜ | — |
| Interactive conversations with simulated agents | ✅ | preserve | interview chat | ⬜ | — |
| Interactive report investigation | ✅ | preserve | report chat | ⬜ | — |
| Project / run / result management | ✅ | preserve + per-user | project list | ⬜ | — |

## FREYA assistant platform

| Feature | Backend | Android | Status | Verification |
|---------|---------|---------|--------|--------------|
| Persona (versioned config + prompt) | ⬜ | ⬜ | 🚧 | persona JSON authored; loader pending |
| Signature greeting (time-adaptive, once per session) | ⬜ | ⬜ | 🚧 | — |
| Text chat with persona | ⬜ | ⬜ | ⬜ | — |
| Streaming responses (SSE) | ⬜ | ⬜ | ⬜ | — |
| Model adapters (deepseek / compat / mock) | ⬜ | n/a | ⬜ | — |
| Tool orchestration → MiroFish | ⬜ | ⬜ | ⬜ | — |
| Speech-to-text | n/a | ⬜ | ⬜ | — |
| Text-to-speech (interrupt/stop) | n/a | ⬜ | ⬜ | — |
| Hands-free mode (explicit activation) | n/a | ⬜ | ⬜ | — |
| Voice state indicators | n/a | ⬜ | ⬜ | — |
| Text-only fallback | ⬜ | ⬜ | ⬜ | — |
| Persistent conversation history | ⬜ | ⬜ | ⬜ | — |
| Memory & preferences (user-controlled) | ⬜ | ⬜ | ⬜ | — |
| Search (conversations/projects/reports) | ⬜ | ⬜ | ⬜ | — |
| Notifications & persistent jobs | ⬜ | ⬜ | ⬜ | — |
| Settings (personality/model/voice) | ⬜ | ⬜ | ⬜ | — |
| Light & dark themes, accessibility | n/a | ⬜ | ⬜ | — |
| Offline-friendly states & recovery | n/a | ⬜ | ⬜ | — |

## Security & delivery

| Item | Status | Verification |
|------|--------|--------------|
| JWT authentication | ⬜ | — |
| Per-user data isolation | ⬜ | — |
| Secret handling (no keys in app) | ⬜ | — |
| Rate limiting / timeouts / retries | ⬜ | — |
| Redacted logs | ⬜ | — |
| Backend test suite (CI) | ⬜ | — |
| Frontend build (CI) | ⬜ | — |
| Android debug APK (CI) | ⬜ | — |
| Deployment & troubleshooting docs | 🚧 | — |
| AGPL-3.0 notices & corresponding-source | ✅ | license preserved in tree |
