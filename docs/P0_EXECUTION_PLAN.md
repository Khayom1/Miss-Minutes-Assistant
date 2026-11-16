# P0 Execution Plan

## Objective

Replace the legacy character prototype with a clean, buildable foundation for the approved Miss Minutes personal AI product.

## Ordered work and outcome

1. Capture repository baseline, tracked tree inventory, branches, PRs and CI state. **Complete.** Baseline and discarded-tree boundary are recorded in `P0_REPOSITORY_AUDIT.md`.
2. Close obsolete PRs so the former 3D implementation cannot be merged accidentally. **Complete.** PR #1 closed; PRs #2–#4 are also closed.
3. Replace the tracked main tree with a clean Android foundation and P0 architecture/security/test documentation. **Complete.** The active main tree contains no model assets, SceneView dependency or legacy device-control services.
4. Reconcile pre-existing development branches to the new clean baseline. **Complete.** Six pre-existing non-main branches were reconciled to the verified main SHA using expected-head leases. The new `phase/p1-chat-ui` branch is intentionally separate P1 work in draft PR #5.
5. Trigger Android CI and verify unit tests, APK assembly and lint. **Complete.** CI #114 passed all three in the combined Gradle verification step.
6. Inspect every failing CI job and correct the foundation until all P0 gates pass. **Complete.** CI #111 and #112 exposed emulator boot/device-service failures. The workflow now enables KVM before launching the emulator and explicitly keeps Linux hardware acceleration enabled. CI #114 then passed API 35 instrumentation.
7. Record final commit SHA, branch state, CI run, artifact and unresolved platform limitations. **Complete.** See evidence below and `P0_ACCEPTANCE.md`.
8. Mark P0 complete only after all mandatory acceptance criteria pass. **Complete.** Every mandatory P0 checkbox is verified against commit `f859169b0f1648424a7d40343f4b2f259db1e757` and CI #114.

## Commit discipline

P0 changes are committed directly to `main` as coherent commits. Subsequent product phases may be developed on phase-specific branches and draft PRs, but must not merge before their predecessor phase is accepted. Branch ref updates use expected-head leases; destructive history rewrites are prohibited.

## P0 exit gates — verified

- [x] Clean main tree has no legacy 3D or character implementation.
- [x] Android project configuration is reproducible in GitHub Actions with JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21 and SDK 36.
- [x] Gitleaks tracked-working-tree scan passes.
- [x] Unit tests, `assembleDebug` and `lintDebug` pass.
- [x] API 35 emulator instrumentation passes.
- [x] CI artifact upload passes.
- [x] CI run #114 and every job/step are inspected and recorded.
- [x] P0 architecture, threat model, test strategy, risks and acceptance criteria are committed.
- [x] Six pre-existing development branches are reconciled to the P0 main baseline; obsolete PR #1 is closed.
- [x] No provider credential was detected in tracked source or in the basic APK string-pattern scan.

## Final P0 evidence

- Verified main commit: `f859169b0f1648424a7d40343f4b2f259db1e757`.
- CI run #114: https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/43921131956
- CI conclusion: `success`; all steps passed, including Gitleaks, unit/build/lint, KVM setup, API 35 instrumentation and debug APK upload.
- Artifact ID: `14255190082`; artifact name: `miss-minutes-foundation-debug`.
- APK SHA-256: `cf0be05b6076b3b20da84125d81ca870c47cc976e9023bd32776024d8aa670dc`.
- Artifact ZIP SHA-256: `646d90b634ad320269b7bd50d78c608a309a882df0f20c3e4f1af85b7531cf8d`.

## Platform limitations carried forward

- Physical Samsung Galaxy A35 5G / Android 17 validation has not been performed. Emulator results are not represented as device results.
- Provider keys, real DeepSeek/ElevenLabs calls, cloud provisioning, persistent cloud memory, Android control, plugin execution and multi-step agents are not part of P0.
- No unrestricted root or AccessibilityService-based autonomous control is permitted by this architecture.

P0 is closed for the defined foundation scope. P1 is staged independently in draft PR #5; it must pass its own build, instrumentation and review gates before merge.
