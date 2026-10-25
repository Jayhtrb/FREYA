# FREYA's voice with Voicebox

[Voicebox](https://github.com/jamiepine/voicebox) (MIT) is a local voice studio with an
OpenAI-compatible speech API. FREYA uses it so replies are spoken in a voice you own, with the
Core animating from the measured loudness of that audio.

```
Phone (FREYA app) ──POST /api/freya/tts──▶ FREYA backend ──POST /v1/audio/speech──▶ Voicebox
        ◀────────────── WAV per sentence ◀─────────────────────────────────────────┘
```

## 1. Run Voicebox on your computer

Download it from https://voicebox.sh (macOS Intel/Apple Silicon, Windows) or `docker compose up` from the
repo. It listens on `http://127.0.0.1:17493`.

On an **Intel Mac**, Voicebox's fast Apple path (MLX) isn't available. Start with the lightweight CPU
engines (**Kokoro** presets or **LuxTTS**); the larger Qwen engines will be slow without a GPU.

## 2. Create FREYA's voice

In Voicebox, create a voice profile named **FREYA**. Two good options:

- **Qwen VoiceDesign** — describe the voice in words (no reference audio), e.g.
  *"Warm, composed woman in her thirties, clear British accent, quietly witty, unhurried."*
- **Kokoro** — pick one of the preset voices (fastest on CPU).

Only clone a real person's voice with their consent.

## 3. Point the FREYA backend at it

Copy `.env.example` → `.env.local` in the repo root (git-ignored) and set:

```
VOICEBOX_URL=http://127.0.0.1:17493
VOICEBOX_VOICE=FREYA
# optional: a specific engine/model id from GET /v1/models; default "tts-1" = the profile's engine
VOICEBOX_MODEL=
```

Restart the backend (`cd backend && uv run python run_freya.py`). If the backend runs in Docker, use
`http://host.docker.internal:17493` instead of `127.0.0.1`.

## 4. Turn it on in the app

Settings → Voice → **Voice engine: Voicebox**, and switch **Voice output** on. The status line under the
choice says whether the backend can reach Voicebox. Tools → Connected AI providers shows it as well.

## Behaviour and limits

- Replies are split into sentences; sentence 2 is generated while sentence 1 plays.
- Speech speed in Settings is passed to Voicebox (pitch-preserving stretch).
- If Voicebox is unreachable or fails on a sentence, the rest of that reply is spoken by the phone voice
  (no overlap, no silence). The caption only says "Voice-synced" while audio is being measured.
- First-sentence latency depends entirely on your computer and the engine you picked.
- The backend never accepts a URL from the app; only `VOICEBOX_URL` in server config is used.
