# Roadmap — P1 through P8

Status: planning baseline only. A roadmap entry is not evidence that a phase is implemented or accepted. Every phase requires implementation, meaningful tests, a dedicated acceptance record, a commit/PR, and green CI on the exact final commit.

## Global delivery gates

- Deliver strictly in order: P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8.
- Treat `docs/ARCHITECTURE.md` and `docs/P0_SECURITY_MODEL.md` as binding.
- Provider master credentials stay in backend secret storage only. Never put them in APKs, Android resources, BuildConfig, client preferences, source control, logs, CI artifacts, or test fixtures.
- Use deterministic mocks when service credentials/accounts are unavailable. Do not create paid projects or assume account access.
- Model output, retrieved content, plugin metadata, and tool arguments are untrusted. Authorization and policy enforcement must remain outside model context.
- Prefer narrow documented Android APIs and system intents. No root access or unrestricted autonomous AccessibilityService control.
- Network calls must be TLS-only, bounded, cancellable, rate-limited, and safely logged with tokens and personal content redacted.
- A phase passes only when its implementation and tests are present, all required jobs/steps are inspected and successful on the exact final commit, and its acceptance document records evidence and limitations. Pending, skipped, cancelled, missing, or uninspected checks never pass.
- README changes remain deferred until product implementation is stable.

## P1 — ChatGPT-like Compose UI

**Status:** implemented in PR #5; acceptance is recorded in `docs/P1_ACCEPTANCE.md`. Main-branch CI remains a separate verification gate.

### Scope
- Compose shell with Chat, Dashboard, History, and Settings.
- Tajik, Russian, and English UI copy; light/dark theme selection.
- Local-only chat composer preview and truthful connection-state indicators.
- No provider integration, remote history, or durable settings in P1.

### Acceptance criteria
- Four destinations render and navigate correctly.
- All visible P1 UI copy is explicitly translated in all three languages.
- Theme selection changes the Material color scheme without a restart.
- Empty input cannot create a message; submitted text is trimmed and shown as local preview.
- UI states clearly that AI services are not connected.
- No claim of persistent chat history or preferences.

### Tests
- Compose instrumentation: initial rendering, navigation, language selection, theme switch, and composer submission.
- Unit tests for extracted localization/navigation logic.
- Gitleaks, unit tests, APK assembly, lint, and API 35 instrumentation.

### Risks and limitations
- Messages are in-memory only; History is an empty state.
- Language/theme are not durable preferences.
- Emulator tests do not establish physical Galaxy A35 behavior.

## P2 — Secure AI provider abstraction

### Scope
- Provider-neutral Kotlin contract with typed request, response, and error models.
- DeepSeek-compatible backend proxy source (for example, Supabase Edge Function), authenticated user access, request validation, response bounds, rate limits, timeout, and safe error mapping.
- DeepSeek master key exists only in backend secret storage. Android receives a user session or narrowly scoped application token, never the provider key.
- Optional local inference adapter behind the same contract, with feature detection, resource budgets, and deterministic offline fallback.
- Explicit provider/connection state in UI.

### Acceptance criteria
- No provider master key in Android source, resources, BuildConfig, APK, logs, CI, or test fixtures.
- Backend rejects unauthenticated, malformed, oversized, and unsupported requests.
- Provider host/protocol are fixed and allowlisted; TLS is mandatory; redirects are rejected.
- Responses and execution time are bounded; cancellation is propagated where supported.
- Local inference is optional and cannot make the app unusable when absent.
- Mock contract tests pass without real credentials; no live-provider success is claimed without authorized testing.

### Tests
- Kotlin adapter contract, serialization, error mapping, cancellation, timeout, response limits, endpoint validation, and offline fallback.
- Backend authentication/authorization, rate-limit, validation, timeout, secret isolation, and redaction tests.
- APK/static scans proving no provider key is embedded.
- Android and backend CI suites.

### Risks and dependencies
- Live use requires an authenticated backend and provisioned secret store; source and mock tests can be completed without provisioning.
- Local inference depends on model/runtime compatibility and device memory, thermal, and battery budgets.

## P3 — Hybrid voice

### Scope
- Android speech recognition with capability detection.
- Android TextToSpeech as the offline speech-output baseline.
- Optional hosted neural speech through a backend proxy; credentials remain server-side.
- Explicit microphone permission, recording indicator, cancellation, and fallback.
- Locale-aware Tajik/Russian/English selection with truthful unsupported-locale behavior.

### Acceptance criteria
- Recording starts only after user action and permission.
- Recording/playback states are visible and cancellable.
- Hosted-service failure falls back to Android TTS or a clear unsupported-state message.
- Audio is not retained/uploaded beyond the documented request lifecycle.
- Tajik recognition/synthesis support is reported from actual device capability, not assumed.

### Tests
- Voice state-machine tests: permission denial, interruption, timeout, cancellation, and fallback.
- Instrumentation with fake speech/TTS engines and permission grant/deny.
- Mock backend tests for audio bounds and secret isolation.
- Manual device checks for microphone routing, Bluetooth, background interruption, and locales.

### Risks and dependencies
- Speech support varies by Android image, installed services, and network.
- Hosted voice needs an authorized account and backend secret; offline behavior must remain useful.

## P4 — Local persistent memory and optional cloud sync

### Scope
- Room/SQLite storage with migrations, conversation history, and preferences.
- User-visible history search/list/detail and deletion.
- Data minimization, encryption at rest where supported, export, and delete-all controls.
- Optional authenticated cloud sync with conflict-aware reconciliation; local-only remains complete.

### Acceptance criteria
- Conversations survive process death and device restart.
- Export uses a documented portable format and only selected data.
- Delete removes local data and queues authenticated cloud deletion when sync is enabled.
- Cloud sync is opt-in; sync failure never destroys local data.
- Conflict behavior is deterministic; sensitive content is absent from logs.

### Tests
- DAO/repository, migration, transaction, restart-persistence, export/import, delete, and encryption tests.
- Sync authorization, conflict, retry, and isolation tests proving one user cannot access another user's memory.

### Risks and dependencies
- Cloud sync requires authenticated backend storage and explicit retention/privacy decisions.
- Encryption and backup semantics require Android-version validation.

## P5 — Narrow, permissioned Android capabilities

### Scope
- Typed registry for a small, documented set of user-requested actions.
- Prefer documented Android APIs and system intents; no arbitrary intent or shell execution.
- Explicit permission rationale and confirmation immediately before sensitive actions.
- Availability checks, denial/revocation handling, and clear unsupported-state UX.

### Acceptance criteria
- Every capability has a narrow schema, allowlist, permission definition, and documented failure modes.
- Model output cannot invoke arbitrary shell, SQL, intents, or unrestricted UI automation.
- Sensitive/irreversible actions require immediate confirmation.
- No root and no unrestricted autonomous AccessibilityService control.
- Revocation and denial stop execution safely.

### Tests
- Schema/allowlist/policy, permission denial/revocation, and confirmation-gate tests.
- Instrumentation for supported intents and permission UX.
- Adversarial prompt-injection, malformed-input, unauthorized-call, and confirmation-bypass tests.
- Manual device evidence for every claimed capability.

### Risks and dependencies
- Android APIs and AppFunctions vary by OS/device.
- Some features require physical-device validation and platform-policy review.

## P6 — Declarative plugin and skill registry

### Scope
- Declarative, versioned manifests with typed input/output schemas.
- Capability allowlists, explicit grants, quotas, timeouts, and revocation.
- Sandboxed execution boundary; arbitrary third-party code is never loaded into the Android app process.
- Provenance/trust metadata and compatibility checks.

### Acceptance criteria
- Plugins declare capabilities and schemas before activation.
- Invalid provenance, unknown capabilities, schema violations, and revoked grants fail closed.
- Plugin output is untrusted and cannot expand permissions.
- Resource limits and cancellation are enforced outside plugin/model context.
- Users can inspect and revoke every grant.

### Tests
- Manifest/parser/property, schema, versioning, provenance, revocation, quota, timeout, and cancellation tests.
- Adversarial tests for prompt injection, capability escalation, oversized output, path traversal, and malformed payloads.
- Isolation tests proving plugins cannot access app secrets or unrestricted Android APIs.

### Risks and dependencies
- A real sandbox requires a constrained execution technology; in-process arbitrary scripting is not an acceptable substitute.
- Plugin distribution introduces supply-chain and update-signing risk.

## P7 — Bounded multi-step task orchestration

### Scope
- Explicit task state machine with bounded plans, typed steps, deadlines, retry budgets, and idempotency keys.
- User-visible plan, progress, result, cancellation, and audit history.
- Approval gates before sensitive or irreversible actions.
- Recovery after process death only where Android scheduling permits.
- Tools execute only through the P5/P6 capability boundary.

### Acceptance criteria
- Every plan has maximum steps, wall-clock deadline, retry budget, and per-step capability checks.
- Users can inspect and cancel before/during execution.
- Approval is bound to exact action parameters and expires if they change.
- Retries cannot duplicate non-idempotent effects.
- Failed/cancelled/interrupted tasks end in accurate states with audit records.
- Model output cannot bypass policy or directly execute tools.

### Tests
- State-machine/property, deterministic planner, cancellation, process-death, idempotency/replay, approval-binding, retry/deadline, and audit tests.
- Integration tests with fake tools and adversarial model output.

### Risks and dependencies
- Background execution is limited by Android background and foreground-service policy.
- High-impact actions require immediate confirmation and server-side idempotency where relevant.

## P8 — Hardening and release readiness

### Scope
- End-to-end security/dependency review and offline/reliability testing.
- Startup, memory, battery, thermal, and performance profiling.
- Complete trilingual localization, accessibility semantics, large-font/layout, and contrast validation.
- Release build/signing workflow, privacy disclosures, data-safety documentation, and release notes.
- Physical Samsung Galaxy A35 5G validation and supported-version regression matrix.

### Acceptance criteria
- P1–P7 have accepted evidence; no unresolved critical/high security issue remains.
- Release build is reproducible and signed with an owner-controlled private key; signing material is never exposed.
- APK, logs, and build artifacts contain no secrets or sensitive user content.
- Offline mode, provider outage, permission denial, cancellation, export/delete, and recovery are validated.
- Accessibility/localization checks pass across supported sizes and font scales.
- Physical-device evidence records exact model, Android build, test date, and results; emulator tests are not substituted.
- Artifact checksum, version, changelog, and rollback procedure are recorded.

### Tests
- Full unit, instrumentation, backend, security, dependency, and static-analysis suites.
- Performance benchmarks and soak tests with explicit thresholds.
- Manual accessibility/localization review, physical-device regression, and release-signature verification.

### Risks and dependencies
- Physical Galaxy A35 access and an owner-controlled release signing key are external gates; neither may be fabricated or claimed.
- Store policy, privacy disclosures, and third-party service terms require owner review before public release.

## Phase acceptance record

Every `docs/P{N}_ACCEPTANCE.md` must record:
1. Exact implemented and excluded scope.
2. Exact final commit SHA and PR URL.
3. CI run URL(s), conclusion, and inspected job/step results on that exact SHA.
4. Unit, instrumentation, backend, and security test evidence/counts.
5. Artifact name, ID, checksum, and expiration when available.
6. Known limitations, external dependencies, and outstanding manual/device checks.
7. A statement that the phase does not accept any later phase.
