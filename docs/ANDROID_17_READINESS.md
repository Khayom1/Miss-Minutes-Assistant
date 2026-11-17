# Android 17 (API 37) readiness

## Build baseline

- `minSdk = 26` is preserved.
- `compileSdk = 37` and `targetSdk = 37`.
- Java bytecode remains Java 17.
- Android Gradle Plugin is pinned to 9.3.1; Gradle is pinned to 9.5.0.
- Kotlin remains 2.4.21 and uses AGP built-in Kotlin support. The legacy `org.jetbrains.kotlin.android` application plugin is no longer applied.
- CI runs the complete instrumentation suite on API 37 and retains an API 35 compatibility run.

## Android 17 behavior audit

### Background audio and speech

- Chat speech synthesis uses Android's local `TextToSpeech` engine and never selects a network-required voice.
- TTS is intentionally foreground-only. A lifecycle guard calls `stop()` and clears the speaking UI state on `ON_STOP`; the app does not claim that ordinary chat speech continues after its Activity leaves the foreground.
- The optional wake-phrase service is a separate, user-started microphone foreground service. It checks microphone and notification permission at start, uses Android's on-device recognizer only, publishes a persistent notification with Stop, and returns `START_NOT_STICKY`.
- The overlay is a user-started `specialUse` foreground service. It only displays a shortcut to open the Activity; it does not access the microphone, play audio, or execute device actions.
- No service is started from boot. No indefinite background execution is promised.

### Local network privacy

- The product uses Android's `INTERNET` permission for optional direct HTTPS BYOK requests.
- The app does not discover or connect to LAN devices, open local listening sockets, use multicast discovery, or request `ACCESS_LOCAL_NETWORK`.
- Local inference is an on-device capability only. A future model runtime must not silently introduce a loopback/LAN server.

### Plugin and code-loading boundary

- Plugin packages remain declarative manifests bound to compiled, typed, allowlisted handlers.
- No DEX/JAR/APK/script/shell/reflection-based plugin execution is permitted.
- CI runs `scripts/verify-android-platform-policy.sh` to check the platform contract and reject dynamic code loading and LAN discovery/listening APIs.

## Validation boundaries

- API 37 emulator instrumentation is Android 17 OS evidence, not physical-device evidence.
- API 35 instrumentation remains as a compatibility signal.
- No Samsung Galaxy A35 / Android 17 physical-device run is claimed.
- Production Release signing remains separate and requires an owner-controlled signing key; no keystore or private key belongs in this repository.
