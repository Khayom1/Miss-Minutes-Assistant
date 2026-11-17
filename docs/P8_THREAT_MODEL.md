# P8 Threat Model — Offline-First Android

## Data and control inventory

| Asset | Location | Required protection |
|---|---|---|
| User DeepSeek API key | App-private encrypted storage with Android Keystore-backed key | Direct HTTPS only; no logs/backups; user can revoke |
| Conversations and memory | Local encrypted database, guest workspace scope | Encryption at rest, user controls, export/delete |
| Plugin connection grants | App-private local preferences/store | Persist revocation; exclude from backup |
| Android OS permissions | Android system permission manager | Runtime checks; user-controlled revocation |
| Plugin manifests | Local app-private metadata store | Schema validation; no executable payload |
| Plugin audit | Bounded local app-private log | Metadata only; no arguments or credentials |
| Speech | Android on-device TTS/recognizer | User-started microphone, no cloud fallback |

There are no Supabase sessions, publishable keys, Edge Functions, hosted ElevenLabs credentials, or cloud memory records in the product.

## Security properties

- Network inference occurs only when the user configures a personal provider key. The key is sent only to the official DeepSeek HTTPS endpoint.
- Plaintext BYOK exists briefly in process memory during request construction. A rooted or compromised device may capture it; users must be able to revoke the key.
- No API key, plugin consent, Android permission state, or private credential is exported in backup archives.
- Tool calls are untrusted model output. The connected catalog, strict JSON schema, typed dispatcher, runtime access checker, bounded loop, and compiled handlers form the enforcement boundary.
- A model response cannot grant itself consent or Android permissions.
- Consequential writes fail closed unless the user explicitly approves.
- No arbitrary DEX, scripts, shell, reflection, dynamic code loading, or unrestricted intent execution is allowed.
- Local TTS and on-device recognition do not transmit speech to a hosted voice provider.
- Background work is limited by Android scheduling, Doze, process death, foreground-service policy, and Force Stop.

## Remaining risks and evidence

- Physical-device permission, background, microphone, TTS, overlay, battery and thermal behavior remain unverified until tested on Samsung Galaxy A35 / Android 17.
- Live DeepSeek BYOK requires a user-supplied key and is not covered by ordinary CI.
- Production signing requires an owner-controlled key. Debug or unsigned artifacts are not production releases.
- Independent vulnerability review and exact release artifact verification remain required.