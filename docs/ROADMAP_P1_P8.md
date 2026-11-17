# Roadmap P1–P8 — Offline-First Release

## Product direction

Standalone Android assistant; local guest workspace; no mandatory account, Supabase, hosted backend, hosted voice, cloud memory sync, or 3D avatar. Optional network inference is direct user-owned BYOK only.

| Phase | Current status | Remaining acceptance |
|---|---|---|
| P0 | Foundation established | Continue security and release regression checks |
| P1 | Compose chat/settings baseline | Final UX and localization review |
| P2 | Direct DeepSeek BYOK adapter and encrypted key storage implemented | Live request with a user-supplied key; Fast/Standard/Max/Extra/Auto profiles still need distinct runtime behavior |
| P3 | Android TTS and on-device speech path implemented | Physical-device language-pack, wake, microphone, battery and thermal acceptance |
| P4 | Local encrypted conversations and user-controlled memory implemented | Final backup/restore and physical persistence verification |
| P5 | Permissioned compiled Android device handlers implemented | Final Android 17 permission UX and device verification |
| P6 | Local plugin lifecycle, connected catalog, access checks, schemas and tool dispatch implemented on the current PR | Exact-head CI; missing-permission UX; no GitHub connector exists |
| P7 | Bounded tool execution and task runtime foundations implemented | Recovery/duplicate-execution tests and explicit approval UX before any write handler |
| P8 | User-controlled floating launcher and release hardening in progress | Exact-head CI, owner-controlled signing, signed APK artifact, checksum, physical-device acceptance |

## Non-negotiable runtime rules

- The app opens into a local guest workspace without registration.
- No Supabase project, account session, Edge Function, hosted TTS, 3D avatar, or cloud memory service is required or included.
- The DeepSeek API key belongs to the user, is encrypted locally, and is sent only to DeepSeek over HTTPS.
- If no local inference runtime/model is installed, local inference reports unavailable.
- Android TTS selects installed offline voices only; unsupported languages are reported honestly.
- Plugin connection is one-time and local. Connected tools are auto-discovered; no per-turn activation.
- Every tool call is checked against the connected catalog and compiled handler, with strict arguments, runtime access checks, bounded execution, audit, and confirmation for consequential writes.
- No GitHub repository changes are claimed without a real authorized GitHub plugin implementation.

## Release gates

- Exact-head JVM tests, lint, debug/release builds, instrumentation, dependency/security checks, secret scanning and artifact evidence.
- Signed installable Release APK using an owner-controlled production keystore.
- Package/version/signing certificate/SHA-256 verification.
- Real Samsung Galaxy A35 / Android 17 installation and permission/lifecycle tests.
- Live DeepSeek smoke test only after the user supplies a valid personal key.
