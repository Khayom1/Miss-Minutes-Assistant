# Miss Minutes Assistant — P1–P8 Delivery Roadmap

This roadmap is a sequence of independently accepted implementation phases. A phase is not complete because its design document exists. Completion requires implementation, automated tests, inspection of every required CI step, artifact verification where applicable, and recorded evidence.

## Phase status

| Phase | Status | Scope |
|---|---|---|
| P0 | Complete | Clean Android foundation, architecture/security baseline, CI and repository stabilization |
| P1 | Complete | ChatGPT-like Compose UI foundation |
| P2 | CI passed; live provider acceptance pending | Authenticated AI provider gateway, DeepSeek proxy and optional local inference contract |
| P3 | In progress | TTS-only first slice: Android on-device TextToSpeech; cloud voice providers deferred |
| P4 | Not started | Persistent local conversation history and memory, optional authenticated sync |
| P5 | Not started | Explicitly permissioned Android-native capabilities |
| P6 | Not started | Typed plugin/skill manifest, capability allowlist and execution boundary |
| P7 | Not started | Bounded multi-step agent/task orchestration |
| P8 | Not started | Security, reliability, performance, accessibility and release hardening |

## P0 — Foundation (accepted)

- Non-3D Android foundation; no legacy model, SceneView, animation, overlay, AccessibilityService or notification-reader implementation.
- Pinned Android build toolchain and CI.
- Gitleaks, JVM unit tests, APK assembly, lint, API 35 instrumentation and artifact upload all pass.
- P0 evidence is recorded in `docs/P0_ACCEPTANCE.md` and `docs/P0_EXECUTION_PLAN.md`.

## P1 — ChatGPT-like UI (accepted)

- Chat, Dashboard, History and Settings destinations.
- English, Russian and Tajik UI copy.
- Light and dark themes.
- Local preview composer, explicitly labelled as not yet connected to an AI service.
- P1 implementation and limitations are recorded in `docs/P1_ACCEPTANCE.md`.

## P2 — AI and provider gateway (in progress)

Deliverables:
- Provider-neutral request/response/error contracts in Kotlin, with a fail-closed optional local-runtime adapter seam.
- Authenticated Supabase Edge Function as the only remote AI egress path.
- DeepSeek model and output limits controlled by the server, not the Android client.
- DeepSeek API credential stored only as a Supabase Edge Function secret.
- Persistent per-user request quota enforced by a private database table and a server-only RPC.
- Optional local-inference adapter interface; the application must report unavailable rather than fabricate a local model response when no runtime/model is installed.
- Android email/password sign-in/sign-up, session restore/refresh and sign-out are wired to the app UI.
- Session tokens are stored only as AES-256-GCM ciphertext using a non-exportable Android Keystore key; passwords are transient UI state.
- Android transport uses HTTPS, the signed-in user's JWT and the publishable key. It must never contain the DeepSeek credential.
- Supabase `sb_secret_*` credentials are sent on `apikey` only; legacy service-role JWT fallback uses the Bearer header.
- The edge handler streams and caps request bodies before parsing and does not consume quota when provider configuration is missing.

P2 acceptance:
- Deno edge-function tests and Android unit tests pass.
- Gitleaks, APK/lint, API 35 instrumentation and artifact upload pass on the exact P2 head.
- Auth rejects missing/expired sessions; server quota blocks over-limit calls before provider egress.
- Provider error bodies and credentials are not returned to or logged by the app.
- Live provider test is recorded only when a valid Supabase Auth session and server-side DeepSeek secret are configured.
- The exact deployed function version is inspected with `verify_jwt=true`; the service-role header semantics are verified by Deno tests.
- P3 code may be prepared on a dependent draft branch at the owner's direction, but it must not merge or be accepted until P2 is accepted.

## P3 — TTS-first voice (in progress)

First delivery is deliberately limited to text-to-speech; STT, microphone permission and live voice conversations are deferred.

- Android native TextToSpeech engine; no cloud TTS provider.
- Selects the best available installed offline voice matching the device locale.
- Uses Speak/Stop controls for assistant messages.
- TTS text is processed on-device; no TTS API, API key, server function or audio upload.
- Graceful failure when no compatible local voice is installed.
- Stops speech on user cancellation/sign-out and shuts down the engine with the Activity lifecycle.
- Device-specific voice quality and Tajik language availability must be verified on physical hardware.

## P3 — Remaining voice scope (not started)

- Speech-to-text input behind a provider-neutral interface (deferred).
- Additional voice providers (deferred).
- Offline voice fallback with truthful capability status (deferred).
- Runtime permission, microphone indicator, cancellation and timeout handling.
- No raw audio retained by default; user-controlled opt-in for any persistence.

## P4 — Persistent memory and history (not started)

- Local conversation storage with schema versioning and migration tests.
- User-visible delete, export and retention controls.
- Optional authenticated cloud sync with per-user ownership and strict RLS.
- Conflict handling, offline-first behavior and encryption-at-rest review.
- No private conversation content in analytics or diagnostic logs.

## P5 — Android-native capabilities (not started)

- Only documented Android APIs and explicit runtime permissions.
- User-visible capability manifest, permission rationale and confirmation for sensitive actions.
- Each operation has an allowlist, parameter validation, timeout, cancellation and audit event.
- No root requirement, silent background control, unrestricted AccessibilityService automation or notification scraping.

## P6 — Plugin and skill system (not started)

- Versioned declarative manifests with typed input/output JSON schemas.
- Capability allowlists and least-privilege execution.
- Sandboxed execution boundary, resource limits, timeout and revocation.
- Plugin signatures/provenance review and compatibility tests.
- Plugins cannot read provider secrets or arbitrary application storage.

## P7 — Bounded agent orchestration (not started)

- Plans represented as typed, inspectable task graphs.
- Explicit approval for external, destructive, financial or privacy-sensitive actions.
- Visible progress, pause/cancel, idempotency keys and recoverable state transitions.
- Strict tool permissions, step budgets, deadlines and audit trail.
- No claim of unattended multi-hour execution unless the worker actually runs and reports durable state.

## P8 — Production hardening and release (not started)

- Threat model and dependency/supply-chain audit.
- Resilience, retry/backoff, rate-limit, abuse and privacy tests.
- Performance and memory profiling on supported Android versions.
- Accessibility, localization and UI regression coverage.
- Release signing/build reproducibility, artifact checksums and release notes.
- Physical-device testing on Samsung Galaxy A35 5G / Android 17 only when an actual device test is performed and recorded. Emulator tests must never be reported as device tests.

## Global release gates

- Never package provider secrets or privileged Supabase keys in the APK.
- Never accept queued, pending, cancelled, skipped or uninspected checks as passing.
- Keep each phase on a separate branch/PR until the predecessor phase has passed.
- Keep the root README deferred until the product implementation is stable.
- Mark P8 complete only when each prior phase's evidence is preserved and release/device limitations are explicit.
