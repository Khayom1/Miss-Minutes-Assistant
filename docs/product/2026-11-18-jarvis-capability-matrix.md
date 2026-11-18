# J.A.R.V.I.S. film capabilities → Miss Minutes Android capability matrix

**Purpose:** turn the fictional reference into a realistic, testable Android product scope.  
**Code baseline:** Miss-Minutes-Assistant main at 9eba95787b48ec84dcb62291241da3d6442682ef (18 Nov 2026).

J.A.R.V.I.S. is depicted differently across films and situations. The recurring product ideas are broad: Tony Stark's conversational assistant, control of the Stark home and facilities, integration with Iron Man armor, real-time telemetry and diagnostics, analysis/research, communication, and coordination of the Iron Legion. This document maps those ideas to user-authorized Android functions; it does not claim the phone can reproduce fictional armor hardware.

Status meanings:
- **FOUNDATION:** real code exists, but the complete user-facing capability is not finished.
- **PARTIAL:** a constrained version works; significant integration or quality work remains.
- **NOT IMPLEMENTED:** no real adapter/feature is present on audited main.
- **NOT AN ANDROID APP CAPABILITY:** requires external hardware, privileged platform access or a separate connected system.

| Film reference / user value | Miss Minutes Android implementation | Main status | Completion evidence |
|---|---|---|---|
| Natural conversation with a fast, context-aware assistant | Multilingual conversation runtime, context builder, streamed text, dialogue interruption and structured responses | PARTIAL — DeepSeek provider and bounded tools exist; streaming/runtime is not unified | Voice and text turns stream; cancellation works; context citations/sources are inspectable; offline fallback works |
| J.A.R.V.I.S. understands Tony's environment and current task | Permission-aware context snapshot: foreground app labels, time, calendar, user-selected files, allowed notifications and explicit user-shared screen context | PARTIAL — current context is limited and distributed | Each context source has a permission gate, freshness timestamp, source label and exclusion control |
| Controls Stark's home, lights and environment | User-authorized connected-home bridge/provider (for example, local hub or official integration), rooms, scenes and device state cards | NOT IMPLEMENTED | Discover only paired/authorized devices; allowlist operations; verify reported device state; no generic LAN command runner |
| Manages Stark Tower / facility systems | Dashboard for connected services, devices, schedules and operational events | NOT IMPLEMENTED | Each integration has health, scopes, disconnect/revoke, event history and integration tests |
| Operates Iron Man armor systems and displays telemetry | Phone-side device controls, battery/thermal/network diagnostics and supported connected accessory telemetry | PARTIAL — Android device info and battery tools exist | Capability discovery per device; bounded adapter tests; no claim of controlling aircraft, armor actuators or protected OS internals |
| Suit diagnostics, maintenance and real-time analysis | Diagnose app/device health, permission state, connectivity and assistant runtime; explain faults and propose repair steps | PARTIAL — basic device/battery and plugin audit foundations exist | Reproducible diagnostics report; severity, source, recommendation and verified resolution |
| Real-time voice presence and invocation | Android Assistant role, system voice interaction, session UI, ASR/TTS, hotword adapter and explicit fallback | FOUNDATION — RoleManager and service declarations exist; session is a launch surface; wake service uses repeated on-device SpeechRecognizer | Works as the selected Assistant on supported devices; measured wake latency/false positives/battery; visible fallback and Stop |
| Handles phone calls and communications | Contact lookup, call-intent hand-off, message composition, SMS triage and notification summaries | PARTIAL — contact summary and SMS receiver/classifier exist; no notification listener or call experience | Separate grants, clear destination and content, message/call hand-off result, no covert recording or auto-send by classifier |
| Filters important messages and warns Tony | Local SMS classifier and a separate notification triage pipeline with user-defined priority and app allowlist | PARTIAL — SMS-only triage implemented | Deduplication, confidence and reason codes; raw content not placed in analytics; notification access revocable; unknowns remain visible |
| Investigates information and reasons across data | Model-assisted search and summaries over user-authorized web results, documents and local data, with citations/source provenance | PARTIAL — cloud dialogue exists; no integrated research/search and evidence surface on main | Each external fact has source/time; untrusted data cannot trigger tools; local/private material stays local unless opted in |
| Forensic-style image/screen understanding and HUD | User-selected images/documents, explicit screen sharing/capture and multimodal model adapter; result cards in chat | NOT IMPLEMENTED | Consent before capture; unmistakable capture indicator; only selected content transmitted; delete source/context; no secure-screen bypass |
| Holographic displays and visual control surfaces | Premium Compose Command Center, live task cards, charts, tables, rich result blocks and optional 3D character | PARTIAL — basic Compose chat/dashboard and text overlay exist | Design system, responsive layouts, accessibility, three languages, structured blocks and performance benchmarks |
| Remembers Tony's habits and preferences | Encrypted semantic preferences, episodic memory and task history with user controls | PARTIAL — encrypted conversation storage and encrypted agent state exist | User can view/edit/delete; provenance and retention; no secrets or raw OTPs; memory retrieval tested |
| Delegates and coordinates multi-step work | Bounded planner, dependency graph, allowlisted tools, approval, WorkManager, task timeline and recovery | FOUNDATION — graph runner, durable store and worker exist; product-wide planner and verification remain incomplete | Cycle/budget rejection, idempotency, approval freshness, cancellation and process-death recovery tests |
| Controls Iron Legion / robotic units remotely | Separate opt-in integrations to supported consumer/educational robots or external control systems | NOT AN ANDROID APP CAPABILITY by itself | Every robot requires its own authenticated protocol, physical safety layer, operator consent and hardware validation; no weaponization |
| Monitors Tony's vital signs / suit life support | Display data from a paired, user-authorized health wearable or sensor where available; present readings as device data, not diagnosis | NOT IMPLEMENTED | Validate sensor identity, units, timestamp and missing data; no medical diagnosis or fabricated vital signs |
| Works continuously and never misses a task | Resilient system assistant where Android permits, scheduled reminders and user-visible fallback | PARTIAL | Reboot, doze, battery saver, permission loss and OEM behavior tested; no guarantee beyond platform support |

## 1. What the present app can genuinely do

On audited main, the code contains:
- A Kotlin/Jetpack Compose Android app with English, Russian and Tajik UI resources.
- A DeepSeek AI provider abstraction and a bounded provider/tool-call loop.
- Android system Assistant service declarations, a VoiceInteractionSession service, and a user-consented ROLE_ASSISTANT request helper.
- Native Android speech input and TTS; a user-started on-device wake-word service that repeatedly restarts SpeechRecognizer.
- A durable, bounded agent task graph and WorkManager worker with explicit approval state, idempotency keys and recovery-required handling.
- A closed-world plugin registry: imported manifests are declarative metadata; only compiled handlers can execute.
- Device/battery/time/calendar/contact/alarm/app-launch actions and allowlisted Accessibility actions.
- Assistant-role and permission-gated incoming SMS processing, explicit inbox scan and deterministic local classification that persists redacted metadata.
- Keystore-backed encrypted conversation storage, backup/restore foundations and a user-started floating launcher shortcut.

These are real foundations, not proof that the full film capability set is complete.

## 2. Critical gaps to close

1. Replace the session's launch-only behavior with a real voice conversation session connected to the shared orchestrator.
2. Use the platform hotword path only on devices where it is available; keep the disclosed recognizer fallback and measure its battery cost.
3. Add NotificationListenerService as a distinct, separately granted source; do not confuse notifications with SMS Provider access.
4. Replace the single giant Compose screen with feature-owned surfaces and a common event/card renderer.
5. Introduce true streaming turns, provider routing, plan rendering and action verification.
6. Complete the user-facing approval lifecycle for consequential tool actions; model output must never set userConfirmed by itself.
7. Add real app/device integrations only through supported APIs and verifiable state.
8. Add multimodal image/document/screen support with explicit capture scope and privacy controls.
9. Build inspectable memory and proactive routines with clear retention, deletion and provenance.
10. Perform physical-device performance, lifecycle, privacy and compatibility acceptance tests.

## 3. Non-negotiable safety and truth rules

- No root requirement, permission bypass, secret extraction, covert recording or silent monitoring.
- No generic shell, arbitrary intents or downloaded executable plugins.
- The model may suggest an action; only the compiled policy layer can authorize it.
- Consequential actions require just-in-time user confirmation, not a broad one-time app consent.
- Do not report success until Android or the connected service returns a verifiable result.
- The user can inspect, pause and revoke every sensitive capability.
- A connected device is not assumed to be available because its integration card exists.
- If Android or a device OEM does not expose an operation, show it as unsupported rather than simulating it.

## 4. Reference basis

The MCU portrayal is summarized at a high level from these sources:

- [D23 — MCU Robot Roundup](https://d23.com/mcu-robot-roundup/) describes J.A.R.V.I.S. securing Tony Stark's mansion and Stark Tower, supporting Stark Industries and serving as a source of information for the Avengers; it also describes a J.A.R.V.I.S.-controlled second Iron Legion.
- [Iron Man (2008) screenplay transcript](https://www.springfieldspringfield.co.uk/movie_script.php?movie=iron-man) includes the HUD, virtual walkaround, control-surface checks, diagnostics, weather/air-traffic checks and flight-test calculations.
- [VFX Blog — The original Iron Man HUD](https://vfxblog.com/ironman/) discusses the film HUD's diagnostic, flight, power, radar and navigation modes and how information is presented visually and verbally.
- [VICE — Inside Iron Man's suit in Age of Ultron](https://www.vice.com/en/article/take-an-exclusive-look-inside-iron-mans-suit-from-marvels-avengers-age-of-ultron/) describes diagnostic displays and maintenance/updates for the suit and Iron Legion.

The film examples describe a fictional system integrated with specialized armor, facilities, sensors and robotics. They are product inspiration, not a claim that an Android phone can reproduce those physical systems or bypass platform security.
