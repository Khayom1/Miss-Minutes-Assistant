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
The current detector is exact transcript matching over Android's general on-device speech recognizer, not a dedicated acoustic “Miss Minutes” wake-word model. Its accuracy, false activations, false rejections, continuous-listening stability and battery/thermal cost are not accepted until measured on the target physical device. The app must report on-device recognition unavailable rather than using a cloud fallback.

## Acceptance
- Unit tests verify exact phrase matching, including rejection of “Hey Miss Minutes”, extra words and near matches.
- API 35 instrumentation verifies the microphone foreground-service manifest contract and TTS lifecycle callbacks.
- Gitleaks, JVM tests, APK build, lint and API 35 instrumentation must pass on the exact final P3 head.
- Physical-device testing must verify TTS playback, service survival after Activity/task removal, notification Stop action, exact wake behavior, false positives/negatives, lock-screen behavior, battery and thermal use on the target Samsung device.
- Emulator results are not physical-device acceptance.