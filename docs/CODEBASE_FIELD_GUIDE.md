# Miss Minutes — Codebase Field Guide

> Living engineering reference. Refresh this document against the live target branch before every implementation task.
> Reviewed from \`main\` at commit \`7ba30a194a431eed1bc5d5fda41182bd5e9e4ee5\` (2026-11-18).
> This is a code-grounded snapshot, not a promise that later commits or open pull requests are already integrated.

## 1. Source of truth and product identity

- Repository: \`Khayom1/Miss-Minutes-Assistant\`; default branch: \`main\`.
- Snapshot tree: \`2de15e56294f445afdb8a2a1f4dc8289675afa27\`; recursive tree contained 154 entries and was not truncated.
- Android application ID / namespace: \`com.khayom1.missminutes\`.
- \`minSdk = 26\`, \`targetSdk = 36\`, \`compileSdk = 36\`; Java 17; AGP 8.13.2; Kotlin plugin 2.4.21. CI provisions Gradle 8.13.
- The app is a text-first, offline-first Android assistant with a Jetpack Compose UI. The current mainline is not a rendered 3D Miss Minutes character: it has no active 3D model/SceneView runtime.
- It starts in a local guest workspace (\`guest-local\`). There is no required account, backend, cloud-memory sync, API proxy, or hosted speech service.
- Optional remote AI is direct DeepSeek BYOK (bring your own key). A local inference adapter interface exists, but main's \`MainActivity\` wires the DeepSeek provider only.
- Product identity says version \`0.2.0\` in \`ProductIdentity.kt\`, while the Gradle application configuration currently declares \`0.1.0\`. Verify and reconcile before release.

## 2. Runtime entry and message flow

### App startup

1. \`MainActivity.onCreate\` builds \`OkHttpAiHttpTransport\`, \`AndroidDeepSeekApiKeyStore\`, \`DirectDeepSeekProvider\`, \`AndroidTtsProvider\`, \`AndroidSpeechInputProvider\`, and \`LocalConversationStore\`.
2. Conversation encryption uses \`AndroidKeystoreConversationCipher\`.
3. The providers/stores are injected into \`MissMinutesApp\`.
4. \`MainActivity.onDestroy\` closes TTS, speech input, and the local conversation store.
5. \`MissMinutesApp.kt\` is the main application state/UI coordinator. It is very large (about 2,656 lines in this snapshot) and contains chat, navigation, settings, voice state, storage operations, plugin setup, backup flows, and much of the orchestration wiring.

### Typed chat request

1. The user message and recent conversation history are assembled by \`buildAiContextMessages\`.
2. Saved memory is included only when the explicit memory-context setting is enabled. Saved notes are treated as untrusted user data, not privileged instructions.
3. The current connected plugin catalog is obtained from \`AgentTaskRuntime\` / \`AgentOrchestrator.availablePluginTools()\`.
4. \`PluginToolSchemaFactory\` converts connected compiled tools to bounded JSON Schema. The resulting schemas are included in \`AiRequest\`.
5. \`AiToolExecutionLoop\` calls the selected \`AiProvider\`, validates tool-call names against the request catalog, and bounds rounds/calls/messages/results.
6. Tool calls are dispatched through \`AgentOrchestrator.executePluginTool\` → \`PluginRegistryAgentActionExecutor\` → \`PluginToolCallDispatcher\` → \`PluginRegistry\` → compiled handler.
7. Tool execution results are returned to the model; the final assistant response is stored in the conversation database.
8. User and assistant message content is encrypted before SQLite persistence.

The chat tool loop currently passes \`userConfirmed = false\`. It therefore cannot silently authorize a consequential write. A separate, explicit model-tool approval flow must be completed before write actions can be driven end-to-end from ordinary chat.

## 3. Production source map

The current production source tree has 44 Kotlin files.

### \`ai/\` — model transport, credentials, and speech adapters

- \`AiProvider.kt\`: provider contracts, message/tool/request data models, input bounds, provider errors, optional local-runtime interface, and bounded OkHttp HTTP transport.
- \`DirectDeepSeekProvider.kt\`: DeepSeek Chat Completions serialization, HTTPS request, error mapping, response validation, and structured tool-call parsing.
- \`AndroidDeepSeekApiKeyStore.kt\`: user key storage; AES-256-GCM ciphertext in app-private preferences with the AES key held by Android Keystore.
- \`AiToolExecutionLoop.kt\`: provider/tool loop; default maximum two rounds and four calls, with stricter request/catalog/result bounds.
- \`ConversationContextBuilder.kt\`: history and optional saved-memory context; memory text is explicitly marked untrusted.
- \`AndroidSpeechInputProvider.kt\`: one-shot on-device Android speech recognition, API 31+; no hosted-recognition fallback.
- \`AndroidTtsProvider.kt\`: Android TTS with an installed offline voice selected for the requested/system locale; network-required voices are rejected.

### \`agent/\` — bounded task execution

- \`AgentOrchestrator.kt\`: validates DAG plans, dependencies, limits, deadlines, pause/resume/cancel, approval state, one-shot approval expiry, and interrupted-action recovery.
- \`AgentActionCatalogProvider.kt\`: exposes the connected tool catalog and strict tool dispatcher contract.
- \`PluginRegistryAgentActionExecutor.kt\`: adapter from agent actions to the local plugin registry.
- \`AgentTaskRuntime.kt\`: creates the Android tool host, registry, consent store, audit logs, and encrypted task store.
- \`AndroidAgentTaskStateStore.kt\`: persists bounded task graphs encrypted with Android Keystore AES-GCM.
- \`AgentTaskWorker.kt\`: WorkManager worker for persisted task execution; work is deferrable and uses unique work names.

Important distinction: the generic orchestrator supports bounded multi-node plans, but the visible \`AgentTaskPanel\` currently creates one fixed, two-step diagnostic plan (read device information, then battery status). It is not yet a general model-driven natural-language planner.

### \`plugins/\` — local connected tool platform

- \`PluginManifest.kt\`: typed capability/action/schema models, execution result states, access decisions, consent, and handler contracts.
- \`PluginRegistry.kt\`: closed-world registry; each manifest action must match an already compiled handler and exact argument schema.
- \`PluginToolSchemaFactory.kt\`: produces provider-neutral function schemas from connected tools only.
- \`PluginToolCallDispatcher.kt\`: parses bounded JSON arguments, enforces primitive types/field allowlists, and dispatches only current connected tools.
- \`AndroidDeviceStatusPlugin.kt\`: built-in Android plugin and its compiled handlers.
- \`AndroidPluginAccessChecker.kt\`: rechecks OS permissions and provider credentials at execution time. Its default GitHub credential checker returns unavailable; no production GitHub repository handler is present in this mainline.
- \`AndroidPluginConsentStore.kt\`: persistent per-plugin consent, disabled by default.
- \`AndroidPluginManifestStore.kt\`: persists declarative metadata separately from consent.
- \`LocalPluginPackageManager.kt\`: validates/imports/exports metadata-only packages; imports do not carry consent or code.
- \`PluginManifestArchive.kt\`: strict versioned metadata codec.
- \`FilePluginAuditLog.kt\`: bounded metadata-only app-private audit log.

Security invariant: a plugin package cannot add executable Kotlin/DEX/JavaScript/shell code, arbitrary intents, or arbitrary handlers. Imported manifests can bind only to compiled allowlisted handlers. Connection consent and runtime OS permissions/credentials are separate checks.

### \`device/\` — explicit Android actions

- \`DeviceToolEngine.kt\`: sealed typed command allowlist, per-capability user grants, confirmation rules, argument checks, rate limiting, execution budget, and fail-closed audit.
- \`AndroidDeviceToolHost.kt\`: Android API implementation for device/battery status, calendar and contacts reads, reminder/calendar insertion, alarm UI, settings screens, sharesheet, and app launch.
- \`SharedPreferencesDeviceToolAuthorization.kt\`: persistent per-capability authorization; missing grants are denied.

The plugin route has multiple gates: plugin connected, compiled action allowed, current platform access, device-tool capability grant, argument validation, and (for consequential operations) a fresh user confirmation. Do not collapse these independent controls.

### \`memory/\` — encrypted local conversations and saved notes

- \`ConversationCipher.kt\`: Android Keystore AES-256-GCM; authenticated additional data binds ciphertext to an owner ID.
- \`LocalConversationStore.kt\`: SQLite schema/migrations, encrypted message/title/memory payloads, owner scoping, retention, search/list/delete/rename, export/import, and transactional restore.

Current storage limits include 1,000 conversations, 500 messages per conversation, 1,000 memory items, 8,000 characters per message, 2,000 per memory item, and a 2 MB local JSON export ceiling. Retention choices are 0, 30, 90, or 365 days; 0 disables age-based deletion. Memory context is off by default.

### \`voice/\` and \`overlay/\`

- \`WakePhraseMatcher.kt\`: English and Cyrillic wake phrase parsing, including Miss Minutes / Мисс минутка variants and optional command text.
- \`RecognitionRetryBackoff.kt\`: bounded exponential retry delay (500 ms to 8 seconds).
- \`MissMinutesWakeWordService.kt\`: opt-in, user-started microphone foreground service using Android's on-device recognizer, persistent notification, and stop action.
- \`OverlayServicePolicy.kt\`: overlay lifecycle, localized strings, and position clamping.
- \`MissMinutesOverlayService.kt\`: user-started floating launcher only; opens the Activity and does not run the microphone, plugins, or agent tasks.

### \`backup/\` and \`ui/\`

- \`BackupArchive.kt\`: password-protected encrypted archive container; PBKDF2-HMAC-SHA256 key derivation plus AES-GCM; strict archive entry and size validation.
- \`BackupPayloadAdapters.kt\`: chat/memory, display settings, and declarative plugin manifest payloads; credentials, permission grants, and plugin consent are excluded.
- \`SettingsBackupAdapter.kt\`: strict allowlist for non-secret settings.
- \`AndroidAppSettingsStore.kt\`: local language/theme settings.
- \`MissMinutesApp.kt\`: main Compose application and application-level coordination.
- \`AgentTaskPanel.kt\` / \`AgentTaskLabels.kt\`: current task diagnostics UI and localized state labels.
- \`BackupRestorePanel.kt\`: backup category selection, plugin import review, plugin consent/uninstall, and restore confirmation UI.

## 4. Voice lifecycle — actual implementation vs target

The current implementation is more than a simple exact wake-word check, but it is not yet an always-available autonomous agent:

1. The user explicitly starts the foreground microphone service while the Activity is visible and required permissions are granted.
2. The service uses \`SpeechRecognizer.createOnDeviceSpeechRecognizer\`; it requires Android 12+ and an available on-device recognizer. No cloud fallback is used.
3. It parses either a wake phrase plus a command in one transcript, or the wake phrase followed by a second utterance within a 15-second window.
4. A captured command is written to \`PendingVoiceCommandStore\` with a 10-minute expiry. The wake service then stops.
5. When the app is resumed, unlocked, not restoring, and not already sending, \`MissMinutesApp\` submits the queued text through the normal chat path.
6. It does not execute tools while the device remains locked, does not keep a live agent session running after the captured command, and does not promise survival through Force Stop/OEM battery policy.

Privacy note for future review: the queued voice transcript currently sits briefly in app-private SharedPreferences and is not encrypted through \`ConversationCipher\`; it expires after ten minutes and is removed when acknowledged/expired. Treat this as a distinct storage boundary when revising the voice privacy design.

The intended full flow is specified in \`docs/ALWAYS_AVAILABLE_VOICE_AGENT.md\`: user-started voice session, wake+command capture, secure handoff, same AI/agent runtime as chat, automatic connected-tool discovery, bounded execution, approval/unlock gates, cancellation, state recovery, and physical-device acceptance. The intended target is not evidence of current implementation.

## 5. Security and platform constraints

- No root, arbitrary shell, unrestricted AccessibilityService, arbitrary dynamic code loading, reflection-based tool dispatch, or unrestricted Android intent executor.
- The overlay is an entry point, not a way around Android lifecycle or permission rules.
- Microphone use is opt-in, permission-gated, foreground-visible, and stoppable.
- Force Stop, Doze, OS permission revocation, microphone privacy controls, battery restrictions, and OEM policy are respected; no indefinite background execution is promised.
- DeepSeek API key: encrypted locally with Android Keystore; direct HTTPS only; omitted from backups and logs.
- Conversation/memory content: AES-GCM encrypted before SQLite persistence.
- Plugin consent, device capability grants, and OS permissions are distinct state.
- Plugin/device audit records are bounded and designed not to store conversation text or raw arguments.
- Backup archives omit API credentials, Android permissions, and plugin consent. Single-conversation restore coerces destructive REPLACE to MERGE.
- Saved memory is not sent to the model unless the user enables the setting; memory remains untrusted prompt context.

## 6. Current mainline capability truth

| Area | Present on main | Not yet proven/complete |
|---|---|---|
| Local workspace | Guest workspace and encrypted local storage | Physical-device persistence acceptance |
| Remote AI | Direct DeepSeek BYOK over HTTPS | Successful live request with owner's key |
| Local LLM | Optional runtime interface only | Runtime/model import and real local generation |
| Speech input | One-shot on-device dictation | Supported language packs on the target phone |
| TTS | Offline voice selection policy | Actual installed Tajik/Russian/English offline voices on target phone |
| Wake phrase | User-started foreground listener and pending-command queue | Locked-screen command-to-agent execution; full session state machine |
| Plugins | Local manifests, consent lifecycle, schema generation, bounded dispatcher | General third-party handlers; GitHub connector; full missing-access UX |
| Agent | Bounded DAG engine, encrypted task state, WorkManager | LLM-authored plan generation and voice/text shared autonomous execution |
| Android controls | Compiled, permissioned device tools | Broad silent control is intentionally unsupported |
| Overlay | Floating shortcut with explicit stop control | 3D animated character and autonomous overlay actions |
| Release | Debug/release build pipeline and emulator tests configured | Passing current CI, signed owner-controlled APK, checksum and real Galaxy A35 acceptance |

## 7. Verified current issues and review notes

### Confirmed main CI blocker

Latest observed Android CI run for the reviewed main commit:
[Android CI run](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44234918810)

- Secret scan (Gitleaks) passed.
- The Gradle build failed while compiling \`MissMinutesApp.kt\`.
- Two \`when\` expressions are not exhaustive after the device capability enum was expanded. The compiler reports missing cases: \`READ_CALENDAR\`, \`CREATE_REMINDER\`, \`READ_CONTACTS_SUMMARY\`, \`SET_ALARM\`, \`OPEN_APP_BY_PACKAGE\`, and \`CURRENT_TIME\` (reported around lines 2283 and 2365 in that CI checkout).
- Debug/release Kotlin compilation failed. Later dependency capture, emulator setup/instrumentation, checksum generation, and artifact upload were skipped.
- This is a real source compile error, not a GitHub Actions quota failure.

### Other verified code/documentation discrepancies

1. \`ProductIdentity.VERSION_NAME\` is \`0.2.0\`, but Gradle \`versionName\` is \`0.1.0\`.
2. The Dashboard composable exists, but the main route currently maps the Dashboard selection back to Chat. Verify intended navigation before considering Dashboard delivered.
3. The chat tool loop always dispatches with \`userConfirmed = false\`. The agent engine has a one-shot approval primitive, but the visible AgentTaskPanel only launches a fixed diagnostic task. Do not claim that model-initiated write operations are already approved and executable end-to-end.
4. The current built-in Android plugin manifest contains write-like actions (calendar reminder creation, alarm UI, app launch) with confirmation requirements. Older docs describing the shipped device plugin as entirely read-only need refresh.
5. The wake disclosure text describes an installed offline English recognition model, while the app selects \`en-US\`, \`ru-RU\`, or \`tg-TJ\` from the UI language. Actual language availability must be detected/tested on the device; translated UI does not prove recognizer language support.
6. \`AndroidDeviceStatusPlugin.create_reminder\` uses calendar ID \`1\` and explicitly notes that this may fail on some devices. Validate the actual writable calendar rather than assuming ID 1.
7. \`PluginRegistryAgentActionExecutor.execute\` accepts an idempotency key but currently suppresses/does not use it. The orchestrator avoids replay after an interrupted RUNNING state by moving it to RECOVERY_REQUIRED; do not mistake that for downstream idempotency support.
8. Some phase documents are historical. For example, P5 docs describe only the initial device capability set, while current source contains more actions; the architecture/voice documents do not fully reflect the present wake-phrase-plus-command queue. Reconcile docs to source only after confirming the intended product behavior.

These are review findings, not automatic authorization to change product behavior. Apply the owner's explicit next instruction and keep safety/platform guarantees intact.

## 8. Tests and CI

The reviewed tree contains 37 test source files: 20 JVM unit-test files and 17 Android instrumentation-test files.

Coverage areas include:
- AI request validation, DeepSeek adapter, tool loop, and memory context.
- Agent DAG ordering, approval expiry, pause/resume/cancel, deadlines, and interrupted execution recovery.
- Plugin schema, dispatcher, registry, archive/package import and consent.
- Device tool authorization, confirmation, and audit behavior.
- Keystore API-key storage, conversation persistence/restore, and encrypted backup.
- Wake phrase parsing/retry, pending command persistence, service manifest/state.
- Overlay policy, service manifest, UI labels, and Activity smoke tests.

The workflow is \`.github/workflows/android.yml\`. It runs on pushes to \`main\`, PRs targeting \`main\` or \`phase/**\`, and manual dispatch. It runs JDK 17, Android SDK 36, Gitleaks, JVM tests, debug/release builds, lint, API 35 emulator instrumentation, APK SHA-256, and artifact upload. The configured workflow is not proof that each step passed; inspect the exact run for the exact commit.

## 9. Open pull requests observed at review time

The following PRs were open drafts in the live snapshot. They are not part of main merely because they exist:

- [#50 — LiteRT-LM offline local model runtime](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/50), head \`517c772dd9c87c3fb6440d595df6a6a25b6cedac\`.
- [#51 — safe local plugin composer](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/51), based on the LiteRT-LM feature branch rather than directly on main.
- [#49 — explicit approval for consequential plugin actions](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/49).
- [#53 — wake-phrase punctuation fix](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/53).
- [#54 — Android 17 / API 37 validation against a previous main base](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/54).
- [#52 — Android 17 / API 37 validation](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/52).
- [#47 and #44 — runtime profiles](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/47) / [#44](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/44).
- [#48 — dictation session cancellation](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/48).
- [#46 — missing credential settings guidance](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/46).

Always refresh live PR state, exact head/base SHAs, CI, and overlap before any merge or cherry-pick. Never assume an old base SHA is current.

## 10. Working protocol for future changes

1. Refresh repository metadata and current target branch SHA. Do not trust this snapshot for a later task without rechecking.
2. Read the relevant source and tests at the exact target SHA; follow the complete call path rather than changing a similarly named layer.
3. Restate the requested acceptance behavior internally and implement only the owner's requested scope. Do not make unrequested product/architecture choices.
4. Preserve offline-first behavior, default-deny permissions, compiled allowlists, explicit consequential-action confirmation, and Android lifecycle restrictions.
5. Add/update regression tests at the correct JVM or instrumentation layer.
6. Run the relevant tests/build/lint locally when available, then inspect the exact-head GitHub Actions result and failing step/logs.
7. Verify the final commit SHA, diff, and branch destination. Never report a partial phase or unverified feature as complete.
8. Update this field guide when architecture, behavior, constraints, or acceptance status actually changes.

## 11. Primary source files

- [Main activity](../app/src/main/java/com/khayom1/missminutes/MainActivity.kt)
- [Main Compose app](../app/src/main/java/com/khayom1/missminutes/ui/MissMinutesApp.kt)
- [Architecture](ARCHITECTURE.md)
- [Offline-first contract](OFFLINE_FIRST_PRODUCT_CONTRACT.md)
- [Always-available voice target](ALWAYS_AVAILABLE_VOICE_AGENT.md)
- [P1–P8 roadmap](ROADMAP_P1_P8.md)
- [Release checklist](P8_RELEASE_CHECKLIST.md)
- [Threat model](P8_THREAT_MODEL.md)
