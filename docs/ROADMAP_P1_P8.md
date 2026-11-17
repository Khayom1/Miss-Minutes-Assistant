# Roadmap P1–P8 — Offline-First Release

## Product direction

Standalone Android assistant; local guest workspace; no mandatory account, backend, hosted voice, cloud memory sync, or 3D avatar. Optional network inference is direct user-owned BYOK only.

| Phase | Current status | Remaining acceptance |
|---|---|---|
| P0 | Foundation and security baseline established | Continue exact-head security and release regression checks |
| P1 | Compose chat/settings baseline implemented | Final UX and localization review |
| P2 | Direct DeepSeek BYOK adapter and encrypted key storage implemented | Live request with a user-supplied key; distinct runtime profiles; local inference backend |
| P3 | Android TTS and on-device speech path implemented | Physical-device language-pack, wake, microphone, battery and thermal acceptance |
| P4 | Local encrypted conversations and user-controlled memory implemented | Final backup/restore and physical persistence verification |
| P5 | Permissioned compiled Android device handlers implemented | Final Android 17 permission UX and device verification |
| P6 | Local plugin registry, manifest validation, consent and revocation implemented | User-friendly plugin import, model schema injection, end-to-end dispatch, missing-access UX |
| P7 | Bounded local task runtime and WorkManager foundations implemented | End-to-end planning integration, recovery/duplicate-execution tests, approval UX before write handlers |
| P8 | User-controlled floating launcher and release hardening in progress | Exact-head CI, dependency scan, owner-controlled signing, signed APK, checksum and physical-device acceptance |

## Non-negotiable runtime rules

- The app opens into a local guest workspace without registration.
- No account session, backend function, hosted TTS, cloud sync, or 3D avatar is part of the product.
- A user-owned provider key is optional and stays encrypted on the device.
- Local inference is reported unavailable until a supported runtime/model is installed.
- Android permissions are checked at execution time and remain under OS/user control.
- Plugins are declarative metadata plus compiled allowlisted handlers; no downloaded code is executed.
- WorkManager is deferrable and Force Stop is respected.
- README remains unchanged until the product architecture is stable.
