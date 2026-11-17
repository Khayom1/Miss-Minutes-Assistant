# P3 Acceptance — On-Device Voice

P3 is the local speech capability. Hosted ElevenLabs speech and backend voice endpoints are out of scope and have been removed.

## Required behavior

- Speech output uses Android TextToSpeech and installed voices that do not require network access.
- Voice selection is language-aware and rejects wrong-language or network-required voices.
- If no suitable installed voice exists, the app reports the language as unavailable. It must not claim Tajik, Russian, or English speech support without device evidence.
- Speech input uses an on-device recognizer when the Android device provides one. There is no cloud speech fallback.
- Microphone use is user-started, permission-gated, visible, stoppable, and bounded by Android foreground-service rules.
- Wake phrase detection uses the local recognizer only when available. It is not represented as a dedicated acoustic wake-word model unless one is actually integrated and tested.

## Automated acceptance

- JVM tests cover offline voice selection and rejection of network-required voices.
- Instrumentation covers TTS callback cleanup, service manifest contracts, permission/disclosure state, and cancellation.
- No Deno, Supabase, or hosted TTS test suite exists because no voice backend is part of the product.

## Not yet accepted

- Real playback and recognition on Samsung Galaxy A35 / Android 17.
- Tajik/Russian/English voice-pack availability and pronunciation quality.
- False-positive/false-negative wake behavior, lock-screen behavior, battery and thermal cost.
- Survival and recovery behavior after Activity/task removal, process death, and Force Stop. Android platform limits must be documented honestly.