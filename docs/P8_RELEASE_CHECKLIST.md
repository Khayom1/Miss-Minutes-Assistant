# P8 Release Checklist — Signed Offline-First APK

## Automated checks required on the exact release commit

- [ ] JVM unit tests pass.
- [ ] Android lint passes.
- [ ] Debug APK builds.
- [ ] Release APK builds.
- [ ] API 37 (Android 17) emulator instrumentation tests pass.
- [ ] API 35 compatibility instrumentation tests pass.
- [ ] Dependency vulnerability/security checks and secret scanning pass.
- [ ] Installable APK and release evidence are uploaded.
- [ ] Package name, versionCode, versionName, minSdk, targetSdk, and signing certificate are verified.
- [ ] SHA-256 is calculated from the exact downloaded APK.
- [ ] Release notes identify the commit and CI run.

## Product acceptance

- [ ] Launches directly into the local guest workspace without registration or network.
- [ ] Chat history and user memory persist encrypted.
- [ ] DeepSeek BYOK works only after the user enters their own key.
- [ ] Local inference reports unavailable unless a supported model runtime is installed.
- [ ] Android TTS and on-device recognition report real installed-language capability.
- [ ] Connected plugin tools are auto-discovered; runtime access checks and confirmation gates pass.
- [ ] Fast, Standard, Max, Extra, and Auto profiles have distinct tested runtime behavior.
- [ ] Backup/restore excludes API keys, plugin grants, and Android permission state.

## Owner/device-only gates

- [ ] Owner-controlled production keystore is configured outside source control.
- [ ] Signing certificate fingerprint and repeatable signing procedure are verified.
- [ ] Real Samsung Galaxy A35 / Android 17 installation and permission UX are tested; API 37 emulator evidence is not physical-device evidence.
- [ ] Background/Force Stop limits, TTS language packs, microphone behavior, overlay, battery and thermal behavior are tested.
- [ ] A real DeepSeek BYOK request is tested only with a user-supplied key.

No production-signed APK is claimed until the signing key is owner-controlled and all applicable gates pass. Emulator CI is not physical-device acceptance.
