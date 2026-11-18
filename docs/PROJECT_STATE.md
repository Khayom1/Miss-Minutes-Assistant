# Miss Minutes — audited repository state

**Audit timestamp:** 18 November 2026  
**Repository:** Khayom1/Miss-Minutes-Assistant  
**Branch:** main  
**Audited HEAD:** 9eba95787b48ec84dcb62291241da3d6442682ef

## Summary

The project should be continued and refactored, not replaced. The main branch contains a substantial Android-native foundation, but the current product is not yet a complete J.A.R.V.I.S.-class assistant.

## Verified on main

- Kotlin Android app, Jetpack Compose UI, one Gradle app module, minSdk 26 / compileSdk 36 / targetSdk 36, Java 17.
- English, Russian and Tajik UI resources.
- Android Assistant Role helper, VoiceInteractionService, VoiceInteractionSessionService and recognition service declarations.
- DeepSeek provider abstraction, bounded AI tool-call loop, native speech input and TTS.
- User-started on-device SpeechRecognizer wake service with foreground microphone notification.
- Bounded agent task graph, encrypted durable task state, WorkManager worker and recovery-required state. The ordinary chat tool-call loop is not yet backed by that persisted task graph.
- The main Compose chat currently dispatches model tool calls through AiToolExecutionLoop and always passes userConfirmed=false. This fails closed for consequential actions, but there is no interactive just-in-time approval dialog in the ordinary chat path on this baseline. Draft PR #49 proposes an approval UI and must be rebased/audited before integration.
- Closed-world plugin registry with compiled device-status and Accessibility handlers.
- Device info, battery, time, calendar summary/create reminder, contacts summary, alarm and app launch actions.
- Accessibility inspect/click/type/scroll/back actions with guardrails.
- Assistant-role-gated incoming SMS receiver, bounded inbox scan, local deterministic classifier and redacted triage metadata.
- Keystore-backed encrypted conversation persistence and backup/restore foundations.
- User-started overlay that launches the app; it is not an always-on assistant surface.

## Verified gaps on main

- VoiceInteractionSession is a basic title/body/open-app surface, not a full assistant turn.
- VoiceInteractionService does not implement a device-validated low-power hotword detector; the separate wake service repeatedly restarts SpeechRecognizer.
- No NotificationListenerService implementation found in source.
- No CameraX, MediaProjection or image-understanding adapter found in source.
- The main Compose UI remains heavily concentrated in MissMinutesApp.kt (approximately 143 KB at audit time).
- The AI provider path is request/response; a unified streamed event runtime is not present.
- The production capability catalog is narrow; no connected-home hub, general web research, robot control or wearable sensor integration is present.
- The local model adapter on main is an interface/fail-closed seam; offline LiteRT-LM is in an unmerged draft PR and must be audited/rebased before consideration.
- Several open draft PRs are based on older main commits or are stacked. They must not be merged in bulk.

## CI evidence at audit time

Latest observed workflow run for the audited main HEAD:
- Run: 44323036590
- URL: https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44323036590
- JVM unit tests: passed.
- Kotlin debug/release compilation: passed.
- Debug/release APK assembly: passed.
- Gitleaks: passed.
- Job failed at Android Lint because the manifest declared READ_SMS/RECEIVE_SMS without declaring telephony hardware as optional.
- Instrumentation tests and release evidence steps were skipped after the lint failure.

This is a specific manifest metadata defect, not evidence that all Android tests passed. The fix is to declare android.hardware.telephony with required=false so tablets and ChromeOS devices are not excluded by the permission implication.

## Known technical risks to track

1. Calendar event creation currently assumes calendar ID 1 and inserts the event/reminder separately. The writable calendar must be resolved and provider failure/partial success handled.
2. The VoiceInteractionSession process is separated in the manifest; ensure its UI/runtime integration uses a safe, explicit process boundary.
3. DeviceToolEngine checks elapsed time after the synchronous host action returns; this is not a hard cancellation timeout.
4. The direct DeepSeek provider requires a user-owned API key and sends the request to the configured provider; local privacy mode must be clearly distinguished.
5. SMS and notification access are separate Android capabilities and must have separate controls and policy.
6. Unit tests and emulator instrumentation do not replace physical Galaxy A35 testing for wake, battery, thermal and OEM lifecycle behavior.

## Source-of-truth rule

Use the live main branch and exact source commit when making implementation decisions. Older branch summaries and old documentation are not proof of current state. Update this file whenever a material capability is merged or removed.
