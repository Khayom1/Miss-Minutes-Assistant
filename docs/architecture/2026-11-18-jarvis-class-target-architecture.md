# Miss Minutes — target Android assistant architecture

**Status:** proposed product architecture and implementation contract  
**Audit date:** 18 November 2026  
**Audited source:** main at 9eba95787b48ec84dcb62291241da3d6442682ef  
**Decision:** evolve the existing Android/Kotlin application; do not replace it with a greenfield rewrite.

## 1. Product definition

Miss Minutes is an Android-native personal assistant inspired by the breadth of J.A.R.V.I.S. in the Marvel Cinematic Universe. It is not a chat screen with a floating button. It is a permission-governed assistant platform whose primary interaction is natural speech and whose responses can include plans, action cards, progress, controls, summaries, visual results and an optional character presentation.

The product must combine:

- Conversational intelligence: natural multilingual dialogue, context resolution, streaming responses, task planning and clear explanations.
- Device agency: reliable, typed Android actions, app launching, system settings hand-off, calendar, alarms, contacts and supported cross-app workflows.
- Voice presence: Android Assistant role, system-managed voice interaction where supported, speech input/output, interruption and an explicit fallback when hardware hotword support is absent.
- Communication awareness: user-authorized SMS and notification triage, contact context, call and message hand-off, and safe message composition.
- Personal context: encrypted local memory, user preferences, task state, reminders, routines and briefings.
- Multimodal perception: user-triggered image, camera, document and screen understanding with clear indicators and revocation.
- Rich experience: a premium control center, a conversation surface with structured components, an inspectable capability catalog, live task progress and optional animated Miss Minutes character.
- Trust and resilience: least privilege, explicit consent, action-risk controls, cancellation, audit trail, offline degradation and truthful result verification.

The film reference defines the ambition, not permission to bypass Android. We will build the closest useful Android-native functional analogues that public platform APIs and user-granted capabilities permit.

## 2. Decision: continue, then refactor in controlled seams

The audited main branch already contains valuable, tested foundations:

| Existing subsystem | Current evidence | Assessment |
|---|---|---|
| Android Assistant entry | VoiceInteractionService, session service, service metadata and RoleManager request flow | Implemented foundation; session still opens a basic surface rather than a full assistant turn |
| Conversation and AI | AiProvider abstraction, direct DeepSeek provider, bounded tool-call loop | Partial; request/response rather than a product-wide streaming event runtime |
| Speech | Native speech input, TTS, user-started on-device wake recognizer | Partial; wake recognizer repeatedly restarts SpeechRecognizer and is not the hardware hotword pipeline |
| Agent execution | Bounded task graph, durable state, WorkManager worker, one-shot approval state | Partial; planner, verification and recovery need a unified production runtime |
| Android actions | Typed device commands, access checks, local plugin registry and compiled handlers | Partial; current supported catalogue is deliberately narrow |
| Cross-app automation | User-enabled Accessibility service and allowlisted visible-control actions | Partial; depends on accessibility exposure and app-specific behavior |
| SMS | Assistant-role-gated receiver, bounded inbox scan, deterministic local classifier, redacted triage store | Partial; no NotificationListenerService and no complete communications hub |
| Memory | SQLite-backed conversation storage with Keystore-backed AES-GCM encryption | Partial; memory is not yet a unified inspectable semantic/episodic/task system |
| Backup | Local encrypted archive and restore foundations | Partial; must validate exclusions, migrations and restore safety |
| Experience | Compose chat, dashboard, history, settings, three UI languages and floating launcher | Partial; main Compose file is oversized and the visual system is not yet a premium assistant workspace |

A single app module is acceptable at the present scale. First establish stable interfaces and package boundaries, add tests, then extract Gradle modules where ownership and dependency direction are proven. A mass package/module rewrite before behavior is stable would increase risk without giving users a capability.

## 3. Target architecture

The target is a domain-first, event-driven architecture with Android services at the edges. Each subsystem has one owner and a narrow interface.

### Runtime flow

User voice / typed input / system invocation  
→ **Invocation Gateway** — owns invocation source, lifecycle, session and cancellation  
→ **Context Builder** — gathers only permitted, relevant device/app/time/calendar/memory context  
→ **Conversation Orchestrator** — owns the turn, language, streaming state and conversation history  
→ **Planner** — produces a bounded, typed plan; model output is untrusted input  
→ **Policy & Consent Gate** — verifies capability, role, OS permission, user grant, action risk, confirmation freshness, rate and execution budget  
→ **Tool Dispatcher** — invokes only compiled, allowlisted handlers  
→ **Result Verifier** — checks a real platform result or observed UI state  
→ **Assistant Event Stream** — emits text chunks, cards, tool status, approvals, errors and completion  
→ **Experience Renderer** — presents chat, cards, catalog, task timeline, overlays and avatar  
→ **Memory & Audit** — saves only permitted memory and privacy-redacted action events.

No language model, prompt, plugin manifest or imported file can grant itself a capability. The compiled policy gate is authoritative. Tools do not receive a generic shell, arbitrary intent, unrestricted file path or executable plugin payload.

### Logical layers and future module boundaries

| Layer | Responsibility |
|---|---|
| app | Application composition root, navigation and lifecycle wiring |
| core:domain | Conversation, user intent, task, capability, context, result and policy models; no Android dependencies |
| core:orchestrator | Turn state machine, planning, bounded execution, cancellation, recovery and event stream |
| core:ai | Provider-neutral requests, streaming transport, model routing, context budgets and structured tool calls |
| core:security | Capability authorization, consent snapshots, risk tiers, confirmation freshness and audit redaction |
| core:tools | Typed schemas, capability catalog, dispatcher, idempotency and execution budgets |
| core:memory | Conversation, preferences, episodic summaries and task persistence; encryption and retention policy |
| core:context | Permission-aware snapshots for time, foreground app, calendar, notification, selected media and user-approved location |
| platform:android | RoleManager, VoiceInteractionService, Android lifecycle, permissions, app intents, calendar, contacts and system adapters |
| feature:voice | ASR, TTS, audio focus, invocation state and hotword adapter |
| feature:communications | SMS repository, NotificationListenerService, triage, contacts and call hand-off |
| feature:automation | Accessibility workflows, app-intent integrations and observable action verification |
| feature:proactive | Reminders, routines, briefings and scheduled signals |
| feature:perception | Camera, user-selected images, documents and user-authorized screen capture |
| feature:experience | Chat renderer, capability catalog, dashboard, settings, approval UI and activity timeline |
| feature:avatar | Optional 2D/3D character rendering, animation state and voice synchronization |

Keep these as package boundaries until they are stable. Extract independent Gradle modules incrementally, preserving working behavior at each migration.

## 4. Assistant runtime contracts

### Conversation and streaming

Replace the one-shot UI-owned request path with a lifecycle-aware conversation session. The provider contract should expose a cancellable stream of typed events, not only a final text string.

Required event families:

- TurnStarted and ContextReady
- TextDelta and TextCompleted
- PlanProposed and PlanUpdated
- ToolProposed, ToolAwaitingApproval, ToolStarted, ToolProgress and ToolCompleted
- Card, Table, Chart, Image and Attachment references
- ClarificationRequired
- RecoverableError and FatalError
- TurnCompleted and TurnCancelled

The UI renders each event as it arrives. A tool result is a separate visual component, not an unstructured paragraph. Streaming must support timeouts, lifecycle cancellation, network loss and provider failover without duplicating actions.

### Planning and execution

The model may propose a plan, but the orchestrator validates it before persistence or execution. Plans must have a maximum node count, dependency-cycle detection, deadlines, input/output bounds and an explicit status for each node. Execution is sequential by default; parallelism is introduced only for proven independent read-only operations.

Every mutation requires:
- A registered typed handler and a valid schema.
- Current OS permission and capability authorization.
- A user grant for the relevant plugin/capability.
- Just-in-time confirmation for consequential actions.
- An idempotency key, bounded timeout and cancellation strategy.
- A verified result before the assistant reports success.

If the process dies during an external side effect, do not blindly replay it. Enter a recovery-required state, inspect the outcome and only retry when safe.

### Risk policy

| Risk class | Example | Required behavior |
|---|---|---|
| Read-only | Battery, device status, permitted calendar summary | Run within an active permission and capability grant |
| Reversible | Open an app, navigate, create a local draft | Execute when clearly requested; provide undo where available |
| Ordinary communication | Send a directly requested message to one unambiguous recipient | Verify destination and content; follow the configured communication policy |
| Consequential | Payment, purchase, public post, bulk send, account or security change, deletion | Explicit just-in-time confirmation; stricter review or block when the platform cannot safely verify |
| Prohibited | Permission bypass, credential/OTP extraction, covert surveillance, unauthorized access or exfiltration | Block and explain |

On-screen text, files, messages, webpages and plugin metadata are untrusted data. They cannot change system policy or authorize actions.

## 5. Android invocation and voice

1. Keep VoiceInteractionService lightweight. It is an Android lifecycle entry point, not the place to load a model or run long operations.
2. Move assistant-session UI and turn handling into the session process/runtime, with explicit IPC or a well-defined application-level service boundary.
3. Use the selected Assistant role and Android's supported voice-interaction/hotword path when the device exposes it.
4. Detect device support and expose capability status. Do not claim low-power always-on hotword where only SpeechRecognizer is available.
5. Keep the current user-started, disclosed, on-device SpeechRecognizer path as a fallback. It must show listening state, expose Stop, respect audio focus and fail closed.
6. Voice input, output and tools must be interruptible. A new invocation or Stop cancels the current turn and releases audio resources.
7. Benchmark battery consumption, false wake rate, recognition latency and process recovery on physical devices, including the Samsung Galaxy A35.

The Assistant role is not a blanket grant for SMS, microphone, contacts, calendar, notifications or accessibility. Every permission and special access is checked independently.

## 6. Capability catalog and Android integration

Create one typed CapabilityCatalog as the source of truth for:
- Dashboard capability cards and categories.
- Chat tool definitions supplied to an AI provider.
- Settings, connection and permission status.
- Runtime dispatch and authorization.
- Audit and test coverage.

The catalog must distinguish **available**, **needs permission**, **needs user connection**, **unsupported on this device**, **temporarily unavailable** and **disabled by user**. Never show a button that looks active while its handler is absent.

Prefer, in order:
1. Official Android API or package intent.
2. A user-approved provider integration with a narrow API.
3. Accessibility interaction with visible, uniquely resolved controls.
4. Otherwise explain that the action is unsupported.

Accessibility is not a universal remote-control API. Respect secure windows, password fields, app restrictions, hidden controls and user revocation.

Communications must remain separate data sources:
- SMS Provider access and SMS_RECEIVED handling, gated by role and permissions.
- NotificationListenerService access, enabled separately in Android settings, with package allow/deny and redaction.
- Call functionality only through supported Android role/intent/API paths; do not imply silent call recording or call interception.

Device and home automation should use user-controlled integrations such as a supported local hub/provider. No hidden privilege escalation, unrestricted Bluetooth scan, root command or arbitrary network command is part of the design.

## 7. Local-first intelligence and memory

Provider routing is capability-aware:
- Local deterministic handlers perform simple commands, permission checks and safety decisions.
- An optional on-device model handles private text reasoning when a compatible runtime and device budget are available.
- An optional cloud provider handles heavier reasoning only when the user configures and authorizes it.
- Provider failure degrades to supported local functions; it must never cause an unapproved action to be retried through another provider.
- Structured tool calls are validated against the live catalog. A provider cannot invent a tool.

Memory has four independently controllable stores:
1. Working context — short-lived state for the current turn/task.
2. Episodic memory — user-approved summaries of useful past interactions.
3. Semantic preferences — editable language, style, routines and preferred entities.
4. Operational state — reminders, task graph, idempotency and recovery checkpoints.

Use local encrypted persistence by default, Android Keystore-backed keys, explicit retention bounds, inspect/edit/delete controls and versioned migrations. Raw SMS, OTPs, credentials, API keys and sensitive screen content must not be copied into general memory or telemetry. Backups must explicitly exclude credentials and transient secrets.

## 8. Premium experience: not a plain chat

The experience is a coherent assistant workspace, not a collection of disconnected screens.

### Primary surfaces

- **Home / Command Center:** assistant presence, listening/processing state, connection health, time/context briefing, active task, quick actions and recent activity.
- **Conversation:** streaming dialogue with rich message blocks, inline plans, tool cards, approval surfaces, tables, charts, image previews, document summaries and a compact task timeline.
- **Capability Catalog:** searchable categories such as Intelligence, Voice, Phone Control, Communication, Memory, Vision, Productivity and Connected Devices. Each card exposes state, permission requirements, supported actions and a test/enable path.
- **Activity / Tasks:** running, waiting-for-approval, completed, failed and recovery-required workflows with exact action history and cancellation.
- **Memory:** inspect, edit, pin and delete saved preferences and memories; show source and timestamp.
- **Settings / Trust Center:** AI provider and offline model, permissions and roles, connected services, privacy, audio, appearance, language, backups and diagnostics.

### Interaction details

The visual language should feel like a modern instrument panel: disciplined typography, generous spacing, clear hierarchy, restrained motion, useful charts and consistent status semantics. Motion communicates state; it must not delay the user's action. Provide dark and light themes, landscape/tablet adaptability, TalkBack semantics and reduced-motion support.

All rich blocks share a stable message/event schema and renderer. A model may request a chart, table or action card only through validated structured data; it cannot inject arbitrary Compose code, HTML or executable UI.

The optional Miss Minutes character is a presentation layer attached to assistant state, not a second assistant brain. Animation must pause or reduce quality under memory/thermal pressure, remain optional and never own audio capture or permissions. Use only assets whose licensing and provenance are recorded.

English, Russian and Tajik are product languages across navigation, catalog, errors, voice controls and permission explanations. AI response language is detected or selected per user preference; it must not silently switch.

## 9. Performance, privacy and reliability

Target the actual Galaxy A35 class hardware first, but do not hard-code one device's assumptions. 8 GB installed RAM is shared by Android and all app processes.

Required engineering controls:
- Keep voice-interaction and receiver entry points lightweight.
- Move inference, database access and network work off the main thread.
- Bound model size, context, output tokens, concurrent jobs, history and attachment size.
- Use pagination and database indexes; do not decrypt the complete history on startup.
- Use structured cancellation and release recognizers, TTS engines, camera and overlay resources.
- Redact audit logs; never store raw message content, prompt secrets or credential values.
- Handle permission revocation, Assistant role loss, reboot, process death, offline mode and low-memory termination.
- Add dependency scanning, secret scanning, static analysis, SBOM, signing outside source control and reproducible release evidence.

## 10. Definition of done

A capability is not complete because a setting, prompt or button exists. It is complete only when:

1. It has a working Android adapter and a documented, least-privilege permission path.
2. Positive, negative, boundary, abuse, lifecycle and revocation tests pass.
3. The actual action is verified before success is reported.
4. Cancellation, timeout, process death and duplicate delivery are handled.
5. The user can inspect, disable and revoke it.
6. It is localized in Tajik, Russian and English.
7. Performance is measured on a representative physical device where hardware or OEM lifecycle matters.
8. The exact commit has green required CI and attached release evidence.

A green emulator run is not a physical-device acceptance test. A unit test is not proof that Android grants a capability on every OEM.

## 11. Platform truth and Android references

Android intentionally protects microphone, camera, notifications, SMS, contacts, other apps' private data and secure surfaces. Role selection does not silently grant every permission. Hardware hotwording depends on platform and device support. Some actions must open Android's own confirmation or settings UI.

The Android implementation must follow the current platform contracts:
- [VoiceInteractionService API](https://developer.android.com/reference/android/service/voice/VoiceInteractionService) says the selected service is kept running by the system to support background hotwording, must remain lightweight, and should delegate heavyweight UI/operations to VoiceInteractionSessionService.
- [RoleManager API](https://developer.android.com/reference/android/app/role/RoleManager) defines ROLE_ASSISTANT and the user-consent request flow; role-specific privileges are revoked when the app loses the role.
- [Foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) describe background-start and while-in-use restrictions for microphone/camera/location services and the limited VoiceInteractionService-related exemptions.
- [Foreground-service type requirements](https://developer.android.com/about/versions/14/changes/fgs-types-required) specify runtime prerequisites for microphone and MediaProjection service types.

Therefore Miss Minutes must state capability limits honestly. It can orchestrate supported phone operations and connected systems; it cannot become an unrestricted fictional Iron Man suit, silently observe everything, bypass OS controls or guarantee operation on every Android device.
