# P8 — Release Hardening Checklist

Status: in progress. This is a gated handoff checklist, not a declaration that the application is production-ready.

## Automated candidate pipeline

- [x] CI checks tracked source for secrets with Gitleaks.
- [x] CI runs Supabase AI and TTS function tests.
- [x] CI runs JVM tests, Android lint and API 35 instrumentation.
- [ ] CI builds both debug and unsigned release APKs on the exact P8 head.
- [x] Align Kotlin Gradle plugins with the AGP 8.13.2 compatibility range (Kotlin 2.4.21); exact build verification is pending CI.
- [ ] CI exports the resolved Android runtime dependency graph.
- [ ] CI generates SHA-256 checksums for both APKs.
- [ ] Uploaded artifact contains both APKs, dependency report and checksum file.
- [ ] Review every step and artifact from the exact final commit; pending/skipped steps are not passes.

## Manual / external release gates

- [ ] Review dependency report against current OSV/GHSA advisories; record date, findings and disposition.
- [ ] Configure owner-controlled production signing outside source control; verify signing certificate fingerprint and repeatable release procedure.
- [ ] Install on Samsung Galaxy A35 5G / Android 17. Test microphone foreground-service lifecycle, lock screen, task removal, notification stop action, battery/thermal behavior and restart recovery.
- [ ] Verify installed offline speech language packs and real Tajik/Russian/English speech behavior.
- [ ] Perform a real DeepSeek request using a user-supplied BYOK key.
- [ ] Verify hosted ElevenLabs TTS using a configured server-side secret; confirm offline fallback and temporary-audio cleanup.
- [ ] Verify data export/delete and retention behavior on the target device.
- [ ] Record P2–P5 deferred acceptance evidence before claiming the overall product is production-ready.

## Artifact policy

- Debug APK is for installation/testing only.
- Unsigned release APK is a build candidate, not a distributable signed release.
- SHA-256 establishes byte identity, not safety or authenticity by itself.
- Never publish signing keys, provider credentials, user tokens, private conversation content or raw diagnostic data.
- Root README and public release claims remain deferred until product and external acceptance gates are satisfied.
