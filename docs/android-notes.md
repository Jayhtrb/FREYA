# FREYA — Android (native)

Kotlin + Jetpack Compose + Material 3 client for the FREYA backend
(`docs/api-contract.md` is the source of truth). This module lives under `android/`
and is built by `.github/workflows/android-build.yml` (`gradle :app:assembleDebug`).

## Toolchain & versions

| Component | Version |
|-----------|---------|
| Gradle | 8.9 (CI installs it via `gradle/actions/setup-gradle`) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Compose Compiler Gradle plugin | 2.0.21 (matches Kotlin) |
| Kotlin serialization plugin | 2.0.21 |
| KSP | 2.0.21-1.0.27 |
| Compose BOM | 2024.12.01 |
| Navigation Compose | 2.8.5 |
| Lifecycle | 2.8.7 |
| Activity Compose | 1.9.3 |
| Core KTX | 1.15.0 |
| Room | 2.6.1 |
| DataStore Preferences | 1.1.1 |
| Retrofit | 2.11.0 (+ first-party `converter-kotlinx-serialization`) |
| OkHttp | 4.12.0 (+ `logging-interceptor`, `okhttp-sse`) |
| kotlinx-serialization-json | 1.7.3 |
| kotlinx-coroutines-android | 1.9.0 |

SDK: `compileSdk 35`, `targetSdk 35`, `minSdk 26`, Java 17. Package `ai.freya.app`.

## Base URL

Build-time configurable; default is `http://10.0.2.2:5001` (Android emulator loopback):

```bash
gradle :app:assembleDebug -PFREYA_API_BASE_URL=https://your.host:5001
```

Cleartext HTTP is allowed for development (`android:usesCleartextTraffic="true"`);
remove it and use HTTPS for production.

## Architecture

- **DI**: manual `AppContainer` owned by `FreyaApplication` (no Hilt → no extra KSP
  plugin risk on CI). ViewModels are `AndroidViewModel`s and resolve the container
  through the Application, so `viewModel<X>()` needs no custom factory.
- **Networking**: single Retrofit `FreyaApi` against the `{success, data, error}`
  envelope; `ApiEnvelope` + `requireData<T>()` decode the polymorphic `data` payload.
  JWT is injected by `AuthInterceptor` (backed by a volatile `AuthTokenStore`).
  SSE chat streaming uses OkHttp `okhttp-sse` (`ChatSseClient`).
- **Local**: Room (`conversations`/`messages`/`projects` cache) + DataStore
  (auth token + personality/model/voice preferences).
- **Voice**: `SpeechToTextProvider` / `TextToSpeechProvider` abstraction with
  `AndroidSpeechRecognizerProvider` (one-shot STT) and `AndroidTextToSpeechProvider`
  (system TTS). `VoiceController` exposes the `idle/listening/thinking/speaking/
  task_execution` state machine. No neural/continuous/duplex claims; unavailable
  speech falls back to text-only.

## Screens

See `docs/futuristic-ui.md` for the immersive interface (FREYA Core, avatar, design system,
Control Center, interaction contract, implemented vs. not-built).

Auth → Home (FREYA Core + streaming conversation + push-to-talk) · Control Center · Projects (create + upload) · Simulation
(create/prepare/run + run-status polling) · Report (view/download/agent chat) ·
Interview · Search (scoped) · Settings (personality/model/voice + memory + sign out) ·
Jobs (tasks + notifications).

## Notes / known limitations

- The `gradle/wrapper/` jar is intentionally omitted: this host has no Android
  SDK/JDK to run `gradle wrapper`. CI uses `gradle` (8.9) directly via
  `gradle/actions/setup-gradle`, so the wrapper is not required for CI.
- The FREYA (`/api/freya/*`) backend module was not present in the checkout at the
  time of writing; DTOs follow `docs/api-contract.md` exactly and use
  `ignoreUnknownKeys` + tolerant decoding so minor backend field drift degrades
  gracefully rather than crashing.
- `compileSdk 35` requires AGP ≥ 8.6; if `android-actions/setup-android@v3` does
  not pre-install `platforms;android-35`, AGP auto-installs it once licenses are
  accepted by the action.
