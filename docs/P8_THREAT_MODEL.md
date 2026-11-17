# P8 Threat Model — Offline-First Android

## Assets and trust boundaries

| Asset | Location | Required protection |
|---|---|---|
| User DeepSeek API key | App-private encrypted storage with Android Keystore-backed key | Direct HTTPS only; no logs/backups; user can revoke |
| Conversations and memory | Local encrypted database, guest workspace scope | Encryption at rest, user controls, export/delete |
| Plugin connection grants | App-private local preferences/store | Persist revocation; exclude from backup |
| Android OS permissions | Android system permission manager | Runtime checks; user-controlled revocation |
| Plugin manifests | Local app-private metadata store | Schema validation; no executable payload |
| Plugin audit | Bounded local app-private log | Metadata only; no arguments or credentials |
| Speech | Android on-device TTS/recognizer | User-started microphone, no cloud fallback |

There are no account sessions, provider proxy, backend functions, hosted voice credentials, or cloud memory records in the product.

## Security properties

- Network inference occurs only when the user configures a personal provider key. The key is sent only to the official DeepSeek HTTPS endpoint.
- Plaintext BYOK exists briefly in process memory during request construction. A rooted or compromised device may capture it; users must be able to revoke the key.
- No API key, plugin consent, Android permission state, or private credential is exported in backup archives.
- The local plugin registry accepts only declarative manifests whose actions map to compiled allowlisted handlers.
- A plugin manifest cannot grant itself consent or Android permissions.
- Consequential writes remain blocked until a separate explicit confirmation flow is implemented.
- No arbitrary code loading, reflection, shell execution, or unrestricted Android intents are allowed.
- WorkManager is deferrable; Force Stop prevents scheduled work until the user opens the app again.

## Release blockers

- Distinct Fast/Standard/Max/Extra/Auto runtime profiles.
- A supported bundled local inference runtime/model.
- End-to-end model tool-schema injection/dispatch and missing-permission/credential UX.
- Dependency vulnerability scanning and dependency verification.
- Owner-controlled production signing and certificate verification.
- Physical Samsung Galaxy A35 / Android 17 acceptance.

This document is a source/configuration threat model, not a penetration-test report.
