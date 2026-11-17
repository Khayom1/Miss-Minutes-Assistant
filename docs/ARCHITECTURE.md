# Architecture

Miss Minutes is a **standalone, offline-first Android assistant**. It is a software assistant with a lightweight Compose interface and a user-controlled floating launcher. It is not a 3D character, does not require an account, and has no mandatory backend.

## Product constraints

- No Supabase, hosted application backend, mandatory registration, 3D avatar, hosted voice provider, or remote memory synchronization.
- The only optional network inference path is a provider explicitly configured by the user with their own API key.
- The current remote provider is direct DeepSeek BYOK over HTTPS. The key is encrypted at rest using Android Keystore-backed storage and is never committed or logged.
- A local inference adapter may be selected only when a compatible runtime and model are actually installed. Otherwise local inference reports unavailable; it must not simulate a local response.
- Speech output uses Android TextToSpeech and installed local voices. Network-required voices are not eligible for the offline path. Speech recognition must use an on-device recognizer when available and report unavailable otherwise.
- Conversations and user-authored memory are stored locally with encryption. No cloud sync is part of the product.
- Plugin manifests are declarative metadata only. They cannot contain scripts, DEX, reflection instructions, arbitrary URLs, shell commands, or executable code.

## Runtime architecture

1. **Compose UI:** chat, model/provider settings, profile selection, memory controls, plugin connection state, backup/restore, and accessibility settings.
2. **AI provider layer:** direct user-configured provider adapters and an optional local inference adapter. Provider credentials are never shared developer secrets.
3. **Local persistence:** encrypted conversation and memory storage, local preferences, bounded audit records, and app-private plugin metadata.
4. **Plugin runtime:** one-time local connection grants; automatic discovery of connected tools; tool schemas supplied to the model; strict typed argument parsing; execution-time permission/credential checks; confirmation for consequential writes; audit events.
5. **Android device adapters:** compiled allowlisted handlers only. OS permissions and app-specific grants are checked at execution time. Unknown actions fail closed.
6. **Agent runtime:** bounded task steps, durable progress and recovery where Android permits. WorkManager is deferrable; foreground services require visible notifications and user control.

## Security boundaries

- Network access is disabled for assistant features unless the user configures an AI provider.
- BYOK credentials, conversation contents, plugin grants, and Android permission state are excluded from backups and diagnostic logs.
- Connected plugins do not gain permanent Android permissions. OS grants and credentials are checked each time a tool runs.
- No arbitrary plugin code is loaded or executed.
- Consequential writes require a separate explicit approval. The currently shipped device status handlers are read-only; no GitHub repository connector is implemented.

## Current release gaps

Fast, Standard, Max, Extra, and Auto profiles still require distinct runtime behavior and tests. A supported local inference backend is not bundled. Permission-request UX for future non-device plugins, real-device acceptance, owner-controlled release signing, and a signed Release APK remain release gates.