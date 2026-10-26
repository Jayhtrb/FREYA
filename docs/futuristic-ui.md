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

## FREYA Core — real-time energy waveform

Reusable component: `FreyaCore(state, audioLevel = { … }, audioMeasured, audioBands = { … }, wordTick,
receivedChars, acknowledgeTick, appearance)` in `ui/core/FreyaCore.kt`. It is purely abstract — no face,
figure, avatar, silhouette, rings or dials. The Core never owns microphones, TTS, permissions or the network;
it renders a state plus optional measured audio (a level and, for FREYA's voice, an 8-band spectrum).

| Layer | File | Role |
|---|---|---|
| Tokens | `ui/core/CoreAppearance.kt` | `CoreAppearance` (particle count 2,400, filaments, field waves, stream particles, glow, amplitude, motion speed), `CorePalette` per state (electric cyan, ice blue, violet, restrained magenta; deepened light variant), `StateStyle` per state (energy, turbulence, swirl, audio gain, breath, contraction, scatter, orbit, field, glow, spin). |
| Engine | `ui/core/CoreEngine.kt` | Pure Kotlin, unit-tested. A 3D orb of 2,400 particles (Fibonacci sphere: 72% shell, 28% volume) displaced by a smooth noise field, the audio level and per-latitude frequency bands, rotated and perspective-projected each frame; 7 flowing filament arcs over the surface; 8 horizontal field waves (one per band) with 320 particles streaming outward along them; onset ripples that travel across the particle surface. Struct-of-arrays, no per-frame allocation. |
| Renderer | `ui/core/CoreRenderer.kt` | One Canvas: volumetric haze and a flattened light bank → field waves (glow + core stroke) and the outward stream → far filaments → far particles → luminous nucleus → near particles with motion trails → near filaments with bright heads. Particles are drawn in five colour/depth buckets with batched `drawPoints`/`drawLines`, additive blending in dark theme. |

**States** — `deriveCoreState()` (`domain/CoreState.kt`) picks one from real session signals:

| State | Real signal | What moves |
|---|---|---|
| IDLE | nothing in flight | slow rotation, breathing surface, gentle field swell, filaments drifting |
| LISTENING | `SpeechRecognizer` capturing | orb surface, field waves and ripples follow the **mic level** (cyan/ice); red "MIC ON" indicator + Android's privacy dot |
| PROCESSING | chat request in flight | orb twists into a vortex, filaments braid fast, field waves phase-lock into a tighter braid (violet/magenta); the pattern advances with the **real streamed character count** |
| TOOL EXECUTION | tool call in flight | ~18% of particles peel off into a tilted orbiting stream; caption names the task |
| SPEAKING | TTS audio playing | latitude bands of the orb and each field wave follow **FREYA's measured voice spectrum** (low bands at the equator / long swells, high bands at the poles / short ripples) |
| AWAITING APPROVAL | sensitive tool pending | orb draws in and almost stops, field flattens, magenta-violet; approval sheet |
| ERROR | unresolved error | particles drift apart, desaturated crimson, filaments shorten; Retry / Check connection / Dismiss |

**Frame budget.** 60 fps while listening/speaking/processing/tool; 30 fps idle; 20 fps approval/error;
no frames when the screen isn't visible (lifecycle below STARTED); with reduce-motion the ambient clock is
frozen and the loop stops once colours settle (it runs only while live audio is present).

## Audio-reactive path (measured, not simulated)

- **Microphone:** `SpeechRecognizer.onRmsChanged` (the recognizer's own authorised capture) → normalised
  0..1 → `VoiceController.inputLevel` → smoothed in the engine (attack 30/s, release 8/s).
- **FREYA's voice (phone engine):** each reply sentence is synthesised to WAV with `synthesizeToFile`; the
  RMS envelope (20 ms frames, dBFS → 0..1) is computed on a worker thread (`voice/AudioEnvelope.kt`); the clip
  plays through MediaPlayer and the envelope value **at the current playback position** is published every
  30 ms as `VoiceController.outputLevel`. Sentence N+1 is synthesised while N plays.
- **FREYA's voice (Voicebox engine):** same pipeline, but the WAV comes from Voicebox via the backend —
  see `docs/voicebox.md`.
- **Frequency bands (FREYA's voice):** the same WAV is analysed per 20 ms frame into 8 log-spaced bands
  (140 Hz–5.2 kHz, Goertzel filters, `AudioEnvelope.bands`), and the bands at the playback position are
  published with the level (`VoiceController.outputBands`). The microphone has no spectrum (Android's
  recognizer only reports a level), so while listening the level is spread over a fixed speech-shaped
  profile; the engine flags that as `bandsMeasured = false`.
- **Fallback:** if a TTS engine can't produce readable 16-bit PCM WAV, the rest of the reply is spoken
  directly; `outputMeasured = false`, the caption drops "Voice-synced", and only the engine's word timing
  (`onRangeStart`) drives a gentler pulse. Without word timing nothing pretends to be audio.

## Home screen

The waveform owns the whole upper region of the screen — about 60% of the usable height on a phone (the
journey logs the measured fraction) — with the FREYA wordmark and a compact connection/model pill floating
over its top and a minimal status label + one-line caption over its lower edge. Below it: quick actions that
only open screens or send prompts FREYA can handle, a command bar (text, clear, send; "+" uploads documents to
a project), and the talk button with a Stop control. Navigation: FREYA · Projects · Tools · Settings, without
a container so it never competes with the Core. Conversation mode fades the waveform out and shows the
transcript in the same region, with a small orb in the header.

**Debug preview.** Debug builds include `debug/CorePreviewActivity` (not in release), which shows the real
Core in one state with animation forced on — CI uses it to record every state animating. Listening and
speaking there use a synthetic speech-shaped test signal (labelled on screen) because the CI emulator has no
microphone or speaker:
`adb shell am start -n ai.freya.app.debug/ai.freya.app.debug.CorePreviewActivity --es state SPEAKING`.

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
| Quick actions (max 4, hidden while busy) | send a prompt, or open Projects / Tools / Power |
| Approval sheet | Approve runs the tool; Deny or swipe-away records a denied run. Nothing runs without Approve. |

## Implemented vs. not built yet

| Area | Status |
|---|---|
| Real-time energy waveform Core (3D particle orb, filaments, frequency-band field, ripples), push-to-talk, live transcript, interrupt, text input | Implemented |
| Measured voice amplitude (phone TTS and Voicebox) | Implemented; falls back to timing-only when an engine can't write WAV |
| Voicebox voice | Implemented end to end; **needs Voicebox running on your computer** (`docs/voicebox.md`) |
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

- The engine steps inside `withFrameNanos`; the Canvas reads one frame counter in its draw lambda, so a frame
  re-draws only the Core and never recomposes the screen. Audio levels are read through a lambda each frame.
- 2,400 orb particles + 320 stream particles are drawn with ~16 batched native `drawPoints`/`drawLines`
  calls into reused float buffers (no per-particle draw calls, no per-frame allocation); the Core renders
  into its own offscreen layer so unrelated UI changes don't re-rasterise it.
- On the CI emulator (software rendering) continuous animation saturates the UI thread, so the journey runs
  with animations removed and a separate pass records animated frames + `dumpsys gfxinfo` / `meminfo`.
  Real-device frame timing has **not** been measured yet.
- Voice callbacks are marshalled to the main thread; network runs in coroutines; nothing blocks the UI thread.
- Core and captions are live regions; every icon control has a content description; Talk exposes
  state description ("Listening"/"Not listening") and a semantic click action for TalkBack.

## Bugs fixed while here

- The mic button was hidden on a fresh launch: `isAvailable` also required a recognizer instance that only
  existed after the first listen.
- In hands-free mode, TTS completion (a binder thread) restarted `SpeechRecognizer` off the main thread.
