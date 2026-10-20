# FREYA Device & Live-Verification Runbook

This runbook takes FREYA from a CI-built APK to a live, end-to-end verified installation.
The headless build/tests are already CI-verified; this closes the remaining live-verification gap.

## 0. What you need

- A physical Android device (API 26+) **or** an emulator with Google Play services.
- Backend host reachable from the device (same LAN, or a deployed HTTPS host, or `10.0.2.2` from the Android emulator).
- Three API keys (never committed, never in the APK):
  - `DEEPSEEK_API_KEY` — FREYA assistant reasoning (DeepSeek, OpenAI-compatible).
  - `LLM_API_KEY` + `LLM_BASE_URL` + `LLM_MODEL_NAME` — the MiroFish simulation engine.
  - `ZEP_API_KEY` — the MiroFish memory graph (Zep Cloud).

## 1. Start the backend

```bash
cd backend
uv sync                 # installs deps (Python 3.11/3.12)
cp ../.env.example ../.env   # then edit .env with the keys above
# in .env also set: FREYA_JWT_SECRET=<32+ byte random>, FREYA_REQUIRE_AUTH=true
uv run python run.py    # serves http://0.0.0.0:5001
```

Verify: `curl http://localhost:5001/health` → `{"status":"ok","service":"MiroFish Backend"}`.

> If `uv sync` fails locally (e.g. Intel macOS can't build `torch`), use Docker:
> `docker compose up -d --build` (Linux amd64/arm64 image is CI-built), or run on a Linux host.

## 2. Install the APK

- Download `freya-debug-apk` from the latest successful `Build Android debug APK` run, or build it:
  ```bash
  cd android && gradle :app:assembleDebug
  # emulator: the app defaults to http://10.0.2.2:5001
  # physical device: rebuild with your host, e.g.
  gradle :app:assembleDebug -PFREYA_API_BASE_URL=http://192.168.1.20:5001
  ```
- `adb install android/app/build/outputs/apk/debug/app-debug.apk`

## 3. Register and sign in

1. Open FREYA → **Register** with email + password + display name.
2. The FREYA ONLINE greeting appears once per session (time-adaptive).
3. Send a message in the Home chat — with `DEEPSEEK_API_KEY` set you get live reasoning;
   without it, the deterministic mock adapter replies (still exercises auth/chat/history end-to-end).

## 4. Run a complete MiroFish simulation on Android

1. **Projects** → create a project and upload a PDF/MD/TXT document + describe your prediction request.
2. Build the knowledge graph (graph status is polled live).
3. **Simulation** → prepare (profiles), then **start** and watch live `run-status` (rounds, actions).
4. **Report** → generate, read, and chat with the ReportAgent.
5. **Interview** → chat with individual simulated agents.
6. Confirm outputs are labelled as **simulated/model-generated**, not verified real-world outcomes.

## 5. Verify voice (on-device only)

- Tap the mic in Home; grant RECORD_AUDIO when prompted.
- Speak; text is transcribed (system STT). The reply is spoken via system TTS with stop/interrupt.
- If speech services are unavailable, the app degrades to text-only with an accessible message.

## 6. Security spot-checks

- `FREYA_REQUIRE_AUTH=true` → all `/api/graph|simulation|report` require a valid JWT.
- A second user's token must get **404** (not 403) when accessing the first user's project/simulation/report.
- No API key appears anywhere in the APK or client logs.

## 7. Troubleshooting

See [troubleshooting.md](troubleshooting.md). Common first checks: backend `.env` loaded from the
repo root; `FREYA_REQUIRE_AUTH=true` requires a valid token; simulation refuses to start without
`LLM_API_KEY`/`ZEP_API_KEY`; on-device networking needs cleartext or HTTPS matching the build-time
`API_BASE_URL`.
