# Day 1 — Step 02: Exhaustive Repository and Module Inventory

**Repository:** `Khayom1/Miss-Minutes-Assistant`  
**Branch:** `main`  
**Pinned commit:** `ea21db7143781e7c58b54b2cd6d6ad941f4142df`  
**Audit mode:** Recursive Git tree inventory plus source/configuration inspection through the GitHub repository API.  
**Tree completeness:** GitHub recursive tree response reported `truncated: false`.

> Scope is strictly this immutable commit. The GitHub main ref was read immediately before the recursive tree request and resolved to the SHA above. All inventory rows below include the blob SHA and byte size returned by GitHub. Directories are Git tree entries and are listed separately; directory entries are not counted as files.

## 1. Completeness and classification totals

| Measure | Count |
|---|---:|
| Recursive tree entries | 201 |
| Tracked files (blobs) | 141 |
| Tracked directories (trees) | 60 |
| Kotlin production source | 70 |
| Kotlin unit tests | 33 |
| Kotlin instrumentation tests | 24 |
| Gradle Kotlin DSL files | 3 |
| Android XML files | 1+6 |
| CI workflow YAML | 1 |
| Documentation Markdown | 1 |
| Build configuration properties | 1 |
| Git configuration | 1 |
| Unclassified tracked files | 0 |

**Classification rule:** every tracked blob receives exactly one classification in the inventory table. No tracked blob is left as an unexplained exception. No tracked Java source, native binary, image, audio, video, model, archive, or other binary asset is present in the returned tree.

## 2. Complete tracked directory inventory

- `.github/`
- `.github/workflows/`
- `app/`
- `app/src/`
- `app/src/androidTest/`
- `app/src/androidTest/java/`
- `app/src/androidTest/java/com/`
- `app/src/androidTest/java/com/khayom1/`
- `app/src/androidTest/java/com/khayom1/missminutes/`
- `app/src/androidTest/java/com/khayom1/missminutes/agent/`
- `app/src/androidTest/java/com/khayom1/missminutes/ai/`
- `app/src/androidTest/java/com/khayom1/missminutes/backup/`
- `app/src/androidTest/java/com/khayom1/missminutes/device/`
- `app/src/androidTest/java/com/khayom1/missminutes/memory/`
- `app/src/androidTest/java/com/khayom1/missminutes/overlay/`
- `app/src/androidTest/java/com/khayom1/missminutes/plugins/`
- `app/src/androidTest/java/com/khayom1/missminutes/ui/`
- `app/src/main/`
- `app/src/main/java/`
- `app/src/main/java/com/`
- `app/src/main/java/com/khayom1/`
- `app/src/main/java/com/khayom1/missminutes/`
- `app/src/main/java/com/khayom1/missminutes/accessibility/`
- `app/src/main/java/com/khayom1/missminutes/agent/`
- `app/src/main/java/com/khayom1/missminutes/ai/`
- `app/src/main/java/com/khayom1/missminutes/assistant/`
- `app/src/main/java/com/khayom1/missminutes/backup/`
- `app/src/main/java/com/khayom1/missminutes/communication/`
- `app/src/main/java/com/khayom1/missminutes/communication/sms/`
- `app/src/main/java/com/khayom1/missminutes/device/`
- `app/src/main/java/com/khayom1/missminutes/memory/`
- `app/src/main/java/com/khayom1/missminutes/overlay/`
- `app/src/main/java/com/khayom1/missminutes/plugins/`
- `app/src/main/java/com/khayom1/missminutes/ui/`
- `app/src/main/java/com/khayom1/missminutes/voice/`
- `app/src/main/res/`
- `app/src/main/res/values-ru/`
- `app/src/main/res/values-tg/`
- `app/src/main/res/values/`
- `app/src/main/res/xml/`
- `app/src/test/`
- `app/src/test/java/`
- `app/src/test/java/com/`
- `app/src/test/java/com/khayom1/`
- `app/src/test/java/com/khayom1/missminutes/`
- `app/src/test/java/com/khayom1/missminutes/accessibility/`
- `app/src/test/java/com/khayom1/missminutes/agent/`
- `app/src/test/java/com/khayom1/missminutes/ai/`
- `app/src/test/java/com/khayom1/missminutes/assistant/`
- `app/src/test/java/com/khayom1/missminutes/backup/`
- `app/src/test/java/com/khayom1/missminutes/communication/`
- `app/src/test/java/com/khayom1/missminutes/communication/sms/`
- `app/src/test/java/com/khayom1/missminutes/device/`
- `app/src/test/java/com/khayom1/missminutes/overlay/`
- `app/src/test/java/com/khayom1/missminutes/plugins/`
- `app/src/test/java/com/khayom1/missminutes/ui/`
- `app/src/test/java/com/khayom1/missminutes/voice/`
- `docs/`
- `docs/audit/`
- `docs/audit/day-1/`

The recursive listing includes all nested source sets, resource directories, test packages, workflow directories, and documentation directories present at the pinned commit. No additional Gradle module directory or nested source-set root appears in the tree.

## 3. Complete tracked file inventory

| Path | Classification | Bytes | Blob SHA |
|---|---|---:|---|
| `.github/workflows/android.yml` | CI workflow YAML | 3,682 | `7efa187d1375a1f11dbd874bef9948255100ace1` |
| `.gitignore` | Git configuration | 87 | `75592c667efc8cc1defa9c2216c7ca3df42dc7f3` |
| `app/build.gradle.kts` | Gradle Kotlin DSL | 2,391 | `fa8ecd854290763162abdb533ad86f716fa216d4` |
| `app/src/androidTest/java/com/khayom1/missminutes/AndroidTtsLifecycleTest.kt` | Kotlin instrumentation test | 1,169 | `dcfb206943d36ff4a1598a97419f4747a8c1bb5c` |
| `app/src/androidTest/java/com/khayom1/missminutes/DeepSeekApiKeyStoreTest.kt` | Kotlin instrumentation test | 1,146 | `d499f1e93e45ce49337523278dbc3f7a892e1c78` |
| `app/src/androidTest/java/com/khayom1/missminutes/MainActivitySmokeTest.kt` | Kotlin instrumentation test | 831 | `0d757a6888bbf35292a780696be0f1cf83118bbf` |
| `app/src/androidTest/java/com/khayom1/missminutes/MainActivityTest.kt` | Kotlin instrumentation test | 19,500 | `ca62ea49eab731eaad0d74eedc201ba2b352333d` |
| `app/src/androidTest/java/com/khayom1/missminutes/PendingDangerousActionStoreTest.kt` | Kotlin instrumentation test | 4,088 | `820e10ab4f522cae7c4487517a17a8de27976737` |
| `app/src/androidTest/java/com/khayom1/missminutes/PendingVoiceCommandStoreTest.kt` | Kotlin instrumentation test | 2,475 | `ad510b2fc45cdb09e55202473b2c5dc7a5b731d5` |
| `app/src/androidTest/java/com/khayom1/missminutes/SmsTriageManifestTest.kt` | Kotlin instrumentation test | 1,838 | `e7944a8340726cd36a7956872c065cdc445b5ffa` |
| `app/src/androidTest/java/com/khayom1/missminutes/SmsTriageStoreTest.kt` | Kotlin instrumentation test | 1,782 | `aa661cd6a487f8e75089e4d2dd1c6b1ce290194d` |
| `app/src/androidTest/java/com/khayom1/missminutes/VoiceInteractionManifestTest.kt` | Kotlin instrumentation test | 2,296 | `dba53299efb24ea5bc68ae5917a094cfe6036487` |
| `app/src/androidTest/java/com/khayom1/missminutes/WakeWordManifestTest.kt` | Kotlin instrumentation test | 1,895 | `2a55e7a2da5e0f3e3cd52252f9fdc756a8ac1949` |
| `app/src/androidTest/java/com/khayom1/missminutes/WakeWordServiceStateTest.kt` | Kotlin instrumentation test | 1,183 | `f61e32badaaf70448b0925b2563daafeed761728` |
| `app/src/androidTest/java/com/khayom1/missminutes/agent/AgentTaskRuntimeTest.kt` | Kotlin instrumentation test | 5,412 | `33be402300dd8af5031c359d18075d455e754460` |
| `app/src/androidTest/java/com/khayom1/missminutes/agent/AgentTaskWorkerTest.kt` | Kotlin instrumentation test | 5,758 | `8283ffa4bb71d4198853778af65ea8e6cd99d89e` |
| `app/src/androidTest/java/com/khayom1/missminutes/agent/AndroidAgentTaskStateStoreTest.kt` | Kotlin instrumentation test | 2,887 | `d6bac2d49b65a2bb3595bac5034bdd46cd09e737` |
| `app/src/androidTest/java/com/khayom1/missminutes/ai/AndroidSpeechInputProviderTest.kt` | Kotlin instrumentation test | 1,170 | `bc6f821bd7469cd326017c9e09a6b1064ff29cc8` |
| `app/src/androidTest/java/com/khayom1/missminutes/backup/AndroidAppSettingsStoreTest.kt` | Kotlin instrumentation test | 1,118 | `02d2d34be1c44553f5842b6999883ede6a1edbe4` |
| `app/src/androidTest/java/com/khayom1/missminutes/device/DeviceToolAndroidTest.kt` | Kotlin instrumentation test | 2,503 | `b25f0137291e35d38598c41eba107d8b39c5aa26` |
| `app/src/androidTest/java/com/khayom1/missminutes/memory/AndroidKeystoreConversationCipherTest.kt` | Kotlin instrumentation test | 2,665 | `69db233eff864035c99abf25deefa118102318d7` |
| `app/src/androidTest/java/com/khayom1/missminutes/memory/LocalConversationStoreTest.kt` | Kotlin instrumentation test | 14,094 | `f38d4471fe5537b628843b8c1efefb9c1f0800c1` |
| `app/src/androidTest/java/com/khayom1/missminutes/overlay/OverlayServiceManifestTest.kt` | Kotlin instrumentation test | 2,467 | `1654941221792e5cce7bd6e5c9c1d9d46f380e09` |
| `app/src/androidTest/java/com/khayom1/missminutes/plugins/AndroidPluginAccessCheckerTest.kt` | Kotlin instrumentation test | 1,588 | `e885be8677e1af278b8d322329d34bbfbf829ceb` |
| `app/src/androidTest/java/com/khayom1/missminutes/plugins/AndroidPluginConsentStoreTest.kt` | Kotlin instrumentation test | 2,903 | `0cbb637d8f223a9c7346e82c5522f8a5eb4843dc` |
| `app/src/androidTest/java/com/khayom1/missminutes/plugins/AndroidPluginManifestStoreTest.kt` | Kotlin instrumentation test | 3,250 | `293d542dac901c49f8f081d348b9559a3d5fa299` |
| `app/src/androidTest/java/com/khayom1/missminutes/ui/AiToolApprovalDialogTest.kt` | Kotlin instrumentation test | 3,574 | `7cbb833414377f02a87f1fb0efc6a2866affab2e` |
| `app/src/main/AndroidManifest.xml` | Android manifest | 5,543 | `a505b8c5e1fbcae1f379c8382c8b7955580e0aff` |
| `app/src/main/java/com/khayom1/missminutes/MainActivity.kt` | Kotlin production | 2,546 | `807e317665fb556b9a1ff4a5be212e111139776f` |
| `app/src/main/java/com/khayom1/missminutes/ProductIdentity.kt` | Kotlin production | 190 | `ca87a1576fb1f3f37c7ca63e1d48c1e0ff6c62ed` |
| `app/src/main/java/com/khayom1/missminutes/accessibility/AccessibilityAutomationService.kt` | Kotlin production | 24,081 | `9e4093ff8f5bee6ea2c2cfaf230fb24d9b0179f4` |
| `app/src/main/java/com/khayom1/missminutes/accessibility/AccessibilityInvocationGate.kt` | Kotlin production | 794 | `862b41934fc0b09043fe6164bdef8874a6e705f8` |
| `app/src/main/java/com/khayom1/missminutes/accessibility/PendingDangerousActionStore.kt` | Kotlin production | 3,852 | `74f1221aae464edff2c52455fc110d2a75a0728c` |
| `app/src/main/java/com/khayom1/missminutes/agent/AgentActionCatalogProvider.kt` | Kotlin production | 494 | `131c618081251a6fb40458cc62f7f7b4333ebde2` |
| `app/src/main/java/com/khayom1/missminutes/agent/AgentOrchestrator.kt` | Kotlin production | 23,900 | `0f65fafdb2d81a7592d34c499c1e143a3b14ecf0` |
| `app/src/main/java/com/khayom1/missminutes/agent/AgentTaskRuntime.kt` | Kotlin production | 3,277 | `c606c670a09d9c75adf252bef5060993e105a80a` |
| `app/src/main/java/com/khayom1/missminutes/agent/AgentTaskWorker.kt` | Kotlin production | 4,089 | `a9e0cffdb5950a5c4958c459146669b75d24ab76` |
| `app/src/main/java/com/khayom1/missminutes/agent/AndroidAgentTaskStateStore.kt` | Kotlin production | 9,813 | `2919b14686dbe34e446efb181f35745a32f5a46d` |
| `app/src/main/java/com/khayom1/missminutes/agent/PluginRegistryAgentActionExecutor.kt` | Kotlin production | 1,193 | `3fb6a4a3d3fda52149f186ff727c9f2cb22c61f0` |
| `app/src/main/java/com/khayom1/missminutes/ai/AiProvider.kt` | Kotlin production | 7,318 | `32a6bf77fd71c32303ae8e882ba7efe579e95d35` |
| `app/src/main/java/com/khayom1/missminutes/ai/AiToolApprovalGate.kt` | Kotlin production | 6,198 | `254d4e9ec67adf9a47d1b168959141cdd8cfacad` |
| `app/src/main/java/com/khayom1/missminutes/ai/AiToolExecutionLoop.kt` | Kotlin production | 5,115 | `331a8d8a605e415097ee96e009cf4b2d7b986c87` |
| `app/src/main/java/com/khayom1/missminutes/ai/AndroidDeepSeekApiKeyStore.kt` | Kotlin production | 4,033 | `c45c116a91bcbdebadab848ac2e057fb0bac0a17` |
| `app/src/main/java/com/khayom1/missminutes/ai/AndroidSpeechInputProvider.kt` | Kotlin production | 5,327 | `e6dd30e68ecbdd4335580be98930873427d80899` |
| `app/src/main/java/com/khayom1/missminutes/ai/AndroidTtsProvider.kt` | Kotlin production | 4,746 | `c064073adc6abf72572270c98dd1e5a324dda7d3` |
| `app/src/main/java/com/khayom1/missminutes/ai/ConversationContextBuilder.kt` | Kotlin production | 1,982 | `1bbc0ffc59119a77a1f61f6655ad9c49eadd395a` |
| `app/src/main/java/com/khayom1/missminutes/ai/DirectDeepSeekProvider.kt` | Kotlin production | 7,302 | `5a83b02fc49d83418729aff6cf51f0000575967f` |
| `app/src/main/java/com/khayom1/missminutes/ai/SpeechInputBackgroundGuard.kt` | Kotlin production | 1,104 | `9ee41c5ea85808f7feed4fc521ddfcc0cdd4e42c` |
| `app/src/main/java/com/khayom1/missminutes/ai/TtsBackgroundPlaybackGuard.kt` | Kotlin production | 724 | `8d0cd65adab7617a465601df8a37d9aee6fb71fa` |
| `app/src/main/java/com/khayom1/missminutes/assistant/AndroidAssistantRole.kt` | Kotlin production | 1,295 | `223e567ee692a9ab7a015ff89ba7b2bc8e92f296` |
| `app/src/main/java/com/khayom1/missminutes/assistant/MissMinutesRecognitionService.kt` | Kotlin production | 5,852 | `e2959be851168cf60788239af42ecbb07b687f8f` |
| `app/src/main/java/com/khayom1/missminutes/assistant/MissMinutesVoiceInteractionService.kt` | Kotlin production | 613 | `5d4518f18caa7e9f19ac40e637bfea4c51be379c` |
| `app/src/main/java/com/khayom1/missminutes/assistant/MissMinutesVoiceInteractionSessionService.kt` | Kotlin production | 2,347 | `8cc2e70b872b56de481ed4d558aaf2c59ecb8da4` |
| `app/src/main/java/com/khayom1/missminutes/assistant/RecognitionSession.kt` | Kotlin production | 1,314 | `7ef95baec626e6c311f79d28930745cd1936606b` |
| `app/src/main/java/com/khayom1/missminutes/backup/AndroidAppSettingsStore.kt` | Kotlin production | 1,605 | `639ef4a2590045156be6995243a45f35abf950b0` |
| `app/src/main/java/com/khayom1/missminutes/backup/BackupArchive.kt` | Kotlin production | 11,334 | `4d5cefa51758100d68f4c2b659039ec5b8d092ae` |
| `app/src/main/java/com/khayom1/missminutes/backup/BackupPayloadAdapters.kt` | Kotlin production | 7,750 | `34c0b56cf382d50f662f5785a318dcb4adf0282f` |
| `app/src/main/java/com/khayom1/missminutes/backup/SettingsBackupAdapter.kt` | Kotlin production | 2,517 | `157995c6d5c5d78b0d4d570c3c022911c4f51f37` |
| `app/src/main/java/com/khayom1/missminutes/communication/sms/IncomingSmsReceiver.kt` | Kotlin production | 2,860 | `70d09206044754fabf4d46b2e661db524ec70d31` |
| `app/src/main/java/com/khayom1/missminutes/communication/sms/LocalSmsClassifier.kt` | Kotlin production | 6,334 | `2ab98cb6ee1048a8dab0d27cdee6e02c9701ebb6` |
| `app/src/main/java/com/khayom1/missminutes/communication/sms/SmsAccessPolicy.kt` | Kotlin production | 417 | `208e5af36df70a609b60723e4652f4ddad33523f` |
| `app/src/main/java/com/khayom1/missminutes/communication/sms/SmsInboxScanner.kt` | Kotlin production | 2,306 | `0449edc117ac9e14cfd2ea85526a6f9cfdbfface` |
| `app/src/main/java/com/khayom1/missminutes/communication/sms/SmsTriageNotification.kt` | Kotlin production | 3,468 | `ace6dab7ce47d098937cd3eeb6b446f89f550281` |
| `app/src/main/java/com/khayom1/missminutes/communication/sms/SmsTriageProcessor.kt` | Kotlin production | 1,625 | `373467114684d550701c843dc3ea1d39a3cadc3f` |
| `app/src/main/java/com/khayom1/missminutes/communication/sms/SmsTriageStore.kt` | Kotlin production | 2,947 | `83a46cd6a764680d3e2b5c123b775136dc2a7423` |
| `app/src/main/java/com/khayom1/missminutes/device/AndroidDeviceToolHost.kt` | Kotlin production | 7,143 | `d533502a5ad2e4ff6e1cfaf222469531d6f9f960` |
| `app/src/main/java/com/khayom1/missminutes/device/CalendarReminderWriter.kt` | Kotlin production | 7,640 | `b45cdd5150fbed47ba649e531ee06d2ca8d71c87` |
| `app/src/main/java/com/khayom1/missminutes/device/DeviceToolEngine.kt` | Kotlin production | 8,037 | `8c735330d3d0d400ec0f99520da2808ccd862247` |
| `app/src/main/java/com/khayom1/missminutes/device/SharedPreferencesDeviceToolAuthorization.kt` | Kotlin production | 748 | `b14e8b262f29ea9edf19a6f70cd24f089143bcf0` |
| `app/src/main/java/com/khayom1/missminutes/memory/ConversationCipher.kt` | Kotlin production | 3,833 | `45c67704bd09333c69811f37d47c0b25cdcbf4b9` |
| `app/src/main/java/com/khayom1/missminutes/memory/LocalConversationStore.kt` | Kotlin production | 32,719 | `62e9922b3f8ca78de58b73e23522f37d7b18223a` |
| `app/src/main/java/com/khayom1/missminutes/overlay/MissMinutesOverlayService.kt` | Kotlin production | 10,141 | `dc870bc50f9a2eef3ea8aa65d12e4991e4a3e600` |
| `app/src/main/java/com/khayom1/missminutes/overlay/OverlayServicePolicy.kt` | Kotlin production | 2,968 | `b2e785515055d867aff674b5bbece76364e3212a` |
| `app/src/main/java/com/khayom1/missminutes/plugins/AndroidAccessibilityAutomationPlugin.kt` | Kotlin production | 5,010 | `19132a39339fd64ac937b8162ee60335030b9cd9` |
| `app/src/main/java/com/khayom1/missminutes/plugins/AndroidDeviceStatusPlugin.kt` | Kotlin production | 8,629 | `3ac8b6147ea3c0ba3b92e14cfa9e985f5515ac69` |
| `app/src/main/java/com/khayom1/missminutes/plugins/AndroidPluginAccessChecker.kt` | Kotlin production | 5,520 | `8177adb0fedd39fcd25a6848baf593197b673925` |
| `app/src/main/java/com/khayom1/missminutes/plugins/AndroidPluginConsentStore.kt` | Kotlin production | 1,694 | `62fb7586b228cdcd60f8bbe16b0b2f9b4e1aa98d` |
| `app/src/main/java/com/khayom1/missminutes/plugins/AndroidPluginManifestStore.kt` | Kotlin production | 2,029 | `8cb1e2257becbd83e83d209296b81d993340e2e2` |
| `app/src/main/java/com/khayom1/missminutes/plugins/FilePluginAuditLog.kt` | Kotlin production | 1,300 | `c7c3ca37c9d6a050059ae0446e47dd88ca15397a` |
| `app/src/main/java/com/khayom1/missminutes/plugins/LocalPluginPackageManager.kt` | Kotlin production | 4,694 | `5c7ed2d6fc0ce04c7455f7f316751a1f01e5e275` |
| `app/src/main/java/com/khayom1/missminutes/plugins/PluginManifest.kt` | Kotlin production | 4,875 | `304c77aa1cf8cc451885251c8ac3de943080e98c` |
| `app/src/main/java/com/khayom1/missminutes/plugins/PluginManifestArchive.kt` | Kotlin production | 9,692 | `5dce5cd5fa36f7053e030fb3fb80b52081a24f24` |
| `app/src/main/java/com/khayom1/missminutes/plugins/PluginRegistry.kt` | Kotlin production | 18,764 | `19fc80529de37c1f780876f04d5171fbdbf87d16` |
| `app/src/main/java/com/khayom1/missminutes/plugins/PluginToolCallDispatcher.kt` | Kotlin production | 4,515 | `f5e40189edfb32655e088748b5621b36ff0ccb23` |
| `app/src/main/java/com/khayom1/missminutes/plugins/PluginToolSchemaFactory.kt` | Kotlin production | 3,425 | `59876238875bf04665cd056069095beb944e5575` |
| `app/src/main/java/com/khayom1/missminutes/ui/AccessibilityAutomationSettingsPanel.kt` | Kotlin production | 4,778 | `046571d36f069f5080e12e859b7e1277cf2aafb5` |
| `app/src/main/java/com/khayom1/missminutes/ui/AgentTaskLabels.kt` | Kotlin production | 3,753 | `8a84b8633e5704bf6a0d59c55c17fb7ce7d10e6e` |
| `app/src/main/java/com/khayom1/missminutes/ui/AgentTaskPanel.kt` | Kotlin production | 10,351 | `68d98ae9bcd2a995182976fbbc13c4ecb1a928aa` |
| `app/src/main/java/com/khayom1/missminutes/ui/AiToolApprovalDialog.kt` | Kotlin production | 3,565 | `4d05b67fe15836ec636b183daef572ccdfd05a36` |
| `app/src/main/java/com/khayom1/missminutes/ui/AssistantRoleSettingsPanel.kt` | Kotlin production | 3,969 | `9ad0f522c58b4505726a3f109ebf121894c13d8f` |
| `app/src/main/java/com/khayom1/missminutes/ui/BackupRestorePanel.kt` | Kotlin production | 19,027 | `bf1bbfb6f7e4053b7774e89695ebde1b0f13cbfb` |
| `app/src/main/java/com/khayom1/missminutes/ui/MissMinutesApp.kt` | Kotlin production | 150,266 | `14c97af6f5090acaef91f4b1970031e10cb0b660` |
| `app/src/main/java/com/khayom1/missminutes/ui/SmsTriageSettingsPanel.kt` | Kotlin production | 8,708 | `d06c97d4f8aade17e56fe1403a6644b155e6f654` |
| `app/src/main/java/com/khayom1/missminutes/voice/BackgroundVoiceCommandExecutor.kt` | Kotlin production | 10,706 | `84f26e2616d57ca6ba1c80d5060a17f1e203a854` |
| `app/src/main/java/com/khayom1/missminutes/voice/MissMinutesWakeWordService.kt` | Kotlin production | 24,489 | `dec3f43769a8a33c90bf1dff88a595e32bf320fc` |
| `app/src/main/java/com/khayom1/missminutes/voice/RecognitionRetryBackoff.kt` | Kotlin production | 883 | `757e7234c8fe66090b71aeee71377fd50ed34ab2` |
| `app/src/main/java/com/khayom1/missminutes/voice/VoiceActionIntentPolicy.kt` | Kotlin production | 4,454 | `b52118574f28501d03034810771e0530412968cc` |
| `app/src/main/java/com/khayom1/missminutes/voice/WakePhraseMatcher.kt` | Kotlin production | 2,119 | `398711a3d941d110e0fb69e49cffd4a96ad18c90` |
| `app/src/main/res/values-ru/strings.xml` | Android XML resource | 5,791 | `5464d57b8fa199ff4497faad0c0281d125987027` |
| `app/src/main/res/values-tg/strings.xml` | Android XML resource | 5,552 | `2cd235724c94e2554292b3a84a08ca8ca4d434c4` |
| `app/src/main/res/values/strings.xml` | Android XML resource | 4,165 | `8f9ed69754813a989ee12562062d88b895d25846` |
| `app/src/main/res/xml/miss_minutes_accessibility_service.xml` | Android XML resource | 578 | `f7c9dffd51605f253680d446a487a78c11282da7` |
| `app/src/main/res/xml/miss_minutes_recognition_service.xml` | Android XML resource | 122 | `6ce891f0fceab0d140684d597523d418b634078b` |
| `app/src/main/res/xml/miss_minutes_voice_interaction_service.xml` | Android XML resource | 408 | `d6e397dc7d9c64174afcfd76fb30eb2f93e7c11f` |
| `app/src/test/java/com/khayom1/missminutes/ProductIdentityTest.kt` | Kotlin unit test | 374 | `f26cf83948d6deb0215e4d2c55c9389b15d3e132` |
| `app/src/test/java/com/khayom1/missminutes/accessibility/AccessibilityAutomationPolicyTest.kt` | Kotlin unit test | 4,349 | `d194c4ba0fea919c51a312f565660fcd5e70c2b0` |
| `app/src/test/java/com/khayom1/missminutes/accessibility/AccessibilityInvocationGateTest.kt` | Kotlin unit test | 942 | `eb5741a0d8a536a673ce598c65518d131f1ea91e` |
| `app/src/test/java/com/khayom1/missminutes/agent/AgentOrchestratorTest.kt` | Kotlin unit test | 17,719 | `0d6ba62ad9b1565abb2045044921f3da04a907b1` |
| `app/src/test/java/com/khayom1/missminutes/ai/AiProviderTest.kt` | Kotlin unit test | 3,649 | `ebe6d8ad281e2a509e7ea9902feb5d7cf67162ab` |
| `app/src/test/java/com/khayom1/missminutes/ai/AiToolApprovalGateTest.kt` | Kotlin unit test | 6,871 | `37c96c91cf6b02a414ea10e140c9626510520191` |
| `app/src/test/java/com/khayom1/missminutes/ai/AiToolExecutionLoopTest.kt` | Kotlin unit test | 9,956 | `1c06416ca70ff04827e3c0aa4dd3b1215375c14a` |
| `app/src/test/java/com/khayom1/missminutes/ai/ConversationContextBuilderTest.kt` | Kotlin unit test | 2,450 | `cc1c1cebfa6e14dd2a2f73157e7225e6deb1214e` |
| `app/src/test/java/com/khayom1/missminutes/ai/DirectDeepSeekProviderTest.kt` | Kotlin unit test | 8,329 | `4ec91d02a5f10e518c2f01577fa976269fd3b5b3` |
| `app/src/test/java/com/khayom1/missminutes/ai/SpeechInputBackgroundGuardTest.kt` | Kotlin unit test | 3,852 | `4c17a4fcd0ff1d7dc62c2ad8aa37ea0b984f7fae` |
| `app/src/test/java/com/khayom1/missminutes/ai/SpeechInputSessionTest.kt` | Kotlin unit test | 2,520 | `4cd9807de29e099e61caac049f9cf90db716f891` |
| `app/src/test/java/com/khayom1/missminutes/ai/TtsBackgroundPlaybackGuardTest.kt` | Kotlin unit test | 1,375 | `f68c1136ff960d29c857637a903e39acb5722c8d` |
| `app/src/test/java/com/khayom1/missminutes/ai/TtsVoicePolicyTest.kt` | Kotlin unit test | 996 | `0e25c7a1fa97b241a369745ab6954e4ec1f4fcfc` |
| `app/src/test/java/com/khayom1/missminutes/assistant/RecognitionSessionTest.kt` | Kotlin unit test | 3,358 | `ee6a9a8cff55d6da58ea8ea41f4c696369ab8f1a` |
| `app/src/test/java/com/khayom1/missminutes/backup/BackupArchiveTest.kt` | Kotlin unit test | 3,305 | `bb136d9dc41d7636e3474a8b508c3fee6b756131` |
| `app/src/test/java/com/khayom1/missminutes/backup/BackupPayloadAdaptersTest.kt` | Kotlin unit test | 6,400 | `81a0e1c21cd58cba7f8e3468111af510696838fa` |
| `app/src/test/java/com/khayom1/missminutes/backup/SettingsBackupAdapterTest.kt` | Kotlin unit test | 2,053 | `b9f1f9b970cc40df7e9f9ea0d9cf509772115814` |
| `app/src/test/java/com/khayom1/missminutes/communication/sms/IncomingSmsQueueTest.kt` | Kotlin unit test | 1,482 | `2e8120cad3bc90cf993d26c6a82915e748908629` |
| `app/src/test/java/com/khayom1/missminutes/communication/sms/LocalSmsClassifierTest.kt` | Kotlin unit test | 3,454 | `3cd78fabf72fcd58cc346cadd4dc1283a5b13f06` |
| `app/src/test/java/com/khayom1/missminutes/communication/sms/SmsAccessPolicyTest.kt` | Kotlin unit test | 871 | `20adf5665fd37263e097c9132debbd27c17b4a04` |
| `app/src/test/java/com/khayom1/missminutes/device/CalendarReminderWriterTest.kt` | Kotlin unit test | 5,989 | `5cd1a258987ed27e3cd878347bdf3aeef6f89caa` |
| `app/src/test/java/com/khayom1/missminutes/device/DeviceToolEngineTest.kt` | Kotlin unit test | 6,653 | `35fb828dc3f4f4bd53ef66598de2df8ad2093bbe` |
| `app/src/test/java/com/khayom1/missminutes/overlay/OverlayServicePolicyTest.kt` | Kotlin unit test | 2,493 | `14dddde2d74ac43c86541069157d9c516c428c34` |
| `app/src/test/java/com/khayom1/missminutes/plugins/BuiltInDevicePluginManifestTest.kt` | Kotlin unit test | 3,922 | `92cc7167eb82214319dd22b6209cf98b8950bee4` |
| `app/src/test/java/com/khayom1/missminutes/plugins/LocalPluginPackageManagerTest.kt` | Kotlin unit test | 9,071 | `fbdae14073f742d06b48f44b4ebc0b3073e63e70` |
| `app/src/test/java/com/khayom1/missminutes/plugins/PluginManifestArchiveTest.kt` | Kotlin unit test | 5,073 | `4852ed2f87de1debaee99fb31d2ba72639fdb623` |
| `app/src/test/java/com/khayom1/missminutes/plugins/PluginRegistryTest.kt` | Kotlin unit test | 23,995 | `5eab6ae4deac19b8b4e92d2f407e61f242cfa089` |
| `app/src/test/java/com/khayom1/missminutes/plugins/PluginToolCallDispatcherTest.kt` | Kotlin unit test | 5,389 | `2bd550322a0af17c782f7629744292715f27816f` |
| `app/src/test/java/com/khayom1/missminutes/plugins/PluginToolSchemaFactoryTest.kt` | Kotlin unit test | 2,917 | `b06b44e1bdb16bb4a7d17bd811569cf3c9f2fcee` |
| `app/src/test/java/com/khayom1/missminutes/ui/AgentTaskLabelsTest.kt` | Kotlin unit test | 1,475 | `b2d920583d3be184467e2cecdd79d5ee365829bf` |
| `app/src/test/java/com/khayom1/missminutes/voice/RecognitionRetryBackoffTest.kt` | Kotlin unit test | 1,622 | `cb98fa486e3b53eca8b7f1a80c492d068a635a52` |
| `app/src/test/java/com/khayom1/missminutes/voice/VoiceActionIntentPolicyTest.kt` | Kotlin unit test | 3,448 | `a987edd5bdcce6b45583f9fb153cc83cb076d7a2` |
| `app/src/test/java/com/khayom1/missminutes/voice/WakePhraseMatcherTest.kt` | Kotlin unit test | 2,907 | `b28be9edd20c035f2a581d1dc5d0701d573e0e15` |
| `build.gradle.kts` | Gradle Kotlin DSL | 299 | `51d342ab01356af29e57fa0b7254e99e749a3f72` |
| `docs/audit/day-1/01-baseline.md` | Documentation | 11,848 | `85230b889352f79b1eb25f37842b4c64ecf364c5` |
| `gradle.properties` | Build configuration | 158 | `ce96a98f6ca371784e10e24da9db65d4d997d492` |
| `settings.gradle.kts` | Gradle Kotlin DSL | 331 | `2a073dcd01c48905417f882eccfc14911a4d58d4` |

## 4. Gradle modules, source sets, and package architecture

### Gradle module graph

- Root project: `MissMinutes` (`settings.gradle.kts`).
- Included modules: **`:app` only**.
- `:app` applies the Android application plugin, Kotlin Android, Kotlin Compose, and Kotlin serialization plugins.
- No included library, feature, benchmark, dynamic-feature, or build-logic module exists in this tree.
- No Java source file exists; production and test code are Kotlin.
- No checked-in Gradle wrapper files are present (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties` are absent).

### Android source sets

- `app/src/main`: application Kotlin, AndroidManifest, and Android resources.
- `app/src/test`: JVM/local unit tests.
- `app/src/androidTest`: Android instrumentation tests.
- No `src/debug`, `src/release`, flavor-specific, benchmark, or other custom source set is present.

### Package inventory

- **`MainActivity.kt`** (1 production files): `MainActivity.kt`
- **`ProductIdentity.kt`** (1 production files): `ProductIdentity.kt`
- **`accessibility`** (3 production files): `AccessibilityAutomationService.kt`, `AccessibilityInvocationGate.kt`, `PendingDangerousActionStore.kt`
- **`agent`** (6 production files): `AgentActionCatalogProvider.kt`, `AgentOrchestrator.kt`, `AgentTaskRuntime.kt`, `AgentTaskWorker.kt`, `AndroidAgentTaskStateStore.kt`, `PluginRegistryAgentActionExecutor.kt`
- **`ai`** (10 production files): `AiProvider.kt`, `AiToolApprovalGate.kt`, `AiToolExecutionLoop.kt`, `AndroidDeepSeekApiKeyStore.kt`, `AndroidSpeechInputProvider.kt`, `AndroidTtsProvider.kt`, `ConversationContextBuilder.kt`, `DirectDeepSeekProvider.kt`, `SpeechInputBackgroundGuard.kt`, `TtsBackgroundPlaybackGuard.kt`
- **`assistant`** (5 production files): `AndroidAssistantRole.kt`, `MissMinutesRecognitionService.kt`, `MissMinutesVoiceInteractionService.kt`, `MissMinutesVoiceInteractionSessionService.kt`, `RecognitionSession.kt`
- **`backup`** (4 production files): `AndroidAppSettingsStore.kt`, `BackupArchive.kt`, `BackupPayloadAdapters.kt`, `SettingsBackupAdapter.kt`
- **`communication`** (7 production files): `IncomingSmsReceiver.kt`, `LocalSmsClassifier.kt`, `SmsAccessPolicy.kt`, `SmsInboxScanner.kt`, `SmsTriageNotification.kt`, `SmsTriageProcessor.kt`, `SmsTriageStore.kt`
- **`device`** (4 production files): `AndroidDeviceToolHost.kt`, `CalendarReminderWriter.kt`, `DeviceToolEngine.kt`, `SharedPreferencesDeviceToolAuthorization.kt`
- **`memory`** (2 production files): `ConversationCipher.kt`, `LocalConversationStore.kt`
- **`overlay`** (2 production files): `MissMinutesOverlayService.kt`, `OverlayServicePolicy.kt`
- **`plugins`** (12 production files): `AndroidAccessibilityAutomationPlugin.kt`, `AndroidDeviceStatusPlugin.kt`, `AndroidPluginAccessChecker.kt`, `AndroidPluginConsentStore.kt`, `AndroidPluginManifestStore.kt`, `FilePluginAuditLog.kt`, `LocalPluginPackageManager.kt`, `PluginManifest.kt`, `PluginManifestArchive.kt`, `PluginRegistry.kt`, `PluginToolCallDispatcher.kt`, `PluginToolSchemaFactory.kt`
- **`ui`** (8 production files): `AccessibilityAutomationSettingsPanel.kt`, `AgentTaskLabels.kt`, `AgentTaskPanel.kt`, `AiToolApprovalDialog.kt`, `AssistantRoleSettingsPanel.kt`, `BackupRestorePanel.kt`, `MissMinutesApp.kt`, `SmsTriageSettingsPanel.kt`
- **`voice`** (5 production files): `BackgroundVoiceCommandExecutor.kt`, `MissMinutesWakeWordService.kt`, `RecognitionRetryBackoff.kt`, `VoiceActionIntentPolicy.kt`, `WakePhraseMatcher.kt`

Package boundaries are conventional Kotlin package boundaries inside one Android application module, not independently compiled Gradle modules. The UI package imports and composes several domain packages directly; there is no separate domain/data/feature module layer in the Gradle graph.

## 5. Entry points and Android components

### Application entry point and composition root

- `app/src/main/java/com/khayom1/missminutes/MainActivity.kt`: launcher Activity; creates the HTTP transport, DeepSeek API-key store/provider, Android TTS and speech-input providers, encrypted local conversation store, and passes them to the Compose root.
- `app/src/main/java/com/khayom1/missminutes/ui/MissMinutesApp.kt`: main Compose application/UI and major application orchestration surface. It is 150,266 bytes and imports/coordinates many feature packages; see maintainability findings.
- Manifest: `app/src/main/AndroidManifest.xml`.

### Manifest-declared components

| Component | Manifest declaration | Implementation |
|---|---|---|
| Launcher activity | `.MainActivity` | `MainActivity.kt` |
| Incoming SMS receiver | `.communication.sms.IncomingSmsReceiver` | `communication/sms/IncomingSmsReceiver.kt` |
| Voice interaction service | `.assistant.MissMinutesVoiceInteractionService` | `assistant/MissMinutesVoiceInteractionService.kt` |
| Voice interaction session service | `.assistant.MissMinutesVoiceInteractionSessionService` | `assistant/MissMinutesVoiceInteractionSessionService.kt` |
| Speech recognition service | `.assistant.MissMinutesRecognitionService` | `assistant/MissMinutesRecognitionService.kt` |
| Wake-word foreground service | `.voice.MissMinutesWakeWordService` | `voice/MissMinutesWakeWordService.kt` |
| Accessibility service | `.accessibility.AccessibilityAutomationService` | `accessibility/AccessibilityAutomationService.kt` |
| Overlay foreground service | `.overlay.MissMinutesOverlayService` | `overlay/MissMinutesOverlayService.kt` |

The manifest also declares permissions for Internet, microphone, notifications, system-alert-window overlays, foreground services, SMS, calendar, contacts, and alarm setting. SMS hardware features are declared optional. Exported state and binding permissions are defined in the manifest and must be reviewed together with the corresponding service implementation and tests.

## 6. Feature-to-implementation, test, and dependency map

This map is based on package contents, inspected class/API implementations, manifest registrations, Gradle dependencies, and test names. It records the concrete code locations; it does not imply that every end-to-end behavior has been validated on a physical device.

| Feature / subsystem | Implementation files | Tests | Principal dependencies / platform APIs |
|---|---|---|---|
| App shell, navigation, chat/dashboard/history/settings, language selection | `MainActivity.kt`; `ui/MissMinutesApp.kt`; `ui/AgentTaskPanel.kt`; `ui/AgentTaskLabels.kt`; `ui/BackupRestorePanel.kt`; `ui/AccessibilityAutomationSettingsPanel.kt`; `ui/AssistantRoleSettingsPanel.kt`; `ui/SmsTriageSettingsPanel.kt` | `MainActivityTest.kt`; `MainActivitySmokeTest.kt`; `AgentTaskLabelsTest.kt` | Jetpack Compose BOM, Activity Compose, Material 3, Lifecycle Compose |
| AI request/response contract and HTTP transport | `ai/AiProvider.kt`; `ai/DirectDeepSeekProvider.kt`; `ai/ConversationContextBuilder.kt` | `AiProviderTest.kt`; `DirectDeepSeekProviderTest.kt`; `ConversationContextBuilderTest.kt` | OkHttp 4.12.0, Kotlin serialization JSON, coroutines |
| AI tool execution and user approval | `ai/AiToolApprovalGate.kt`; `ai/AiToolExecutionLoop.kt`; `ui/AiToolApprovalDialog.kt` | `AiToolApprovalGateTest.kt`; `AiToolExecutionLoopTest.kt`; `AiToolApprovalDialogTest.kt` | Compose Material 3, serialization, coroutines |
| API-key storage | `ai/AndroidDeepSeekApiKeyStore.kt` | `DeepSeekApiKeyStoreTest.kt` | Android app storage / platform cryptographic and preference APIs (implementation-specific) |
| Speech input and TTS | `ai/AndroidSpeechInputProvider.kt`; `ai/AndroidTtsProvider.kt`; `ai/SpeechInputBackgroundGuard.kt`; `ai/TtsBackgroundPlaybackGuard.kt` | `AndroidSpeechInputProviderTest.kt`; `AndroidTtsLifecycleTest.kt`; `SpeechInputBackgroundGuardTest.kt`; `SpeechInputSessionTest.kt`; `TtsBackgroundPlaybackGuardTest.kt`; `TtsVoicePolicyTest.kt` | Android SpeechRecognizer, TextToSpeech, lifecycle/runtime APIs |
| Android assistant role and recognition | `assistant/AndroidAssistantRole.kt`; `assistant/MissMinutesRecognitionService.kt`; `assistant/MissMinutesVoiceInteractionService.kt`; `assistant/MissMinutesVoiceInteractionSessionService.kt`; `assistant/RecognitionSession.kt`; related service XML under `res/xml` | `RecognitionSessionTest.kt`; `VoiceInteractionManifestTest.kt` | Android VoiceInteractionService, RecognitionService, manifest metadata |
| Wake phrase and background voice commands | `voice/MissMinutesWakeWordService.kt`; `voice/WakePhraseMatcher.kt`; `voice/RecognitionRetryBackoff.kt`; `voice/VoiceActionIntentPolicy.kt`; `voice/BackgroundVoiceCommandExecutor.kt` | `WakePhraseMatcherTest.kt`; `RecognitionRetryBackoffTest.kt`; `VoiceActionIntentPolicyTest.kt`; `WakeWordManifestTest.kt`; `WakeWordServiceStateTest.kt`; `PendingVoiceCommandStoreTest.kt` | Android foreground microphone service, SpeechRecognizer, notifications, WorkManager/coroutines where used |
| Accessibility automation and dangerous-action gating | `accessibility/AccessibilityAutomationService.kt`; `accessibility/AccessibilityInvocationGate.kt`; `accessibility/PendingDangerousActionStore.kt`; `plugins/AndroidAccessibilityAutomationPlugin.kt`; `ui/AccessibilityAutomationSettingsPanel.kt` | `AccessibilityAutomationPolicyTest.kt`; `AccessibilityInvocationGateTest.kt`; `PendingDangerousActionStoreTest.kt`; `OverlayServiceManifestTest.kt` (separate component manifest coverage) | Android AccessibilityService, AccessibilityNodeInfo, Settings, Compose |
| Device tools and authorization | `device/AndroidDeviceToolHost.kt`; `device/DeviceToolEngine.kt`; `device/SharedPreferencesDeviceToolAuthorization.kt`; `plugins/AndroidDeviceStatusPlugin.kt` | `DeviceToolEngineTest.kt`; `DeviceToolAndroidTest.kt`; `BuiltInDevicePluginManifestTest.kt` | Android system services, permissions, preferences |
| Calendar/reminder writing | `device/CalendarReminderWriter.kt` | `CalendarReminderWriterTest.kt` | Android Calendar Provider / ContentResolver |
| Agent orchestration and persisted tasks | `agent/AgentOrchestrator.kt`; `agent/AgentActionCatalogProvider.kt`; `agent/AgentTaskRuntime.kt`; `agent/AgentTaskWorker.kt`; `agent/AndroidAgentTaskStateStore.kt`; `agent/PluginRegistryAgentActionExecutor.kt`; `ui/AgentTaskPanel.kt`; `ui/AgentTaskLabels.kt` | `AgentOrchestratorTest.kt`; `AgentTaskRuntimeTest.kt`; `AgentTaskWorkerTest.kt`; `AndroidAgentTaskStateStoreTest.kt`; `AgentTaskLabelsTest.kt` | AndroidX WorkManager, Kotlin coroutines, plugin registry |
| Plugin manifests, package manager, registry, schema and dispatch | `plugins/PluginManifest.kt`; `plugins/PluginManifestArchive.kt`; `plugins/LocalPluginPackageManager.kt`; `plugins/PluginRegistry.kt`; `plugins/PluginToolCallDispatcher.kt`; `plugins/PluginToolSchemaFactory.kt`; `plugins/AndroidPluginManifestStore.kt`; `plugins/AndroidPluginConsentStore.kt`; `plugins/AndroidPluginAccessChecker.kt`; `plugins/FilePluginAuditLog.kt` | `PluginRegistryTest.kt`; `PluginManifestArchiveTest.kt`; `LocalPluginPackageManagerTest.kt`; `PluginToolCallDispatcherTest.kt`; `PluginToolSchemaFactoryTest.kt`; `AndroidPluginAccessCheckerTest.kt`; `AndroidPluginConsentStoreTest.kt`; `AndroidPluginManifestStoreTest.kt`; `BuiltInDevicePluginManifestTest.kt` | Kotlin serialization, Android storage/permissions, AI tool schema contract |
| Local conversation memory and encryption | `memory/ConversationCipher.kt`; `memory/LocalConversationStore.kt` | `AndroidKeystoreConversationCipherTest.kt`; `LocalConversationStoreTest.kt` | Android Keystore, local persistence, serialization/crypto platform APIs |
| Backup and restore | `backup/AndroidAppSettingsStore.kt`; `backup/BackupArchive.kt`; `backup/BackupPayloadAdapters.kt`; `backup/SettingsBackupAdapter.kt`; `ui/BackupRestorePanel.kt` | `AndroidAppSettingsStoreTest.kt`; `BackupArchiveTest.kt`; `BackupPayloadAdaptersTest.kt`; `SettingsBackupAdapterTest.kt` | Android storage/URI APIs, Kotlin serialization, Compose |
| SMS triage and notification | `communication/sms/IncomingSmsReceiver.kt`; `LocalSmsClassifier.kt`; `SmsAccessPolicy.kt`; `SmsInboxScanner.kt`; `SmsTriageNotification.kt`; `SmsTriageProcessor.kt`; `SmsTriageStore.kt`; `ui/SmsTriageSettingsPanel.kt` | `IncomingSmsQueueTest.kt`; `LocalSmsClassifierTest.kt`; `SmsAccessPolicyTest.kt`; `SmsTriageManifestTest.kt`; `SmsTriageStoreTest.kt` | Android SMS broadcast/provider, notifications, permissions |
| Floating overlay | `overlay/MissMinutesOverlayService.kt`; `overlay/OverlayServicePolicy.kt` | `OverlayServicePolicyTest.kt`; `OverlayServiceManifestTest.kt` | WindowManager, TYPE_APPLICATION_OVERLAY, foreground service, notifications |
| Product identity | `ProductIdentity.kt` | `ProductIdentityTest.kt` | Kotlin/JVM only |
| Localization resources | `res/values/strings.xml`; `res/values-ru/strings.xml`; `res/values-tg/strings.xml`; UI language state/copy in `ui/MissMinutesApp.kt` | UI tests in `MainActivityTest.kt` (coverage must be checked at assertion level before claiming full locale parity) | Android resource qualifiers, Compose |
| CI, dependency and release evidence | `.github/workflows/android.yml`; root/app Gradle files; `gradle.properties` | Workflow itself runs unit, instrumentation, build, lint, Gitleaks and OSV stages | GitHub Actions, JDK 17, Gradle 8.13, Android SDK 35/36, emulator |

## 7. Dependencies and build configuration

### Plugins and toolchain

- Android application plugin: 8.13.2.
- Kotlin Android, Compose compiler plugin, serialization plugin: 2.4.21.
- Java/Kotlin bytecode target: JVM 17.
- compileSdk 36, minSdk 26, targetSdk 36, application ID/namespace `com.khayom1.missminutes`.
- Dependency locking is enabled for all configurations.
- Repository policy: Google and Maven Central; project-level repositories forbidden.

### Direct runtime dependencies declared in `app/build.gradle.kts`

- `androidx.compose:compose-bom:2026.04.01` (platform).
- `androidx.activity:activity-compose:1.12.0`.
- `androidx.core:core-ktx:1.17.0`.
- `androidx.lifecycle:lifecycle-runtime-ktx:2.9.4`.
- `androidx.lifecycle:lifecycle-runtime-compose:2.9.4`.
- `androidx.work:work-runtime-ktx:2.10.5`.
- `androidx.compose.ui:ui`.
- `androidx.compose.ui:ui-tooling-preview`.
- `androidx.compose.material3:material3`.
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0`.
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2`.
- `com.squareup.okhttp3:okhttp:4.12.0`.

### Test and debug dependencies

- JVM: `junit:junit:4.13.2`.
- Android instrumentation: Compose BOM, `ui-test-junit4`, AndroidX test JUnit 1.2.1, runner 1.6.1, core 1.6.1, rules 1.6.1, WorkManager testing 2.10.5.
- Debug: `androidx.compose.ui:ui-test-manifest`.

No SceneView, Filament, OpenGL wrapper, Vulkan engine, ARCore, model-viewer, glTF parser, or 3D rendering library is declared in the inspected app Gradle dependencies.

## 8. 3D/model/rendering audit

### Tracked assets

The full tracked tree contains no files with model/mesh/scene formats such as `.glb`, `.gltf`, `.fbx`, `.obj`, `.mtl`, `.blend`, `.dae`, `.stl`, `.usdz`, `.vrm`, `.ply`, or `.3ds`. It also contains no tracked image, texture, audio, video, font, native library, or packaged model asset.

### Source and dependency references

Repository code search was queried for `Sketchfab`, `SceneView`, `Filament`, `ModelViewer`, `gltf`, `glb`, `blend`, `3D`, and `Renderer`; the GitHub code-search API returned zero matches for each query. Direct app dependency declarations likewise contain no 3D engine or model-loading dependency.

### Conclusion

At this commit, **there is no implemented 3D character/model/rendering subsystem in the tracked repository**. There is no renderer, model asset pipeline, rig, animation controller, material/texture pipeline, or 3D-specific test package visible in the tracked tree. This is a verified absence in the inspected commit, not a claim that no untracked local assets exist outside Git.

CI also includes an explicit guard that `app/src/main/assets/models` must not exist (workflow step “Verify Kotlin source formatting basics”). This corroborates that model assets are currently intentionally absent from the committed application tree.

No 3D-related item was deleted or changed.

## 9. Duplication, dead code, resources, and architectural risk review

### Duplicated implementations

- No duplicate file paths exist in the Git tree.
- No second production source set or parallel Gradle module exists.
- No duplicate renderer/model implementation exists; none is present.
- Similar lifecycle/policy abstractions exist in different subsystems (for example, wake-word, overlay, accessibility and voice interaction); they are not classified as duplicates without semantic equivalence evidence.
- A repository-wide symbol/reference analysis is still required before declaring individual classes or resources dead. The remote tree and limited code-search results alone cannot prove that a Kotlin declaration is unreachable.

### Large files and responsibility concentration

The following tracked files warrant a focused responsibility/maintainability review. Byte sizes are exact Git blob sizes:

| File | Bytes | Reason for review |
|---|---:|---|
| `app/src/main/java/com/khayom1/missminutes/ui/MissMinutesApp.kt` | 150,266 | Very large Compose root; imports and coordinates AI, agents, device tools, plugins, memory, voice, SMS, backup, permissions and navigation. Strong candidate for decomposition into feature state holders/screens. |
| `app/src/main/java/com/khayom1/missminutes/memory/LocalConversationStore.kt` | 32,719 | Large persistence/security/state implementation; review separation of storage, migrations, encryption and conversation policy. |
| `app/src/main/java/com/khayom1/missminutes/voice/MissMinutesWakeWordService.kt` | 24,489 | Service lifecycle, speech recognition, notification, command handoff and state objects share one source file. |
| `app/src/main/java/com/khayom1/missminutes/accessibility/AccessibilityAutomationService.kt` | 24,081 | Large platform automation service; review separation of traversal, action policy, execution and lifecycle. |
| `app/src/main/java/com/khayom1/missminutes/agent/AgentOrchestrator.kt` | 23,900 | Orchestration policy and execution responsibilities are concentrated. |
| `app/src/main/java/com/khayom1/missminutes/plugins/PluginRegistry.kt` | 18,764 | Registry/consent/dispatch surface is substantial; inspect responsibility boundaries. |
| `app/src/main/java/com/khayom1/missminutes/ui/BackupRestorePanel.kt` | 19,027 | UI and backup/restore orchestration concentration. |
| `app/src/androidTest/java/com/khayom1/missminutes/MainActivityTest.kt` | 19,500 | Large integration test suite; consider test fixture/behavior grouping. |
| `app/src/test/java/com/khayom1/missminutes/plugins/PluginRegistryTest.kt` | 23,995 | Large registry test suite; review test grouping and setup duplication. |
| `app/src/test/java/com/khayom1/missminutes/agent/AgentOrchestratorTest.kt` | 17,719 | Large orchestration test suite. |

Size is a review signal, not proof of a defect. No file was split or edited during this inventory step.

### Dead/unused code and resource status

- The tree has no obvious abandoned top-level prototype directory, sample application, legacy Java source tree, or alternate renderer.
- No source file or resource is marked dead solely from its filename.
- Resource directories contain three string-resource sets (`values`, `values-ru`, `values-tg`) and three service configuration XML files. Manifest references are explicit for the service XML and app label resources.
- The locale resource sets exist, while the main UI also defines internal language/copy state in `MissMinutesApp.kt`. A complete resource-key-to-callsite and locale-parity analysis is required before asserting that every string resource is consumed or every UI string is localized.
- No image/drawable/layout/menu/raw resource directory is present. The app currently has no committed visual/model asset library.
- No native binaries or JNI/CMake/NDK source files are present.

## 10. Documentation and repository hygiene

Tracked documentation consists only of `docs/audit/day-1/01-baseline.md` at this commit. There is no root `README.md`, architecture overview, module map, feature guide, threat model, data-flow diagram, permission rationale, release guide, model/3D integration guide, or developer setup guide in the tracked tree.

The prior Step 01 document records a different main SHA (`cdb27a37b4a53e5f2208178c255f9a1ca05d9499`), whereas this Step 02 inventory is pinned to `ea21db7143781e7c58b54b2cd6d6ad941f4142df`. Treat Step 01 as a historical snapshot; do not combine its file counts or CI state with this commit without re-verification.

## 11. Audit limitations and evidence boundaries

- GitHub's recursive tree response is complete (`truncated: false`) and provides the authoritative tracked path inventory for this commit.
- Binary blobs are represented by Git tree metadata; no binary file exists in the tracked tree to inspect.
- Code search returned zero 3D-related matches, but GitHub code-search indexing availability was not independently confirmed. The absence conclusion is therefore grounded primarily in the complete tree, package/file inventory, and direct Gradle dependency declarations.
- A complete proof of dead code, unused resource keys, all Kotlin symbol references, and exact line-level class responsibility requires a local recursive source checkout and static analysis. Such conclusions are not fabricated here.
- No application source, build configuration, workflow, existing branch, or prior audit artifact was modified by the audit process. The only intended write is this new documentation file.

## 12. Step 02 acceptance checklist

- [x] Verified current `main` HEAD and pinned the audit to its exact SHA.
- [x] Enumerated all tracked files from a non-truncated recursive Git tree.
- [x] Enumerated all tracked directories, including nested source and test packages.
- [x] Classified every tracked file by source/config/resource/test/documentation type.
- [x] Identified Gradle module graph and Android source sets.
- [x] Identified package boundaries, launcher entry point, and manifest-declared Android components.
- [x] Mapped feature implementation paths to test paths and direct dependency families.
- [x] Inspected and recorded the large-file/responsibility hotspots.
- [x] Audited tracked assets, native files, 3D references and rendering dependencies.
- [ ] Proved every Kotlin declaration and resource key is used or dead (requires full local symbol/resource analysis; not claimed).
- [ ] Executed project build/tests in a local checkout (not part of this read-only remote inventory).
- [x] Prepared a complete tracked-file inventory with byte sizes and blob SHAs.

**Step 02 status:** The recursive tracked-tree inventory and architecture/feature map are complete for the verified commit. Dead-code/resource reachability and line-by-line responsibility findings remain explicitly unproven rather than being guessed.
