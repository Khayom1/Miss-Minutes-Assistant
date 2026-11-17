# P3 — On-device voice implementation contract

## Text-to-speech
- Android native TextToSpeech only; no cloud TTS provider, API key, network request or audio upload.
- Prefer the highest-quality installed offline voice matching the device locale.
- Speak/Stop controls are available for assistant messages.
- Speech is cancelled on user stop/sign-out and the TTS engine is shut down with Activity destruction.
- Device voice quality and Tajik voice availability are device-dependent.

## Offline wake phrase
- Exact phrase: “Miss Minutes”. The transcript matcher must reject “Hey Miss Minutes” and any transcript containing extra words.
- Listening is off by default and requires an explicit user action in Settings.
- Show the microphone/audio-processing disclosure before requesting RECORD_AUDIO and POST_NOTIFICATIONS.
- Start the microphone foreground service only from the visible Activity after permission grants.
- Declare FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, RECORD_AUDIO, and POST_NOTIFICATIONS; service is non-exported, microphone-typed and stopWithTask=false.
- Keep an ongoing foreground notification with an explicit Stop action while listening.
- Use SpeechRecognizer.createOnDeviceSpeechRecognizer only. If the OS has no on-device recognizer, stop and report unavailable; never fall back to a network recognizer.
- The service owns recognizer lifecycle independently of the Activity and releases it on disable, wake, error and destruction.
- On exact wake, stop microphone recognition and post a notification for the user to open the assistant. Do not launch an Activity from the background.
- No raw audio is uploaded or retained.

## Known implementation limitation
The current detector uses Android's general on-device speech recognizer and accepts only the primary (best-ranked) final transcript when it normalizes to exactly “Miss Minutes”; it is not a dedicated acoustic wake-word model. Its accuracy, false activations, false rejections, continuous-listening stability and battery/thermal cost are not accepted until measured on the target physical device. The app must report on-device recognition unavailable rather than using a cloud fallback.

## Implemented hybrid speech paths

- Chat composer includes a user-triggered dictation control. It requests RECORD_AUDIO at the point of use and uses Android's on-device SpeechRecognizer only; there is no network-recognition fallback.
- Dictation uses the currently selected English, Russian or Tajik locale. Unsupported offline language packs return a visible error and do not silently upload audio.
- Assistant speech first calls the authenticated `miss-minutes-tts` Supabase Edge Function. The Android client sends the user's short-lived access token and the public Supabase key only; the ElevenLabs credential remains server-side.
- The Edge Function validates the Supabase session, bounds text and audio sizes, enforces a per-user rate limit, normalizes provider errors and never returns provider credentials.
- If hosted TTS is unavailable, Android falls back to an installed offline TTS voice. Network TTS audio is written only to the app cache, bounded to 5 MiB, and deleted after playback, stop or error.
- Edge Function source and Deno security tests are tracked in `supabase/functions/miss-minutes-tts/` and included in CI.

## Remaining acceptance gates

- Confirm the deployed Supabase Edge Function has `ELEVENLABS_API_KEY` configured as a server secret and the TTS quota RPC is active. Missing configuration must return a normalized unavailable response and trigger local fallback.
- Run the new Android dictation and hybrid TTS flows on the physical Samsung Galaxy A35 / Android 17. Emulator CI cannot prove language-pack availability, audio route behavior or Samsung background restrictions.
- Validate the continuous wake phrase detector's false-positive/false-negative rate, task-removal survival, lock-screen behavior, battery and thermal cost. The current detector is Android general on-device ASR, not a dedicated acoustic wake-word model.

## Acceptance

- Unit tests verify exact phrase matching, including rejection of “Hey Miss Minutes”, extra words and near matches.
- Deno tests verify authenticated TTS gateway behavior, per-user quota enforcement, bounded input/audio, normalized provider errors and secret isolation.
- API 35 instrumentation verifies the microphone foreground-service manifest contract and TTS lifecycle callbacks.
- Gitleaks, JVM tests, APK build, lint, both Edge Function test suites and API 35 instrumentation must pass on the exact final P3 head.
- Physical-device testing must verify dictation, TTS playback/fallback, service survival after Activity/task removal, notification Stop action, exact wake behavior, false positives/negatives, lock-screen behavior, battery and thermal use on the target Samsung device.
- Emulator results are not physical-device acceptance.
