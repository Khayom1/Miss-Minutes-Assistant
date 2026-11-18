# P3 Acceptance — On-Device Voice

P3 is the local speech capability. Hosted speech providers and backend voice endpoints are out of scope.

## Required behavior

- Speech output uses Android TextToSpeech and installed voices that do not require network access.
- Voice selection is language-aware and rejects wrong-language or network-required voices.
- If no suitable installed voice exists, the app reports the language as unavailable. It must not claim Tajik, Russian, or English speech support without device evidence.
- Speech input uses an on-device recognizer when the Android device provides one. There is no cloud speech fallback.
- Microphone use is user-started, permission-gated, visible, stoppable, and bounded by Android foreground-service rules.
- Wake phrase detection uses the local recognizer only when available. It is not represented as a dedicated acoustic wake-word model unless one is actually integrated and tested.
- The listener must parse a wake phrase followed by a command, not require the utterance to contain only the exact words "Miss Minutes".
- A voice command must enter the same bounded AI/agent/tool runtime as a typed chat request. Connected plugins are discovered automatically; the user must not manually select a plugin for every request.
- When the user explicitly enabled listening before locking the screen, lock-screen command capture/processing should work while Android permits the active microphone foreground service. Sensitive actions remain gated by unlock and explicit approval.

## Automated acceptance

- JVM tests cover offline voice selection and rejection of network-required voices.
- Unit tests cover wake-phrase-plus-command parsing, multilingual variants where supported, cancellation, stale callbacks, duplicate suppression, and command handoff.
- Instrumentation covers TTS callback cleanup, service manifest contracts, permission/disclosure state, and cancellation.
- Agent tests cover automatic connected-tool discovery, bounded multi-step execution, missing permissions, confirmation-required actions, and task recovery.
- No hosted voice backend or cloud audio fallback is permitted.

## Physical-device acceptance required

- Real playback and recognition on Samsung Galaxy A35 / Android 17.
- Tajik/Russian/English voice-pack availability and pronunciation quality.
- Wake false-positive/false-negative behavior and battery/thermal cost.
- Screen locked and screen off while the user-started service is already active.
- Recovery after Activity/task removal, process death, permission revocation, battery saver and Doze.
- Force Stop must be respected; no claim of wake listening after Force Stop.
- Verify that lock-screen notifications do not expose private content and that protected actions require unlock/approval.

## Not yet accepted

All physical-device checks above remain open until evidence is recorded. A running emulator or a green JVM suite does not prove real lock-screen microphone operation on Samsung firmware.
