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
| OkHttp 4.12.0 is within the affected version range for CVE-2026-129482, a CertificatePinner trailing-dot hostname bypass | Conditional | Repository code search found no CertificatePinner usage; current fixed HTTPS endpoints do not use trailing-dot hostnames. The vulnerable code path is not configured in this app; re-check before introducing pinning and monitor for a vendor-fixed release |
| Debug and unsigned release artifacts are not production-signed | High for distribution | Release signing requires an owner-controlled signing key and secure release process |
| Emulator API 35 does not prove Samsung Android 17 microphone, battery or thermal behavior | High | Explicitly deferred to physical-device acceptance |
| Live DeepSeek BYOK and hosted ElevenLabs provider configuration are not verified by CI | Medium | Require real credential-backed smoke tests before claiming provider acceptance |

## Dependency inventory reviewed

The initial compatibility review found Kotlin Gradle Plugin 2.2.21 paired with Android Gradle Plugin 8.13.2, outside Kotlin 2.2's fully supported AGP range. The P8 branch upgrades the Kotlin Android, Compose and serialization plugins together to 2.4.21. Kotlin's official compatibility matrix lists 2.4.20–2.4.21 as fully supporting AGP 8.5.2–9.3.1 and Gradle 7.6.3–9.7; this repository uses AGP 8.13.2 and Gradle 8.13. Direct Android dependencies are otherwise version-pinned in app/build.gradle.kts: AndroidX Compose BOM, Activity Compose, Core KTX, Lifecycle Runtime, WorkManager, Kotlin serialization/coroutines and OkHttp. The exact resolved runtime graph is exported by CI for each P8 candidate. Advisory triage on 2026-11-17 identified CVE-2026-129482 for OkHttp through 5.5.1. The issue is specific to CertificatePinner hostname matching with trailing-dot hosts; repository code search found no CertificatePinner usage, and the app uses fixed non-trailing-dot HTTPS endpoints. No patched release was confirmed in the reviewed advisory sources, so do not upgrade blindly to another affected version. Reassess when the vendor publishes a fixed version. A successful build is not equivalent to a complete CVE scan; dependency vulnerability review must be performed against the uploaded resolved graph before a public production release.

## Security acceptance

- Gitleaks passes on the exact candidate commit.
- JVM tests, Android API 35 instrumentation, debug/release assembly and lint pass.
- Release candidate checksums match the uploaded APK bytes.
- Resolved dependency report is retained with the candidate.
- No privileged key or private user content appears in source, APK constants, logs or artifacts.
- Physical-device, live-provider, signing-key and independent vulnerability-scan gates remain explicitly open until evidence exists.
