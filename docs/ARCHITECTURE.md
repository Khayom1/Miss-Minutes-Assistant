# Architecture

Miss Minutes is a standalone, offline-first Android assistant with a lightweight Compose interface and a user-controlled floating launcher. It is not a 3D character and does not require an account, backend, cloud sync, or hosted voice provider.

## Product constraints

- The app opens directly into a local guest workspace.
- Optional network inference is direct DeepSeek BYOK over HTTPS. No shared provider key or application proxy is used.
- Local inference is available only when a supported runtime and model are actually installed. The current adapter seam reports unavailable when they are absent.
- Speech output uses Android TextToSpeech and installed offline voices. Speech recognition uses an on-device recognizer when supported; no cloud fallback is permitted.
- Conversations and user-authored memory are encrypted and stored locally. No remote memory synchronization exists.
- Plugins are local declarative manifests paired with compiled allowlisted handlers. No executable plugin payloads are loaded.
- The product target includes an opt-in, user-started always-available voice session: wake phrase, command capture, AI planning, automatic connected-tool discovery, bounded execution, and spoken result. This target is specified in `docs/ALWAYS_AVAILABLE_VOICE_AGENT.md`; it is not yet accepted as implemented.

## Runtime components

1. **Compose UI:** chat, history, settings, memory controls, language selection, voice controls, backup/restore, and overlay controls.
2. **AI provider layer:** direct user-owned DeepSeek BYOK and an optional local-inference adapter seam.
3. **Local persistence:** encrypted conversations and memory, local preferences, app-private plugin metadata, and bounded audit records.
4. **Android adapters:** typed, allowlisted device actions with runtime permission checks and user-visible controls.
5. **Plugin registry and tool dispatch:** local manifest/consent/revocation lifecycle, compiled handler validation, connected-tool schema discovery, and bounded dispatch are present in the mainline path. The Plugin Builder and complete voice-to-tool path remain incomplete.
6. **Agent runtime:** bounded task state and WorkManager scheduling within Android lifecycle limits. Voice and typed chat must share the same execution path.
7. **Voice session runtime (required, incomplete):** the current wake listener accepts an exact wake phrase and then stops with a notification; it does not yet capture the following command and dispatch it to the agent. The target runtime must add a lifecycle-aware foreground session, secure command handoff, cancellation/approval, and recoverable service state.
8. **Floating launcher:** a user-started overlay shortcut. It must not silently bypass microphone permissions or Android background-start restrictions.

## Security boundaries

- The user-owned API key is encrypted with Android Keystore-backed storage and is never logged or included in backups.
- Android app backup is disabled. Explicit encrypted exports also exclude API keys, plugin grants, OS permissions, and credentials.
- Unknown plugin actions, arguments, permissions, or handlers fail closed.
- Consequential writes require explicit, just-in-time user confirmation. Lock-screen presence is not confirmation; protected actions require unlock where appropriate.
- No arbitrary Kotlin, DEX, JavaScript, shell, reflection, or unrestricted intent execution is allowed.
- Voice listening is opt-in, user-started, visible through a persistent notification, stoppable, and subject to OS microphone and foreground-service restrictions. No claim is made that listening survives Force Stop or every OEM battery policy.

## Current release gaps

Distinct Fast/Standard/Max/Extra/Auto runtime profiles, a bundled local inference runtime, Plugin Builder, voice wake-phrase-plus-command capture, voice-to-agent execution, consequential-action approval, physical lock-screen acceptance, owner-controlled signing, and a signed Release APK remain open.
