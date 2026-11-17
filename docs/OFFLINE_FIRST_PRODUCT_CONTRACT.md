# Offline-First Product Contract

## Non-negotiable product shape

Miss Minutes is a standalone Android assistant. It launches into a local guest workspace and works without registration, Supabase, a remote backend, a hosted voice provider, or a 3D avatar.

## Network policy

The only optional network path is AI inference explicitly configured by the user with their own provider key. The current remote adapter calls DeepSeek directly over HTTPS. No shared API key or backend proxy is allowed. All other core features must remain local.

## Local AI and voice

- A local model may be selected only when a supported runtime and model are installed.
- No model is fabricated or silently downloaded. An absent runtime is reported as unavailable.
- Speech output uses Android TTS and installed offline voices only.
- Speech recognition uses an on-device recognizer when supported. No cloud speech fallback is permitted.
- Language support is capability-detected; Tajik/Russian/English are not claimed as verified until tested on the target device.

## Memory and data

- Conversations and user-authored memory are stored locally with encryption.
- Memory sharing with an external model is off by default and requires an explicit user setting.
- No cloud sync or account-scoped server storage exists.
- API keys, plugin connection grants, OS permission state, and private credentials are excluded from backups and logs.

## Plugins and agents

- Plugins are local declarative manifests plus compiled allowlisted handlers; metadata cannot contain executable code.
- The user connects a plugin once. Connected tools are discovered automatically for each task; there is no per-turn plugin activation.
- OS permission and credential checks occur at execution time.
- The model receives JSON schemas for connected tools only.
- Tool execution is bounded, typed, audited, and fail-closed. Consequential writes require explicit user confirmation.
- GitHub repository read/write is not supported until a real authorized GitHub handler exists and passes tests.

## Android platform limits

Background work must use WorkManager or a visible foreground service as appropriate. WorkManager is deferrable; Doze, process death, and Force Stop can stop or delay work. No indefinite always-on or post-Force-Stop execution is promised. The overlay is only a user-controlled launcher.

## Release gates

Exact-head CI, JVM tests, lint, debug/release builds, instrumentation, dependency/security checks, package/version/signing verification, SHA-256, and a signed installable Release APK are required. Physical-device testing on Samsung Galaxy A35 / Android 17 remains separate from emulator evidence. Production signing requires an owner-controlled keystore.
