# P3 TTS Contract — Local Android Speech

## Speech output

- Use Android TextToSpeech with installed voices only.
- Prefer the best matching installed voice for the requested language, provided it does not require a network connection.
- Never silently choose a network-required voice for an offline request.
- Stop/cancel speech on user request and release the TTS engine with the owning lifecycle.
- If no offline voice matches the requested language, report the capability as unavailable.
- No ElevenLabs, hosted neural speech, server-side audio proxy, temporary remote audio cache, or voice-provider secret exists in the product.

## Speech input and wake behavior

- Use an on-device Android recognizer when supported.
- No cloud transcription fallback is permitted.
- Microphone access is user-started, permission-gated, disclosed by Android UI/notification, and stoppable.
- The current wake phrase path is a local recognizer heuristic, not a dedicated acoustic model. Accuracy is not accepted until measured on the physical target device.
- Do not promise indefinite background listening. Android Doze, process death, foreground-service restrictions, and Force Stop apply.

## Tests and evidence

- Unit tests verify local voice selection and rejection of network-required or wrong-language voices.
- Instrumentation verifies TTS callback/lifecycle cleanup and microphone service manifest contracts.
- Physical-device testing must verify actual installed voice packs, Tajik/Russian/English playback, dictation, wake behavior, false activations/rejections, lock-screen behavior, battery, and thermal use on Samsung Galaxy A35 / Android 17.