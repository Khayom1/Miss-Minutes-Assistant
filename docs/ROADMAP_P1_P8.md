# Miss Minutes Assistant — P1–P8 Delivery Roadmap

This roadmap is a sequence of independently accepted implementation phases. A phase is not complete because its design document exists. Completion requires implementation, automated tests, inspection of every required CI step, artifact verification where applicable, and recorded evidence.

## Phase status

| Phase | Status | Scope |
|---|---|---|
| P0 | Complete | Clean Android foundation, architecture/security baseline, CI and repository stabilization |
| P1 | Complete | ChatGPT-like Compose UI foundation |
| P2 | Implemented; external acceptance pending | Direct DeepSeek BYOK and Supabase account/session integration; live provider request still required |
| P3 | Implemented; device/provider acceptance pending | Hybrid voice, permission-gated wake phrase, speech input and hosted ElevenLabs adapter |
| P4 | In progress | Persistent encrypted local conversation history and user-controlled memory; optional authenticated sync remains deferred |
| P5 | Partial; final acceptance pending | Explicitly permissioned Android-native device capabilities; notification/calendar intelligence not implemented |
| P6 | Complete | Typed, consent-gated plugin registry and read-only Android capability adapter; PR #18 merged to main |
| P7 | Complete | Encrypted task state, approvals, bounded orchestration and WorkManager read-only diagnostics; PR #19 merged to main |
| P8 | In progress | Threat model, dependency inventory, release candidate builds, checksums and external release gates |

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
- P2 implementation is merged; live provider acceptance remains deferred until a real user-supplied key succeeds. This is not inferred from CI.

## P3 — Hybrid voice (automated gates passed; final device acceptance deferred)

- Speech-to-text input and text-to-speech output behind provider-neutral interfaces.
- ElevenLabs hosted voice adapter with API credentials held server-side.
- Android/offline voice fallback with truthful capability status.
- Runtime permission, microphone indicator, cancellation and timeout handling.
- No raw audio retained by default; user-controlled opt-in for any persistence.

## P4 — Persistent memory and history (in progress)

- Local conversation storage with schema versioning and migration tests.
- User-visible delete, export and retention controls.
- Optional authenticated cloud sync with per-user ownership and strict RLS.
- Conflict handling, offline-first behavior and encryption-at-rest review.
- No private conversation content in analytics or diagnostic logs.

## P5 — Android-native capabilities (partial; final acceptance pending)

- Only documented Android APIs and explicit runtime permissions.
- User-visible capability manifest, permission rationale and confirmation for sensitive actions.
- Each operation has an allowlist, parameter validation, timeout, cancellation and audit event.
- Notification Intelligence: an opt-in NotificationListenerService receives only notifications Android exposes after the user explicitly grants Notification Access; no hidden app databases, historical inbox scraping, or bypass of notification privacy.
- Classify and summarize exposed notifications on-device by urgency, source app/sender, time sensitivity and actionable intent. Detect and suppress OTP, banking, password-reset and other sensitive categories from AI processing by default.
- Extract candidate calendar events (title, date, time, location, participants and source notification). Validate date/time completeness, deduplicate, and ask the user to confirm ambiguous or external actions.
- Calendar integration uses Android Calendar Provider only after explicit calendar permission, or a user-confirmed Calendar insert intent. No event is silently created from an uncertain message.
- User controls include per-app allowlists, pause/resume, notification retention/deletion, category exclusions and a visible processing/audit history.
- Continuous background work must respect Android foreground-service, notification-listener, battery and process lifecycle restrictions; no guarantee of uninterrupted operation after force-stop or OS restrictions.
- No root requirement, silent background control, unrestricted AccessibilityService automation, raw SMS access by default, or notification scraping outside the documented NotificationListenerService API.

## P6 — Plugin and skill system (accepted; PR #18 merged)

- Versioned declarative manifests with typed input/output JSON schemas.
- Capability allowlists and least-privilege execution.
- Sandboxed execution boundary, resource limits, timeout and revocation.
- Plugin signatures/provenance review and compatibility tests.
- Plugins cannot read provider secrets or arbitrary application storage.

## P7 — Bounded agent orchestration (accepted; PR #19 merged)

- Plans represented as typed, inspectable task graphs.
- Explicit approval for external, destructive, financial or privacy-sensitive actions.
- Visible progress, pause/cancel, idempotency keys and recoverable state transitions.
- Strict tool permissions, step budgets, deadlines and audit trail.
- No claim of unattended multi-hour execution unless the worker actually runs and reports durable state.

## P8 — Production hardening and release (in progress)

- Threat model and dependency/supply-chain audit recorded in docs/P8_THREAT_MODEL.md; resolved dependency report and independent advisory review remain required.
- Resilience, retry/backoff, rate-limit, abuse and privacy tests.
- Performance and memory profiling on supported Android versions.
- Accessibility, localization and UI regression coverage.
- Debug and unsigned release builds, resolved dependency report, artifact checksums and release notes; production signing requires an owner-controlled key.
- Physical-device testing on Samsung Galaxy A35 5G / Android 17 only when an actual device test is performed and recorded. Emulator tests must never be reported as device tests.

P8 evidence and handoff checklist: docs/P8_THREAT_MODEL.md and docs/P8_RELEASE_CHECKLIST.md. P8 is not accepted until all automated checks and external/manual blockers are either completed with evidence or explicitly excluded from a non-production handoff.

## Global release gates

- Never package provider secrets or privileged Supabase keys in the APK.
- Never accept queued, pending, cancelled, skipped or uninspected checks as passing.
- Keep each phase on a separate branch/PR until the predecessor phase has passed.
- Keep the root README deferred until the product implementation is stable.
- Mark P8 complete only when each prior phase's evidence is preserved and release/device limitations are explicit.
