# Test Strategy — Offline-First Android

## Automated CI gates

- JVM tests for direct provider request/response validation, BYOK storage, local-runtime availability, encrypted memory, plugin lifecycle, Android action validation, and bounded agent execution.
- Android instrumentation for manifest contracts, local persistence, Keystore-backed stores, permission behavior, TTS lifecycle, overlay gates, and supported process/lifecycle behavior.
- Android lint, debug/release builds, dependency inventory, secret scanning, API 35 instrumentation, and APK checksums.
- No Deno or backend Edge Function tests: the product has no backend functions.

## Security tests

- Missing or invalid BYOK keys fail with typed errors and never expose key material.
- Local inference reports unavailable when the runtime is absent; it never fabricates a response.
- Plugin installation accepts only manifests whose actions map to compiled allowlisted handlers.
- Consent revocation persists; unknown actions and missing access fail closed.
- Audit records contain metadata only, never raw arguments or credentials.
- No arbitrary code, reflection, shell, or unapproved Android intent path exists.

## Release acceptance

- Run JVM tests, lint, debug/release builds, dependency/security checks, and API 35 instrumentation on the exact PR head.
- Verify package name, version, APK presence, signing certificate, and SHA-256.
- An emulator pass is not physical-device evidence.
- Physical Samsung Galaxy A35 / Android 17 tests must verify installation, offline launch, guest chat, encrypted history, voice language availability, microphone behavior, overlay permissions, background limits, battery, and thermal behavior.
