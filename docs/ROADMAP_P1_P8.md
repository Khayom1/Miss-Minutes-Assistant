# Roadmap P1–P8 — Offline-First Release

## Product direction

Standalone Android assistant; local guest workspace; no mandatory account, backend, hosted voice, cloud memory sync, or 3D avatar. Optional network inference is direct user-owned BYOK only.

| Phase | Current status | Remaining acceptance |
|---|---|---|
| P0 | Foundation and security baseline established | Continue exact-head security and release regression checks |
| P1 | Compose chat/settings baseline implemented | Final UX and localization review |
| P2 | Direct DeepSeek BYOK adapter and encrypted key storage implemented | Live request with a user-supplied key; distinct runtime profiles; local inference backend |
| P3 | Android TTS, on-device speech input, and opt-in wake-listener foundation implemented | Wake phrase plus command capture; handoff to shared AI/agent runtime; screen-locked operation while OS permits; physical-device language, battery and thermal acceptance |
| P4 | Local encrypted conversations and user-controlled memory implemented | Final backup/restore and physical persistence verification |
| P5 | Permissioned compiled Android device handlers implemented | Final Android 17 permission UX and device verification |
| P6 | Local plugin registry, manifest validation, consent and revocation implemented | User-friendly plugin import, automatic connected-tool discovery/schema injection, end-to-end dispatch, missing-access UX |
| P7 | Bounded local task runtime and WorkManager foundations implemented | Voice and text share one agent execution path; multi-step planning, recovery/duplicate-execution tests, approval UX before write handlers |
| P8 | User-controlled floating launcher and release hardening in progress | Always-available voice session integration; exact-head CI, dependency scan, owner-controlled signing, signed APK, checksum and physical-device acceptance |

## Always-available assistant requirement

The product must support a user-started, opt-in voice session. After saying "Miss Minutes" followed by a natural command, the user should not need to open the app or manually select a plugin. The agent must discover connected tools, plan bounded steps, check access, ask for consequential-action approval, execute, and report the result. When the listener is already active, command capture/processing should work with the screen locked where Android permits it. See `docs/ALWAYS_AVAILABLE_VOICE_AGENT.md`.

This is a required product target, not a claim that the current main branch already implements it. Force Stop, denied permissions, microphone privacy controls, OEM battery policies, and Android foreground-service restrictions must be respected.

## Non-negotiable runtime rules

- The app opens into a local guest workspace without registration.
- No account session, backend function, hosted TTS, cloud sync, or 3D avatar is part of the product.
- A user-owned provider key is optional and stays encrypted on the device.
- Local inference is reported unavailable until a supported runtime/model is installed.
- Android permissions are checked at execution time and remain under OS/user control.
- Plugins are declarative metadata plus compiled allowlisted handlers; no downloaded code is executed.
- Connected plugins are discovered automatically per request; manual per-request plugin selection is not the target UX.
- WorkManager is deferrable and Force Stop is respected.
- README remains unchanged until the product architecture is stable.
