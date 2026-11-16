# Miss Minutes Assistant — Repository Audit Findings

Mission: MM-AUTONOMOUS-DELIVERY-2026-11-16
Audit date: 2026-11-16
Audited default branch: main
Main commit: 843d34b6c4b80e06b89908d0d1d03c619f59651a
PR #1 head: d276799d13d2fe36f4bfaf2d0edd63f4056acd0d

## Executive result

The current main branch builds successfully and has a real, non-expired debug APK artifact. This does not mean the full product acceptance target is met. The current implementation remains a Russian-first, DeepSeek-only assistant with incomplete conversation-context restoration and a transform-only 3D asset. The findings below separate verified functionality from unfinished requirements.

## Verified build and asset evidence

- GitHub Actions run #83: https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/43739803557 — completed successfully on main SHA 843d34b6c4b80e06b89908d0d1d03c619f59651a.
- Run #83 job steps passed: checkout, JDK 17 setup, Android SDK, Gradle 8.13 setup, model staging, GLB validator, clean unit tests + debug assembly + lint, API 35 emulator instrumentation, APK upload.
- Artifact: Miss-Minutes-Assistant-debug, artifact ID 14172547997, not expired at audit time, associated head SHA matches main, expiry 2026-11-29T17:34:22Z.
- Downloaded artifact ZIP was integrity-checked. It contains app-debug.apk (49,526,929 bytes); APK ZIP integrity check passed. Package includes AndroidManifest.xml, classes.dex, and the runtime GLB.
- Extracted runtime GLB independently parsed as glTF 2.0 binary: 120 nodes, 24 meshes, 15 materials, 0 skins, 17 animations, 0 primitives with morph targets. Animation names include Idle_Breathe, Blink, Wave_Greeting, Pointing, Talk_Loop, Laugh, Surprised, Angry_Expression, Sleep_Idle, Glitch_Recovery, Walk_Cycle, Run_Cycle, Look_Around, Peek_Around, Spin_In_Place, Nervous_Glance, Cheerful_Bounce.

## Findings

### MM-AUD-001 — AI provider is hard-coded (HIGH)

Evidence: app/src/main/java/com/khayom1/missminutes/AssistantCore.kt — DeepSeekClient.reply constructs https://api.deepseek.com/chat/completions and model deepseek-flash directly. MainActivity reads only deepseek_api_key and invokes DeepSeekClient.
Impact: users cannot create provider profiles, switch models/endpoints, or use OpenAI-compatible gateways, Gemini, or Anthropic through a provider abstraction. Provider selection is not independent of AssistantCore.
Remediation: introduce ProviderConfig, ProviderCapabilities, AiProvider, ProviderRegistry, a configurable OpenAI-compatible adapter, and separately tested Gemini/Anthropic protocol adapters. Persist only non-secret profile metadata separately from KeyStore-encrypted secrets. Route the UI through AiConversationService.
Status: FAIL in current main.

### MM-AUD-002 — Conversation history is not sent as role-based context (HIGH)

Evidence: DeepSeekClient.reply constructs a two-element messages array containing only system and the current user message. MemoryDatabase.saveConversation stores dialogue rows using savePersonalMemory with kind dialogue_user/dialogue_assistant. buildPromptContext reads only the latest eight personal_memory rows and serializes them into a system-prompt archive string. MainActivity initializes a transient Compose list with a welcome message and does not rehydrate prior turns into that list.
Impact: the model does not receive a true ordered conversation transcript with system/user/assistant roles. The current database representation conflates dialogue and personal memory. UI continuity after restart is not implemented.
Remediation: add a dedicated conversations/messages schema with stable conversation ID, role, timestamp, ordering, and migration preserving existing rows. Load bounded history on startup and submit ordered role-based messages. Keep personal memory and notification archive in separate stores.
Status: FAIL in current main.

### MM-AUD-003 — Localization is incomplete (HIGH)

Evidence: app/src/main/res/values/strings.xml contains only four resources; there are no values-ru, values-tg, or values-en resource sets in the main tree. MainActivity and AssistantCore contain extensive hard-coded Russian UI/status/error/response strings. PersonalityEngine.buildSystemPrompt has a fixed Russian-only instruction.
Impact: no effective Tajik/Russian/English UI selector, no localized errors, and no selected-language system prompt.
Remediation: move user-facing text to Android resources, add complete ru/tg/en string sets and a persisted language setting, and make the personality core language-aware without duplicating character identity.
Status: FAIL in current main.

### MM-AUD-004 — Voice routing is Russian-only (MEDIUM)

Evidence: AssistantCore.kt defines RussianVoiceController. Speech/TTS setup is fixed around Russian language/locale; RecognitionListener.onError is a no-op.
Impact: English routing and user-visible recognizer error states are not implemented. ElevenLabs adapter is absent from the current main tree.
Remediation: provide locale-aware speech controller configuration, explicit listening/speaking/error states, and a separately configured optional voice provider with Android TTS fallback. Keep all provider secrets in encrypted storage.
Status: PARTIAL for local Russian voice; FAIL for bilingual/ElevenLabs acceptance.

### MM-AUD-005 — Runtime character is not a weighted facial/body rig (HIGH)

Evidence: APK-packaged assets/models/miss-minutes.glb was independently parsed. It contains 120 nodes, 24 meshes, 15 materials and 17 animation clips, but no glTF skins and no morph-target primitives. The runtime renderer uses SceneView ModelNode and authored animation names.
Impact: node-transform clips are usable, but independent skeletal deformation, weighted finger/face rigging, phoneme-level lip sync and morph-driven facial expressions cannot be claimed.
Remediation: integrate a verifiable Blender source and exported GLB containing weighted skinning and required named controls; run the advanced validator on the exact asset staged into the APK and add a runtime scene test.
Status: FAIL for advanced rig; PARTIAL for transform-based authored animation.

### MM-AUD-006 — Sensitive local archives are not encrypted at rest (HIGH)

Evidence: MemoryDatabase writes personal_memory and notifications rows using SQLiteOpenHelper and ordinary SQLite text columns. NotificationReaderService persists notification package/title/body. Conversation messages are also stored through personal_memory. allowBackup=false reduces backup exposure but does not encrypt these SQLite records.
Impact: data remains within the Android app sandbox but is plaintext at the database layer. Notification and chat content can be sensitive.
Remediation: use SQLCipher or an application-layer authenticated-encryption design with a Keystore-wrapped database key; define retention/deletion controls and migration behavior; test tampering and upgrade paths.
Status: FAIL for encrypted archive acceptance. Existing retention caps are implemented.

### MM-AUD-007 — Network request cancellation is not proven end-to-end (MEDIUM)

Evidence: DeepSeekClient uses HttpURLConnection inside withContext(Dispatchers.IO), finite connect/read timeouts, bounded response reading, and disconnects in finally. No cancellation hook is registered to disconnect the underlying connection when the coroutine is cancelled.
Impact: coroutine cancellation may not promptly stop an in-flight blocking HTTP operation; lifecycle cancellation behavior is not fully verified.
Remediation: register invokeOnCancellation to disconnect, or migrate to a cancellation-aware HTTP client; test cancellation during connect, write, and response read.
Status: NOT_VERIFIED / improvement required.

### MM-AUD-008 — Permission model is explicit but user-device behavior remains unverified (MEDIUM)

Evidence: Manifest declares SYSTEM_ALERT_WINDOW, RECORD_AUDIO, foreground service, notification listener and accessibility service. Accessibility configuration sets canRetrieveWindowContent=false and listens only for typeWindowStateChanged. MainActivity requests overlay/microphone permissions via Android settings/runtime flows.
Impact: the app avoids screen-content retrieval in the current config, but actual overlay lifecycle, permission denial, foreground-service policy and physical-device behavior are not proven by source inspection alone.
Remediation: retain explicit opt-in and narrow permissions; execute manual tests on the target Android device, including denial, revocation, process death and screen rotation.
Status: PARTIAL; physical-device verification is NOT_VERIFIED.

### MM-AUD-009 — Autonomous semantic animation work exists only on PR #1 (MEDIUM)

Evidence: PR #1 adds MotionIntent, AutonomousBehaviorEngine, ProceduralMotionEngine and ProceduralRigController. These files are absent from current main. The PR run #79 completed successfully, but the PR is divergent and not merged.
Impact: current main does not contain the proposed autonomous priority scheduler or semantic procedural rig layer.
Remediation: review the full PR implementation against current main, port only compatible units onto this delivery branch, then run CI and real GLB node/renderer tests.
Status: FAIL in current main; PR port not yet performed.

## PR #1 audit

- PR: https://github.com/Khayom1/Miss-Minutes-Assistant/pull/1
- State at audit: open, draft, unmerged; GitHub returned mergeable=false.
- Head: d276799d13d2fe36f4bfaf2d0edd63f4056acd0d; base at PR creation: 048d4c5a68f281408ae2c50e8803345833f0ce4c.
- Compare against current main: status=diverged, ahead_by=102, behind_by=4, merge_base=048d4c5a68f281408ae2c50e8803345833f0ce4c. Direct merge is not safe.
- PR comparison reports 26 changed files. Changes add semantic motion, procedural transforms, behavior scheduling, overlay synchronization, tests, validator tooling and engineering notes. It does not introduce provider-independent AI adapters, role-based conversation history, or complete trilingual localization.
- PR run #79 reports success for PR head d276799d13d2fe36f4bfaf2d0edd63f4056acd0d. This is useful evidence for that PR snapshot only; it does not make the current divergent PR mergeable.
- PR branch tree contains assets/models/source-rig-audit.json but does not contain the named Blender or advanced GLB source binaries. The audit JSON is therefore not independently reproducible from the branch tree alone.
- The runtime GLB in PR branch and main has the same size (681,024 bytes) and the same Git blob SHA (18195a527a177c492a3b92726fb7e355d9f9c855). The procedural controller must not be represented as evidence that the APK now contains a weighted advanced character rig.
- PR workflow adds node --check for validate-advanced-rig.mjs but does not execute the advanced-rig validator against the staged runtime asset. Syntax validation is not asset acceptance.
- PR changes AndroidManifest permission from VIBRATE to com.android.alarm.permission.SET_ALARM. Main currently declares both. This is not by itself a reason to merge or reject the whole PR; assess actual feature usage and permission necessity during selective port.
- Decision: do not merge PR #1. Perform selective, file-level port only after current-main compatibility review and tests.

## Recommended execution order

1. Preserve this audit and acceptance matrix in a dedicated delivery branch.
2. Implement and test provider profiles and OpenAI-compatible adapter first; add separately gated native Gemini/Anthropic adapters only with protocol-specific tests.
3. Implement dedicated role-based conversation storage and request-context restoration.
4. Localize UI/system prompts into Tajik, Russian and English.
5. Improve voice locale/error routing; add optional ElevenLabs only behind a configured user key.
6. Port PR #1 motion/overlay work selectively; execute advanced-rig validator on the actual packaged model.
7. Encrypt local conversation/notification archives, add cancellation-aware network tests, and run the full API 35+ emulator suite.
8. Run target physical-device install, overlay, microphone, audio and performance verification; produce a signed release only when a user-owned signing key is configured.