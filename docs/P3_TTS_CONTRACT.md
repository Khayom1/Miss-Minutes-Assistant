# P3 TTS — On-device implementation contract

## Scope
This increment implements text-to-speech only. No microphone permission, speech-to-text, live voice conversation, cloud voice provider, API key, account session or network request is used by TTS.

## Runtime path
Assistant message → Android TextToSpeech engine → installed device voice → speaker.

## Voice selection
- Uses Android's installed TextToSpeech engine and voices.
- Prefers a locally installed (non-network-required) voice matching the device locale.
- Selects the highest-quality available matching offline voice.
- Uses slightly reduced speech rate and pitch for a calmer delivery.
- Falls back to the device default locale when the requested locale is unavailable.
- Voice quality and Tajik language availability depend on voices installed on the Android device; the app does not claim a voice exists when it does not.

## Privacy and lifecycle
- Text remains on-device for speech synthesis.
- No audio or text is uploaded by the TTS component.
- No microphone permission is requested.
- Stop cancels current speech; activity destruction shuts down the TTS engine.
- The app exposes Speak/Stop controls on assistant messages.

## Acceptance
- Android unit tests and API 35 instrumentation pass.
- Build, lint and APK artifact succeed on the exact P3 head.
- Device playback must be checked on a physical Android device before claiming hardware acceptance.
