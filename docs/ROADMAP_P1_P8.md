# Miss Minutes Assistant — P1–P8 Delivery Roadmap

This roadmap is a sequence of independently accepted implementation phases. A phase is not complete because its design document exists. Completion requires implementation, automated tests, inspection of every required CI step, artifact verification where applicable, and recorded evidence.

## Phase status

| Phase | Status | Scope |
|---|---|---|
| P0 | Complete | Clean Android foundation, architecture/security baseline, CI and repository stabilization |
| P1 | Complete | ChatGPT-like Compose UI foundation |
| P2 | In progress | Direct DeepSeek connection using a user-owned, locally encrypted API key and optional local inference contract |
| P3 | Not started | Hybrid speech input/output and ElevenLabs adapter |
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
- DeepSeek requests are sent directly from Android to `https://api.deepseek.com/chat/completions` using the user's own API key.
- The key is entered in Settings, masked in the UI, encrypted with AES-256-GCM and a non-exportable Android Keystore key, and stored only on the device.
- No DeepSeek provider key is embedded in the APK, committed to Git, sent to Supabase, or stored in a server secret.
- Provider requests use HTTPS, bounded message/output sizes, timeouts and normalized errors.
- The user can replace or remove the local key at any time.
- Optional local-inference adapter interface; the application must report unavailable rather than fabricate a local model response when no runtime/model is installed.
- Android email/password sign-in/sign-up, session restore/refresh and sign-out are wired to the app UI.
- Session tokens are stored only as AES-256-GCM ciphertext using a non-exportable Android Keystore key; passwords are transient UI state.
- Supabase Auth remains an independent account/session feature; the DeepSeek request path does not send Supabase JWTs or Supabase keys.

P2 acceptance:
- Android unit tests and API 35 instrumentation tests pass; legacy edge-function tests remain regression coverage only.
- Gitleaks, APK/lint, API 35 instrumentation and artifact upload pass on the exact P2 head.
- Missing or rejected DeepSeek keys produce clear, typed UI errors without exposing credentials or signing the user out of Supabase.
- Provider error bodies and credentials are not returned to or logged by the app.
- Live provider test is recorded only when the user supplies a valid DeepSeek API key in the app and a real request succeeds.
- The P3 branch remains separate until P2 is accepted.

## P3 — Hybrid voice (not started)

- Speech-to-text input and text-to-speech output behind provider-neutral interfaces.
- ElevenLabs hosted voice adapter with API credentials held server-side.
- Android/offline voice fallback with truthful capability status.
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
