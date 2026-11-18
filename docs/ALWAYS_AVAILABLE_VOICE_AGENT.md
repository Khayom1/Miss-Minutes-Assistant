# Always-Available Voice Agent — Product Contract

Status: REQUIRED, NOT YET IMPLEMENTED OR ACCEPTED.

## User experience

The owner must be able to enable an always-available voice mode once while the app is visible and permissions are granted. After that, the intended flow is:

1. The user says "Miss Minutes" without opening the app.
2. The on-device wake listener detects the phrase while its user-started foreground service is alive.
3. The assistant captures the command that follows the wake phrase, including a natural phrase such as "Miss Minutes, read my latest notification" or "Miss Minutes, create a reminder for tomorrow".
4. The command is passed to the same AI conversation/task runtime used by chat.
5. The model discovers the currently connected plugin/tool catalog automatically, selects the required tools, plans bounded steps, and executes eligible actions. The user must not manually open the app and choose a plugin for each request.
6. The assistant speaks or otherwise reports the result. If the command needs an Android permission, missing credential, plugin connection, or consequential-action approval, it requests only that missing authorization.

A connected plugin is discovered automatically at request time. Plugin connection and Android permission grants are one-time/user-controlled setup, not per-request plugin picking. A disconnected, unsupported, or unauthorized tool must never be silently used.

## Lock-screen behavior

- If the listener was explicitly enabled before the screen locked, it should continue only while Android permits the microphone foreground service and on-device recognizer to run.
- The assistant may capture and process a voice command while the screen is locked, subject to OS permissions, device policy, battery policy, and the app's current service state.
- The app must not claim that it can start microphone capture from a cold background state on every Android version, survive Force Stop, bypass OEM battery restrictions, or listen after the user disables it.
- Unlock-required or sensitive actions must remain blocked until the user unlocks the device. Do not bypass the keyguard or expose private content in lock-screen notifications.
- A persistent, clear notification and a one-tap stop control are required while listening.
- If the service is stopped, denied, killed, or the recognizer becomes unavailable, publish an honest state and provide a recovery path when the user opens the app.

## Android lifecycle and privacy constraints

- Listening is opt-in, user-started from a visible Activity, permission-gated, and disclosed.
- Use the correct foreground-service type and satisfy the current target-SDK start restrictions. Never try to evade Android background-start or microphone restrictions.
- Prefer an on-device recognizer and offline TTS. Never upload microphone audio to a hosted speech service.
- Respect microphone privacy toggles, runtime permission revocation, notification denial, battery saver, Doze, process death, task removal, and Force Stop.
- Do not use an always-on partial wake lock as a workaround. Measure battery and thermal cost on a physical Samsung Galaxy A35.
- Wake detection must distinguish the wake phrase from the following command. Exact-phrase-only matching is insufficient for utterances such as "Miss Minutes, do this".
- Prevent duplicate command execution, stale recognition callbacks, replayed commands, and concurrent task races.

## Agent and tool execution contract

- Voice and text requests must enter the same bounded agent/task orchestration path.
- The model receives only schemas for connected, compiled, allowlisted tools.
- Tool discovery and selection are automatic per request; no manual plugin selection is required.
- Recheck OS permissions, credentials, plugin consent, task limits, and current conversation scope immediately before execution.
- Read-only actions may run after their required access checks.
- Writes, purchases, messages, calls, deletion, and other consequential actions require a clear one-time confirmation at the moment of action. A lock screen must not be treated as confirmation.
- Persist task state safely so process death cannot cause duplicate side effects. WorkManager is deferrable and is not a guarantee of immediate execution.

## Required implementation work

1. Replace exact-only wake matching with a tested wake-phrase-plus-command parser.
2. Add a secure local handoff from the voice service to a process-independent command coordinator; do not put secrets or raw private content in broadcasts/notifications.
3. Extract AI/tool execution out of Compose UI so a foreground voice/task service can use the same provider, encrypted key store, conversation store, plugin registry, approval gate, and bounded agent runtime.
4. Add a lifecycle-aware voice session state machine: idle, listening, wake detected, capturing command, processing, awaiting approval/unlock, speaking, recoverable error, stopped.
5. Implement automatic connected-plugin schema discovery and multi-step task planning for voice requests.
6. Add safe speech output and interruption/cancellation controls.
7. Add tests for command parsing, cancellation, duplicate suppression, process recreation, permission loss, lock-screen entry, tool denial, approval, and task recovery.
8. Run exact-head CI and physical-device acceptance on Samsung Galaxy A35 / Android 17, including screen locked, screen off, battery saver, Doze, task swipe-away, process kill, permission revocation, and Force Stop.
9. Document which states are supported by Android and which require the user to reopen the app.

## Release acceptance — no false claims

This feature is NOT complete until all automated tests pass and physical-device evidence is recorded. Emulator tests alone do not prove lock-screen microphone behavior or OEM battery-policy compatibility. Force Stop survival and unrestricted always-on listening are explicitly not promised.
