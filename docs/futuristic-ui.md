# FREYA Android — immersive interface

Branch: `feat/futuristic-core-ui`. Everything below is built in Jetpack Compose and
compiled + unit-tested in CI (`.github/workflows/android-build.yml`). The device journey
(`.github/workflows/android-device-check.yml`) runs the real app on an Android 14 emulator
against the real backend in assistant-only mode with the deterministic mock provider.

## Design system

| Layer | Where | Rules |
|---|---|---|
| Colour tokens | `ui/theme/Color.kt`, `FreyaColors` in `Theme.kt` | Void/abyss/midnight base; accents violet `#9A8CFF`, cyan `#6FE3FF`, ice `#CFE8FF`; semantic amber (approval), red (error), green (success). Accents are light and state, never large fills. |
| Type | `ui/theme/Typography.kt` | Unbounded (display, light weight, wide tracking) · Manrope (text) · IBM Plex Mono (telemetry chips, ids, latencies). All `sp`, so font scale is respected. OFL licences in `assets/licenses/`. |
| Spacing / shape | `ui/theme/Tokens.kt` | 4-dp scale (`Space`), 20-dp gutter, 48-dp touch minimum; radii: chip 10, control 14, panel 22, sheet 28. |
| Motion | `Motion` in `Tokens.kt` | micro 120 ms · state 420 ms · layout 520 ms (emphasized curve). Nothing loops unless it represents a live state. Reduce-motion (in-app or system "Remove animations") stops every loop; states remain distinct by colour and shape. |
| Components | `ui/components/Glass.kt` | `FreyaBackdrop` (gradient + stage light + fixed star field, drawn once), `GlassPanel` (translucent fill, hairline border, top sheen — no real blur, so it's cheap on every device), `StatusChip`, `SectionLabel`, `GlassAction`, `InfoRow`. |
| Haptics | `ui/components/Haptics.kt` | Platform constants only (respects system haptic settings): press, start, confirm, reject, tick. Toggle in Settings. |

## FREYA Core states — what drives each one

`deriveCoreState()` (`domain/CoreState.kt`, unit-tested) picks one state from real session signals,
priority top to bottom:

| State | Real signal | Visual |
|---|---|---|
| AWAITING APPROVAL | a sensitive tool request is pending | motion stops, steady amber double ring, approval sheet |
| LISTENING | `SpeechRecognizer` is capturing | 72-bar radial spectrum whose height is the **live mic RMS** (`onRmsChanged`); silent mic = flat |
| TOOL EXECUTION | a tool call is in flight (POST `/api/freya/tools`, or `tool_start` SSE events once the backend emits them) | orbiting satellite + 4-step sequencer; tool name in caption; pill on other screens |
| PROCESSING | a chat request is in flight | 3 counter-rotating segmented arcs; before the first token they chase, then the pattern shifts with the **real streamed character count** |
| SPEAKING | TTS is playing (between the engine's real `onStart` and `onDone`) | a ripple and a mouth pulse per **spoken word** (`onRangeStart`) |
| ERROR | unresolved request/voice/tool error | static broken red ring; Retry / Check connection / Dismiss |
| IDLE | nothing happening | slow breath at ~20 fps |

**Lip/pulse sync.** Word timing comes from `UtteranceProgressListener.onRangeStart`. Engines that don't
report ranges get a documented approximation: a cadence pulse (260 ms ÷ speech rate) that starts
450 ms after real playback starts and stops the moment playback ends. Nothing moves when FREYA is silent.

## Avatar

`ui/core/FreyaAvatar.kt`: a vector line portrait (hood, diadem with three nodes, almond eye-lights, one
mouth line), drawn inside the Core. Expressions are weights driven by real state: listening (eyes widen,
slight tilt), thinking (diadem brightens, gaze up-right), speaking (mouth from the word envelope),
acknowledge (closed "smiling" eyes + green ring after a backend-confirmed task), concern (error/approval).
Settings → FREYA presence → **Abstract core** removes the face entirely. No 3D engine: one Canvas,
a few dozen draw calls.

## Interaction contract

| Control | Behaviour |
|---|---|
| Talk (tap) | starts one-shot recognition; it ends at end of speech, or tap again to finish early |
| Talk (hold ≥ 300 ms) | listens while held; release sends what was heard |
| Talk while FREYA speaks | interrupts speech and starts listening (barge-in) |
| Talk while processing / running a task / awaiting approval | disabled (lock icon); use Stop |
| Talk without permission | requests `RECORD_AUDIO`; if permanently denied, a snackbar links to app settings |
| Stop | cancels listening, or cancels the request and any speech |
| Text field + Send | sends and switches to the conversation view |
| Copy · Read aloud · Retry · Helpful / Not helpful · Details | copy to clipboard · speak this reply · re-send the prompt that produced it · local rating (toggle) · model, transport, first-token and total latency, characters, ≈tokens, conversation id |
| Source chips | open the link found in the reply |
| Suggestions (max 3, hidden while busy) | send a prompt or open Control Center / Power |
| Approval sheet | Approve runs the tool; Deny or swipe-away records a denied run. Nothing runs without Approve. |

## Implemented vs. not built yet

| Area | Status |
|---|---|
| Core states, avatar, push-to-talk, live transcript, interrupt, text input | Implemented |
| Streaming replies, timing details, copy/retry/stop/feedback/read-aloud, sources, code blocks | Implemented |
| Tool execution from Control Center with client-side approval gate + local audit list | Implemented |
| Tool events inside the chat stream | Client ready; **backend doesn't emit them yet** (`/api/freya/chat/stream` sends only delta/done/error) |
| Server-side tool permission enforcement | **Not built** — `POST /api/freya/tools` runs any registered tool for a signed-in user. The app's gate is UX, not security. |
| Model selection | Implemented (free-text identifier saved to backend settings; no hard-coded model list) |
| Automatic / cost / latency / privacy routing, provider fallback | **Not built** — shown as "manual" in Control Center |
| Usage | App-measured counters on this device; token count is an estimate (≈4 chars/token). **Cost is shown as unavailable**, because the backend doesn't report usage or pricing. |
| Feedback learning | Ratings stored on device only; they don't change FREYA automatically |
| Wake word / continuous listening | **Not built** (one-shot recognition only) |
| Light theme | Implemented (Settings → Appearance); dark remains the primary target |

## Performance & accessibility notes

- Per-frame values (clock, mic level, envelopes) live in state objects read only in the draw phase, so
  animation never recomposes the screen. Calm states draw at ~20 fps; error and reduce-motion don't run a clock.
- Voice callbacks are marshalled to the main thread; network runs in coroutines; nothing blocks the UI thread.
- Core and captions are live regions; every icon control has a content description; Talk exposes
  state description ("Listening"/"Not listening") and a semantic click action for TalkBack.

## Bugs fixed while here

- The mic button was hidden on a fresh launch: `isAvailable` also required a recognizer instance that only
  existed after the first listen.
- In hands-free mode, TTS completion (a binder thread) restarted `SpeechRecognizer` off the main thread.
