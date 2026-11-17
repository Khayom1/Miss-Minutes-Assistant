# P8 — Threat Model and Security Review

Status: initial source/configuration review recorded; automated release-candidate gates are being added. This document is not a penetration-test report and does not claim physical-device validation.

## Assets and trust boundaries

| Asset | Storage / boundary | Required protection |
|---|---|---|
| User DeepSeek API key | Android Keystore AES-256-GCM + app-private preferences | User-owned BYOK; never embed a shared provider key |
| Supabase session | Android Keystore encrypted local storage | TLS, expiry/refresh handling, no token logging |
| Conversations and saved memory | SQLite ciphertext, owner-scoped rows | Keystore encryption, authenticated owner scoping, explicit export/delete |
| ElevenLabs credential | Supabase Edge Function secret | Server-side only; never delivered to Android |
| Supabase publishable key | Android BuildConfig | Public client identifier only; never treat as authorization |
| Device capabilities | Typed allowlist + per-capability grants | Default deny, per-action confirmation, audit-before-execute |
| Agent task state | Keystore-encrypted SharedPreferences | Bounded graph, explicit approvals, interrupted work recovery |
| Audio response | Bounded temporary cache file | HTTPS, authenticated request, size cap, delete on stop/completion |

## Reviewed controls

- Android manifest disables backup and cleartext traffic; the microphone foreground service is non-exported and declares its microphone service type.
- Direct DeepSeek BYOK is a deliberate exception to server-side provider-key storage: the key belongs to the user, is encrypted at rest, and is sent only to the official HTTPS endpoint. Plaintext exists briefly in app memory during a request; a compromised/rooted device can still capture it.
- Supabase publishable credentials in BuildConfig are public by design. No service-role or Supabase secret key may be placed in the APK.
- The hosted AI/TTS functions require a bearer session, verify the current Auth user, validate bounded input, apply per-user database rate limits, and keep provider secrets server-side.
- Conversation content is encrypted before SQLite insertion; owner ID is authenticated additional data. Database backup is disabled. Secure-delete is a best-effort storage control, not a forensic erasure guarantee.
- Device and plugin execution are closed-world, typed and consent-gated. Audit failure blocks device actions. No shell, arbitrary intent, reflection or unrestricted AccessibilityService execution is present.
- Agent work is bounded and persisted. An interrupted RUNNING node becomes RECOVERY_REQUIRED rather than being replayed automatically. WorkManager is deferrable and does not promise always-on execution.
- Hosted TTS downloads enforce an audio byte ceiling and use app-private cache files. Audio is not a durable conversation record.

## Residual risks and release blockers

| Risk | Severity | Status / disposition |
|---|---|---|
| Rooted device, runtime instrumentation or malware can capture a user-entered BYOK key while in memory | High | Inherent BYOK limitation; disclose and support key revocation |
| Wildcard CORS on native Edge Function endpoints | Low for native client; browser exposure remains broad | Bearer authentication and user verification are mandatory; revisit if browser clients are added |
| Android OS may defer/stop background microphone or WorkManager execution | High for always-on expectations | Device-specific validation required; no force-stop bypass |
| Fixed-window per-user quotas can burst at minute boundaries and do not provide global IP abuse protection | Medium | Current abuse control; add gateway-level/global protection before public scale |
| Gradle dependency graph has no committed lockfile or dependency-verification metadata | Medium | CI now records the resolved runtime graph; lock/signature verification remains a release hardening follow-up |
| Debug and unsigned release artifacts are not production-signed | High for distribution | Release signing requires an owner-controlled signing key and secure release process |
| Emulator API 35 does not prove Samsung Android 17 microphone, battery or thermal behavior | High | Explicitly deferred to physical-device acceptance |
| Live DeepSeek BYOK and hosted ElevenLabs provider configuration are not verified by CI | Medium | Require real credential-backed smoke tests before claiming provider acceptance |

## Dependency inventory reviewed

Direct Android dependencies are version-pinned in app/build.gradle.kts and the root plugin declarations: AndroidX Compose BOM, Activity Compose, Core KTX, Lifecycle Runtime, WorkManager, Kotlin serialization/coroutines, OkHttp, Android Gradle Plugin and Kotlin 2.2.21. The exact resolved runtime graph is exported by CI for each P8 candidate. A successful build is not equivalent to a CVE scan; dependency vulnerability review must be performed against the uploaded resolved graph before a public production release.

## Security acceptance

- Gitleaks passes on the exact candidate commit.
- JVM tests, Android API 35 instrumentation, debug/release assembly and lint pass.
- Release candidate checksums match the uploaded APK bytes.
- Resolved dependency report is retained with the candidate.
- No privileged key or private user content appears in source, APK constants, logs or artifacts.
- Physical-device, live-provider, signing-key and independent vulnerability-scan gates remain explicitly open until evidence exists.
