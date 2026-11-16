# Release readiness (version 1.2.0)

## Current state

This update is a development/demo APK candidate on the open feature PR, not a signed public release. The latest CI workflow validates the GLB, runs unit tests, assembles the APK, runs lint, executes API 35 emulator instrumentation, and uploads a debug APK. Download only an artifact from a successful run on the intended PR head commit.

## Delivered in this update

- Tajik, Russian, and English UI/localization and configurable AI-provider settings are included on the feature branch.
- Users enter their own API key. Gemini/Anthropic are routed to their native APIs; other providers need the documented OpenAI-compatible chat-completions protocol. Provider access and usage may cost money.
- Floating character starts as a 72dp 2D bubble; 3D is loaded only after explicit expansion. Collapse it to remove the overlay's 3D scene.
- App version is incremented to 1.2.0 / versionCode 3 so this APK can update a 1.1.0 / versionCode 2 install when Android signing keys match.

## Explicit release blockers

1. **Film-accurate character:** the current repo asset is a stylized fan recreation, with no weighted skinning or facial morphs. No licensed, film-accurate source asset is in the repository. Do not claim this is the original character or an official Marvel application. An accurate asset must be supplied/commissioned with rights, rigged, optimized, and validated in Android.
2. **Physical-device verification:** no target phone FPS, memory, battery, thermal, overlay, voice, or lifecycle report is available. The overlay optimization is code-reviewed but not certified on a real handset.
3. **Public distribution:** CI APK is debug-signed. A production release requires an owner-controlled signing key, final privacy/permissions review, and a release build/test cycle.
4. **No-cost guarantee:** user-supplied AI APIs and ElevenLabs are optional and may charge. Local app features and Android TTS do not make third-party model APIs free.

## Acceptance steps before public launch

- Provide an authorized character asset (or choose an original, non-infringing design) and run the strict model/rig validator on the exact APK asset.
- Run CI on the final PR head and inspect its APK artifact.
- Install/update and profile on supported physical devices; record FPS, memory, thermal and battery results with the overlay compact and expanded.
- Generate a signed release with a securely stored owner key. Do not commit the signing key or API credentials.
- Review provider terms, privacy disclosure, permissions, branding, and applicable distribution requirements.
