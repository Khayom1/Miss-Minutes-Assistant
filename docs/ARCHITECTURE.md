# Architecture

Miss Minutes is a standalone, offline-first Android assistant with a lightweight Compose interface and a user-controlled floating launcher. It is not a 3D character and does not require an account, backend, cloud sync, or hosted voice provider.

## Product constraints

- The app opens directly into a local guest workspace.
- Optional network inference is direct DeepSeek BYOK over HTTPS. No shared provider key or application proxy is used.
- Local inference is available only when a supported runtime and model are actually installed. The current adapter seam reports unavailable when they are absent.
- Speech output uses Android TextToSpeech with installed offline voices. Speech recognition uses an on-device recognizer when supported; no cloud fallback is permitted.
- Conversations and user-authored memory are encrypted and stored locally. No remote memory synchronization exists.
- Plugins are local declarative manifests paired with compiled allowlisted handlers. No executable plugin payloads are loaded.

## Runtime components

1. **Compose UI:** chat, history, settings, memory controls, language selection, voice controls, backup/restore, and overlay controls.
2. **AI provider layer:** direct user-owned DeepSeek BYOK and an optional local-inference adapter seam.
3. **Local persistence:** encrypted conversations and memory, local preferences, app-private plugin metadata, and bounded audit records.
4. **Android adapters:** typed, allowlisted device actions with runtime permission checks and user-visible controls.
5. **Plugin registry:** local manifest/consent/revocation lifecycle with compiled handler validation. Provider tool-schema injection and end-to-end model dispatch remain a separate implementation gate.
6. **Agent runtime:** bounded task state and WorkManager scheduling within Android lifecycle limits.
7. **Floating launcher:** a user-started overlay shortcut that opens the Activity; it does not listen or execute actions by itself.

## Security boundaries

- The user-owned API key is encrypted with Android Keystore-backed storage and is never logged or included in backups.
- Android app backup is disabled. Explicit encrypted exports also exclude API keys, plugin grants, OS permissions, and credentials.
- Unknown plugin actions, arguments, permissions, or handlers fail closed.
- Consequential writes remain blocked unless a separate explicit confirmation path is implemented.
- No arbitrary Kotlin, DEX, JavaScript, shell, reflection, or unrestricted intent execution is allowed.

## Current release gaps

Distinct Fast/Standard/Max/Extra/Auto runtime profiles, a bundled local inference runtime, end-to-end model tool schemas/dispatch, plugin import and missing-permission UX, real-device acceptance, owner-controlled signing, and a signed Release APK remain open.
