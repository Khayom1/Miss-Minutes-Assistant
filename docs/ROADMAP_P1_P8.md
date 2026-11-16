# Roadmap — P1 through P8

Status: planning baseline only. A roadmap entry is not evidence that a phase is implemented or accepted. Each phase is a separate delivery gate with its own implementation, tests, commit, pull request review, and green CI on the exact final commit.

## Global delivery rules

- Implement phases in order: P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8. Do not start dependent work before its prerequisites are accepted.
- Use `docs/ARCHITECTURE.md` and `docs/P0_SECURITY_MODEL.md` as binding architecture and security constraints.
- Keep provider master credentials exclusively in backend secret storage. Never place them in APK assets, Android resources, BuildConfig, client preferences, source control, logs, CI variables exposed to build steps, or test fixtures.
- Use deterministic mocks and contract tests when provider credentials or service accounts are unavailable. Do not create paid projects or assume credentials exist.
- Treat model output, retrieved content, plugin metadata, and tool arguments as untrusted input. Authorization and policy enforcement must live outside the model.
- Prefer narrow Android platform APIs and user-triggered intents. Never add root requirements or unrestricted autonomous AccessibilityService control.
- Keep network calls bounded, cancellable, TLS-only, rate-limited, and observable. Redact tokens, message bodies, and personal data from logs.
- Every phase acceptance record must name the exact commit SHA, CI run URL and conclusion, test suites and counts, generated artifacts, known limitations, and any external validation still outstanding.
- A CI run is acceptable only when every required job and step is inspected and completed successfully on the exact commit being accepted. Pending, skipped, cancelled, missing, or uninspected checks do not pass.
- README changes remain deferred until product implementation is stable.

## P1 — ChatGPT-like Compose UI

**Status:** implementation and acceptance evidence are present in `docs/P1_ACCEPTANCE.md`; the phase remains subject to PR merge and main-branch CI verification.

### Scope
- Compose application shell with Chat, Dashboard, History, and Settings destinations.
- Trilingual interface: Tajik, Russian, and English.
- Light and dark theme selection.
- Local-only chat composer preview and truthful connection-state indicators.
- No provider connection, remote history, or persistent user preferences in this phase.

### Acceptance criteria
- All four destinations render and navigation works.
- Each language has explicit UI copy for all visible P1 controls and screens.
- Theme changes update the Material color scheme without restarting the process.
- Empty input cannot create a message; submitted input is trimmed and displayed as a local preview.
- UI clearly communicates that AI services are not connected.
- No claim is made that chat history or settings are persistent.

### Tests
- Compose instrumentation tests for initial rendering, each destination, language switching, theme switching, and local composer submission.
- Unit tests for any extracted navigation or localization logic.
- `testDebugUnitTest`, `assembleDebug`, `lintDebug`, API 35 `connectedDebugAndroidTest`, and Gitleaks.

### Risks and limitations
- Preview messages are in-memory only; History is an empty state.
- Language and theme are not durable preferences.
- Emulator validation does not establish behavior on the physical Galaxy A35.

## P2 — Secure AI provider abstraction

### Scope
- Introduce a provider-neutral client contract and typed request/response/error models.
- Implement a DeepSeek-compatible backend proxy (for example, a Supabase Edge Function source tree) with authentication, authorization, input/output bounds, rate limits, timeout handling, and safe error mapping.
- Store the DeepSeek master key only in backend secret storage; the Android app receives only an authenticated application session or narrowly scoped token.
- Add an optional local inference adapter behind the same provider contract, with feature detection and resource budgets. A deterministic offline response remains available if no model runtime is installed.
- Keep provider selection and connection state explicit in Settings/Dashboard.

### Acceptance criteria
- Android source, resources, BuildConfig, APK, logs, and CI artifacts contain no provider master credentials.
- The backend rejects unauthenticated requests and malformed, oversized, or unsupported payloads.
- Provider host and protocol are allowlisted; TLS is mandatory; redirects are rejected; responses and execution time are bounded.
- Client cancellation propagates where supported; retries are limited and do not duplicate user-visible messages.
- Local inference is optional and cannot prevent the app from operating when unavailable.
- Mocked provider contract tests pass without real keys; no live-provider success is claimed without a separately authorized credentialed test.

### Tests
- Unit/contract tests for provider adapters, serialization, error mapping, timeout, cancellation, response-size limits, host validation, and offline fallback.
- Backend tests for authentication, authorization, rate limiting, validation, secret lookup, and redaction.
- Static secret scanning and a test proving no provider key is compiled into the APK.
- CI build, lint, unit tests, and backend test suite.

### Risks and dependencies
- Requires a separately provisioned authenticated backend and secret store for live use; source code and mock tests can be completed without provisioning.
- Local inference depends on compatible runtime/model availability and must be tested against memory, thermal, and battery budgets.

## P3 — Hybrid voice

### Scope
- Voice input through Android speech recognition with runtime capability detection.
- Speech output through Android TextToSpeech as the offline baseline.
- Optional hosted neural speech through a backend proxy; provider credentials remain server-side.
- Explicit microphone permission flow, recording indicator, cancellation, and fallback behavior.
- Language-aware voice selection for Tajik, Russian, and English, with truthful fallback when a locale is unsupported.

### Acceptance criteria
- Voice features are opt-in and never start recording without a user action and permission.
- Recording and playback have visible states and reliable cancellation.
- Network/hosted speech failure falls back to available Android TTS or a clear unsupported-state message.
- No audio is retained or uploaded beyond the documented request lifecycle.
- Tajik speech support is reported based on actual device/service capability, not assumed.

### Tests
- Unit tests for voice state machine, permission denial, interruption, timeout, cancellation, and fallback selection.
- Instrumentation tests with fake speech/TTS engines and permission grant/deny cases.
- Mock backend tests for hosted speech request bounds and credential isolation.
- Manual device tests for microphone routing, Bluetooth, background interruption, and supported locales.

### Risks and dependencies
- Speech recognition language availability varies by Android image, installed services, and network.
- Hosted voice requires an authorized account and backend secret; offline TTS must remain functional without it.

## P4 — Local persistent memory and optional cloud sync

### Scope
- Room/SQLite local conversation and preference storage with schema migrations.
- User-visible history search/list/detail and conversation deletion.
- Data minimization, encryption at rest where supported, and safe backup/export behavior.
- Explicit export and delete-all controls.
- Optional authenticated cloud sync with conflict-aware reconciliation; local-only mode remains complete.

### Acceptance criteria
- Conversations survive process death and device restart.
- Export produces a documented portable format and includes only user-selected data.
- Delete operations remove local records and queue authenticated cloud deletion when sync is enabled.
- Cloud sync is disabled by default until the user authenticates and opts in.
- Sync failures do not destroy local data; conflicts are deterministic and observable.
- Sensitive content is excluded from diagnostic logs.

### Tests
- DAO/repository unit tests, migration tests from every shipped schema, transaction/rollback tests, and restart persistence tests.
- Export/import round-trip, delete verification, encryption-at-rest checks, and sync conflict/retry tests.
- Authorization tests proving one account cannot access another account's memory.

### Risks and dependencies
- Cloud sync requires authenticated backend storage and privacy/data-retention decisions.
- Encryption and backup behavior must be validated on supported Android versions.

## P5 — Narrow, permissioned Android capabilities

### Scope
- Typed capability registry for a small, documented set of user-requested Android actions.
- Prefer documented APIs and system intents (for example, open a selected app or open a system-owned settings screen).
- Explicit runtime permission rationale and user confirmation immediately before sensitive actions.
- Capability availability checks and clear unsupported-state UX.

### Acceptance criteria
- Each capability has a narrow typed input schema, allowlisted operation, permission declaration, and documented failure modes.
- The model cannot invoke arbitrary intents, shell commands, SQL, or unrestricted UI automation.
- Sensitive or irreversible actions require immediate user confirmation.
- No root access and no unrestricted autonomous AccessibilityService control are introduced.
- Revocation and permission denial stop the operation safely.

### Tests
- Unit tests for schema validation, allowlists, permission gates, denial/revocation, and policy decisions.
- Instrumentation tests for supported intents and permission UX.
- Security tests for malformed arguments, prompt-injection attempts, unauthorized tool invocation, and confirmation bypass.
- Manual device verification for each capability actually claimed.

### Risks and dependencies
- Android APIs and AppFunctions vary by OS version and app capability.
- Some capabilities require physical-device verification and/or platform policy review.

## P6 — Declarative plugin and skill registry

### Scope
- Declarative, versioned plugin manifests with typed input/output schemas.
- Capability allowlists, explicit user grants, execution quotas, timeouts, and revocation.
- Sandboxed execution boundary; arbitrary third-party code is never loaded into the Android application process.
- Trust/provenance metadata and compatibility checks.

### Acceptance criteria
- Plugins declare capabilities and schemas before installation or activation.
- Unknown capabilities, invalid signatures/provenance, schema violations, and revoked grants fail closed.
- Plugin outputs are treated as untrusted data and cannot expand permissions.
- Resource limits and cancellation are enforced by the host boundary.
- User can inspect and revoke each plugin grant.

### Tests
- Manifest parser/property tests, schema validation, version compatibility, signature/provenance checks, revocation, quota, timeout, and cancellation tests.
- Adversarial tests for prompt injection, capability escalation, oversized output, path traversal, and malformed payloads.
- Sandbox isolation tests proving plugins cannot access app secrets or unrestricted Android APIs.

### Risks and dependencies
- A safe sandbox requires a deliberately constrained execution technology and must not be approximated by running arbitrary scripts in-process.
- Remote plugin distribution introduces supply-chain and update-signing risks.

## P7 — Bounded multi-step task orchestration

### Scope
- Explicit task state machine with bounded plans, typed steps, deadlines, retry budgets, and idempotency keys.
- User-visible plan, progress, result, and audit history.
- Approval gates before sensitive or irreversible steps.
- Cancellation and safe recovery after process death where Android scheduling permits.
- Tool execution only through the P5/P6 capability boundary.

### Acceptance criteria
- Every plan has maximum steps, wall-clock deadline, retry budget, and per-step capability checks.
- The user can inspect the plan and cancel before or during execution.
- Approval is bound to the exact action parameters and expires when they change.
- Duplicate delivery/retry cannot duplicate non-idempotent effects.
- Failed, cancelled, or interrupted tasks produce an accurate final state and audit record.
- Model output cannot bypass policy or invoke tools directly.

### Tests
- State-machine/property tests, deterministic planner tests, cancellation and process-death recovery tests.
- Idempotency/replay tests, approval-binding tests, deadline/retry exhaustion, and audit integrity tests.
- Integration tests with fake tools and adversarial model outputs.

### Risks and dependencies
- Reliable background execution is bounded by Android background and foreground-service policies.
- High-impact actions require careful confirmation UX and server-side idempotency where applicable.

## P8 — Hardening and release readiness

### Scope
- End-to-end security review, dependency review, reliability and offline behavior.
- Performance, memory, battery, thermal, and startup profiling.
- Complete Tajik/Russian/English localization, accessibility semantics, large-font/layout validation, and contrast checks.
- Release build configuration, signing workflow, privacy disclosures, data-safety documentation, and release notes.
- Physical-device validation on the Samsung Galaxy A35 5G and supported Android versions.

### Acceptance criteria
- All P1–P7 acceptance records are complete and no unresolved critical/high security issue remains.
- Release build is reproducible, signed using an owner-controlled private key, and verified without exposing signing material.
- No secrets or sensitive user content appear in APK, logs, or build artifacts.
- Offline behavior, provider outage, permission denial, cancellation, data export/deletion, and recovery are validated.
- Accessibility and localization checks pass across supported screen sizes and font scales.
- Physical-device test evidence identifies exact device model, Android version/build, test date, and results; emulator-only results are not substituted.
- Release artifact checksum, version, changelog, and rollback procedure are recorded.

### Tests
- Full unit, instrumentation, backend, security, dependency, and static-analysis suites.
- Performance benchmarks and soak tests with documented thresholds.
- Manual accessibility/localization review and physical-device regression matrix.
- Release signing verification and artifact integrity checks.

### Risks and dependencies
- Physical Galaxy A35 access and an owner-controlled release signing key are external release gates; neither may be claimed or fabricated.
- Store policy, privacy disclosures, and third-party service terms may require owner review before public distribution.

## Phase acceptance template

Each phase must add a dedicated `docs/P{N}_ACCEPTANCE.md` containing:

1. Exact scope implemented and intentionally excluded.
2. Exact final commit SHA and PR URL.
3. CI run URL(s), conclusion, and inspected job/step results on that exact SHA.
4. Unit, instrumentation, backend, and security test evidence with counts where available.
5. Artifact name, ID, checksum, and expiration when produced.
6. Known limitations, external dependencies, and uncompleted manual/device checks.
7. Explicit statement that the phase does not imply acceptance of later phases.
