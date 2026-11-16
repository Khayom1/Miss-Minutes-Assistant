# Miss Minutes Assistant

Android companion app inspired by Miss Minutes from Marvel's TVA. The project includes a selectable Tajik, Russian, and English interface, configurable AI connections, local device features, speech input, and an optional floating character.

## Build and install a test APK

The current CI produces a **debug APK**, not a production-signed release. Open the latest successful [Android CI run for PR #3](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/workflows/android.yml), select **Artifacts**, and download `Miss-Minutes-Assistant-debug`. Install `app-debug.apk` on Android. If Android refuses an update because the signing certificate differs from an older local build, uninstall the older app first; uninstalling removes local app data and saved keys.

The build is version **1.2.0 (versionCode 3)**. Changes are in the open PR branch and do not become the `main`/default version until that PR is reviewed and merged.

## Languages and AI providers

Choose Тоҷикӣ, Русский, or English in Settings. A user can configure a provider name, HTTPS endpoint, model identifier, and their own API key. Gemini and Anthropic use their native protocols at their official HTTPS hosts; other compatible endpoints must implement the OpenAI Chat Completions protocol. Keys are encrypted with Android Keystore and are not included in the repository or APK. Never paste an API key into an issue or chat.

The app shell, local commands and optional 3D character work without an AI key. Remote model use depends on the selected provider's availability, account and pricing. A provider accepting an API key does not imply free service. Android TTS is available as a local fallback; ElevenLabs is optional and may be billed by that service.

## Floating character and performance

The overlay now starts as a small 2D bubble. It does not instantiate the GLB renderer until the user explicitly expands it. This avoids starting a second continuously rendered 3D scene by default. Expand the bubble for the 3D character; collapse it again to release the overlay scene. The full in-app character view is still rendered while the app is open.

Actual FPS, memory, battery and thermal behavior must still be measured on target phones. CI/emulator success is not a physical-device performance certification.

## Important model and release status

The included character is an **unofficial stylized fan recreation**, not an authentic Marvel/film asset. Its GLB has authored transform animations, but does not have weighted skinning or facial morph targets. The project does not currently contain a verified, licensed film-accurate source model. Code changes alone cannot create a faithful production model; that requires an appropriately licensed 3D asset and specialist modeling/rigging work.

This repository is suitable for a development/demo build after CI passes. It is **not yet a finished public production release**: the model is not film-authentic, physical-device testing and release signing have not been completed, and use of Marvel names/artwork may require permission. Do not market the app as official or as using an authentic film model.

## Permissions and privacy

Microphone, overlay, notification-listener, and accessibility capabilities require Android permissions/settings. Grant only the permissions you intend to use. AI requests go to the endpoint configured by the user. Review the provider's privacy and billing terms before entering a key or sending personal data.
