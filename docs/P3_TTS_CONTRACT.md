# P3 — Hybrid voice implementation contract

## Required on-device speech output

- Android native `TextToSpeech` is a required capability and remains available as the offline speech-output path.
- Select the highest-quality installed voice for the requested language only when it does not require a network connection.
- Never silently select a network-required system voice for the offline path.
- Speak/Stop controls are available for assistant messages.
- Speech is cancelled on user stop/sign-out and the TTS engine is shut down with Activity destruction.
- If no matching offline voice is installed, report the capability as unavailable; do not claim that the device can speak Tajik or another language without evidence.

## Optional hosted neural speech

- Hosted ElevenLabs TTS is an optional enhancement behind the authenticated Supabase `miss-minutes-tts` Edge Function.
- The ElevenLabs credential remains in Supabase server-side secret storage; it is never embedded in the APK, returned to the Android client, or logged.
- The Android client sends only the user's short-lived Supabase access token and the public publishable key.
- The Edge Function validates the Supabase session, bounds text and audio sizes, enforces a per-user rate limit, normalizes provider errors and never returns provider credentials.
- If hosted TTS is unavailable, Android falls back to the installed offline Android TTS voice.
- Network TTS audio is written only to the app cache, bounded to 5 MiB, and deleted after playback, stop or error.
- If the hosted provider secret is not configured, hosted speech reports unavailable and offline TTS remains usable.

## Offline wake phrase

- Exact phrase: “Miss Minutes”. The transcript matcher must reject “Hey Miss Minutes” and any transcript containing extra words.
- Listening is off by default and requires an explicit user action in Settings.
- Show the microphone/audio-processing disclosure before requesting `RECORD_AUDIO` and `POST_NOTIFICATIONS`.
- Start the microphone foreground service only from the visible Activity after permission grants.
- Declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `RECORD_AUDIO`, and `POST_NOTIFICATIONS`; service is non-exported, microphone-typed and `stopWithTask=false`.
- Keep an ongoing foreground notification with an explicit Stop action while listening.
- Use `SpeechRecognizer.createOnDeviceSpeechRecognizer` only. If the OS has no on-device recognizer, stop and report unavailable; never fall back to a network recognizer.
- The service owns recognizer lifecycle independently of the Activity and releases it on disable, wake, error and destruction.
- On exact wake, stop microphone recognition and post a notification for the user to open the assistant. Do not launch an Activity from the background.
- No raw audio is uploaded or retained.

## Known implementation limitation

The current detector uses Android's general on-device speech recognizer and accepts only the primary (best-ranked) final transcript when it normalizes to exactly “Miss Minutes”; it is not a dedicated acoustic wake-word model. Its accuracy, false activations, false rejections, continuous-listening stability and battery/thermal cost are not accepted until measured on the target physical device. The app must report on-device recognition unavailable rather than using a cloud fallback.

## Acceptance

- Unit tests verify exact phrase matching, including rejection of “Hey Miss Minutes”, extra words and near matches.
- Unit tests verify offline TTS voice selection and reject network-required or wrong-language voices.
- Deno tests verify authenticated TTS gateway behavior, per-user quota enforcement, bounded input/audio, normalized provider errors and secret isolation.
- API 35 instrumentation verifies the microphone foreground-service manifest contract and bounded TTS lifecycle cleanup.
- Gitleaks, JVM tests, APK build, lint, both Edge Function test suites, API 35 instrumentation and artifact upload must pass on the exact final P3 head.
- Physical-device testing must verify dictation, on-device TTS playback/fallback, service survival after Activity/task removal, notification Stop action, exact wake behavior, false positives/negatives, lock-screen behavior, battery and thermal use on the target Samsung Galaxy A35 / Android 17.
- Emulator results are not physical-device acceptance.
