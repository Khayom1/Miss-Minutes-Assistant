# Miss Minutes — Target Architecture

Status: approved product direction; implementation staged by P0–P8.

## Product definition

Miss Minutes is an Android-first personal AI application with a ChatGPT-like conversation surface and a separate control dashboard. It is not a 3D character or avatar. Its core capabilities are intended to include hybrid AI, natural voice, persistent hybrid memory, permissioned Android control, extensible plugins/skills, and multi-step agents.

## Approved decisions

- AI: hybrid — remote DeepSeek provider plus an optional small local model.
- Voice: hybrid — hosted neural speech plus offline speech recognition/synthesis.
- Android control: maximum lawful and platform-permitted capabilities, using narrow native APIs and explicit permissions.
- Memory: hybrid — local database plus user-controlled cloud persistence and retrieval.
- Interface: both a conversational chat and a control/dashboard workspace.
- Product languages: Tajik, Russian, and English.
- Android baseline: minSdk 26, compileSdk 36, targetSdk 36, Java 17, Kotlin 2.2.21, AGP 8.13.2.

## Runtime boundaries

1. Android client: presentation, local encrypted preferences, local database, audio capture/playback, permission UX, and native platform integrations.
2. Backend: authenticated API gateway, provider proxy, memory synchronization, plugin metadata, agent job orchestration, audit events, and usage controls.
3. AI providers: interchangeable adapters behind a provider-neutral interface. Provider credentials remain server-side.
4. Local inference: optional on-device model adapter with strict memory/thermal/battery budgets and a deterministic fallback when unavailable.
5. Plugin runtime: capability-based tools with explicit manifests, schemas, user-granted permissions, execution limits, and audit logs. Arbitrary third-party code is not loaded into the Android app process.
6. Memory: local SQLite for offline continuity; cloud PostgreSQL for synchronization; vector retrieval is an enhancement, not the only source of truth.

## Request lifecycle

User input → intent classification → policy and permission check → planner → selected model → tool registry → user confirmation when required → execution adapter → result validation → memory policy → response stream → audit event.

Model output is untrusted input. It never directly executes arbitrary shell, SQL, Android intents, plugin code, or financial actions.

## Android control strategy

Prefer, in order:
- documented Android APIs and system intents;
- Android AppFunctions where available and compatible;
- official app/provider APIs and OAuth integrations;
- user-triggered, deterministic accessibility interactions only where permitted by platform and store policy.

A general-purpose AI agent must not rely on AccessibilityService as an unrestricted autonomous UI-control channel. Google Play policy restricts autonomous planning and execution through AccessibilityService for general assistants. The implementation must use explicit disclosure/consent and narrower APIs wherever possible.

AppFunctions are an emerging Android integration surface and must be treated as experimental/device-dependent until verified on the target device and public API release.

## Non-goals for the clean foundation

- 3D avatar, GLB/GLTF assets, SceneView, skeletal animation, lip sync.
- Root access or bypassing Android security boundaries.
- Storing provider API keys in APK resources, BuildConfig, source control, or client-side preferences.
- Claiming unlimited context, unlimited memory, unrestricted Android control, or autonomous background execution without tested platform support.

## Quality attributes

Security and user control take precedence over breadth. All network operations are bounded and cancellable. Background work is durable only where Android scheduling permits it. Every external action is typed, permission checked, observable, and testable. The UI must remain useful if cloud AI, local inference, voice, or plugin services are unavailable.


## Background voice activation — approved product requirement

- The exact wake phrase is **"Miss Minutes"**. Do not prepend "Hey".
- Wake-word detection must run locally on the device; raw microphone audio must not be streamed to the AI provider or uploaded for wake-word detection.
- The user explicitly enables/disables always-listening mode. While active, show a persistent, truthful foreground-service notification and a clear microphone indicator.
- Start the microphone foreground service only from a visible, user-initiated activity after runtime permission and required disclosures. Do not attempt to bypass Android background-start or while-in-use microphone restrictions.
- The service owns audio capture and wake-word detection independently of the Activity lifecycle. Closing the app's task must not stop an already-running, user-enabled foreground service; Force stop, permission revocation, OS termination, battery restrictions and device reboot are separate system conditions and must be handled truthfully.
- On detection, stop wake-word capture before handing audio to the conversation capture state machine; do not claim full duplex until echo cancellation, audio focus and capture/playback arbitration are tested.
- The wake phrase requires a dedicated "Miss Minutes" model (or a verified custom model). A bundled "Hey Jarvis" classifier is not an acceptable substitute.
- Background wake-word acceptance must include physical-device false-positive/false-negative, battery, thermal, lock-screen and task-removal testing on the target Samsung device. Emulator-only evidence is insufficient.
