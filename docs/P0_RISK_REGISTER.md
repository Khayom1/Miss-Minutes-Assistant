# Risk Register — Offline-First Release

| ID | Risk | Severity | Mitigation / evidence required |
|---|---|---:|---|
| R-01 | User BYOK key is exposed on a compromised device | High | Android Keystore-backed encryption, no logs/backups, direct HTTPS only, user revocation |
| R-02 | Android Doze, process death, or Force Stop interrupts background work | High | WorkManager/foreground service within platform limits; visible notification; no always-on promise |
| R-03 | AI provider is unavailable or key is invalid | Medium | Typed errors, cancellable requests, honest offline/local availability |
| R-04 | Plugin or device action exceeds user consent | High | Default deny, compiled allowlist, runtime access checks, explicit write confirmation, audit log |
| R-05 | Local conversation or memory data is lost | Medium | Encrypted persistence, export/restore, retention controls, restore conflict tests |
| R-06 | Installed Android speech language is missing or network-only | Medium | Inspect installed voice properties; reject network-required voices; report unsupported languages |
| R-07 | Model prompt injection attempts tool misuse | High | Connected catalog only, strict JSON schema, typed dispatcher, bounded tool rounds/calls |
| R-08 | Release artifact is unsigned or signed with an uncontrolled key | High | Owner-controlled keystore, certificate fingerprint verification, repeatable signing procedure |
| R-09 | Local model runtime is absent on the target phone | Medium | Do not claim local inference; report unavailable until an installed supported runtime is tested |
| R-10 | CI emulator infrastructure is unstable | Medium | Pin emulator packages, preserve logs, rerun only infrastructure failures; never mark a failed check green |
| R-11 | Language behavior differs on a physical Samsung device | Medium | Physical-device Tajik/Russian/English acceptance; emulator results are not sufficient |
| R-12 | Plugin metadata is malicious or malformed | High | Schema validation, size limits, no executable payloads, no dynamic code loading |