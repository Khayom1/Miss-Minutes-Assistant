# Test Strategy — Offline-First Android

## Automated CI gates

- JVM unit tests for provider request/response validation, BYOK storage, local inference availability, memory encryption, plugin lifecycle, tool schemas, argument parsing, confirmation gates, audit behavior, and bounded agent execution.
- Android instrumentation tests for manifest contracts, local persistence, Keystore-backed stores, Android permission checks, TTS lifecycle, overlay permission gates, and process/lifecycle behavior where the emulator supports it.
- Android lint, debug build, release build, dependency resolution/reporting, secret scanning, and artifact checksum generation.
- No Deno or Supabase Edge Function tests: the product has no backend functions.

## Plugin security tests

- Unconnected tools are not supplied to the model.
- Only tool names from the current connected catalog can execute.
- Unknown fields, non-primitive values, wrong types, oversized JSON, and malformed JSON are rejected.
- Runtime permission and credential checks occur immediately before execution.
- Consequential actions fail closed without explicit confirmation.
- Audit entries contain action metadata only, never raw arguments or credentials.
- No arbitrary code, reflection, shell, or unapproved Android intent path exists.

## Release acceptance

- Run JVM tests, lint, debug/release builds, dependency/security checks, and API 35 instrumentation on the exact PR head.
- Verify package name, version, APK existence, signing certificate, and SHA-256.
- A successful emulator run is not a physical-device test.
- Physical Samsung Galaxy A35 / Android 17 tests must verify installation, offline launch, guest chat, encrypted history, TTS language availability, microphone behavior, overlay permission UX, background limits, battery/thermal behavior, and BYOK only after the user supplies a valid key.
- Production signing requires an owner-controlled keystore. Never generate or publish a substitute signing identity and describe it as the owner's production signature.