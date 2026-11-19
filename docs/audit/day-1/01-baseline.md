# Day 1 — Step 01: Repository Baseline Audit

**Audit date:** 2026-11-19  
**Repository:** `Khayom1/Miss-Minutes-Assistant`  
**Default branch:** `main`  
**Audited commit:** `cdb27a37b4a53e5f2208178c255f9a1ca05d9499`  
**Audit mode:** Remote, read-only inspection. This commit adds only this audit record; application source, build configuration, workflow definitions, branches, and pull requests were not changed.

## 1. Repository identity and Git state

| Check | Observed result |
|---|---|
| Visibility | Public |
| Default branch | `main` |
| Repository archived | No |
| Repository API permissions | admin, maintain, push, pull, triage |
| Main HEAD | `cdb27a37b4a53e5f2208178c255f9a1ca05d9499` |
| Main HEAD commit time | 2026-11-19 08:18:45 UTC |
| Recursive tree | 197 entries; not truncated |
| Tracked files in tree | 140 |
| Remote branches | 84 |
| Branch objects marked protected | 0 of 84, including `main` |
| Repository rulesets API | Empty list |
| Markdown files before this audit artifact | 0 |

The main commit is unsigned according to GitHub's commit verification metadata (`verified: false`, reason: `unsigned`). This is an observation, not proof of malicious content.

A direct branch-protection read returned HTTP 403 (`Resource not accessible by integration`). Therefore, required status checks, review requirements, restrictions, and other detailed protection settings could not be independently enumerated. The branch listing marks `main` as `protected: false`, and the rulesets endpoint returned an empty list; these observations do not replace a successful detailed protection read.

A local checkout was not available in this execution environment. An attempted shallow clone failed because the environment could not resolve `github.com`. Consequently, the user's device-local working tree, staged changes, untracked files, local branches, and local Git configuration are **not assessed**. No claim of a clean local working tree is made.

## 2. Android build configuration

| Setting | Observed value |
|---|---|
| Namespace / application ID | `com.khayom1.missminutes` |
| minSdk | 26 |
| compileSdk | 36 |
| targetSdk | 36 |
| versionCode / versionName | 2 / 0.2.0 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin plugins | 2.4.21 |
| Java source / target | 17 |
| Kotlin JVM target | 17 |
| Gradle version selected in CI | 8.13 |
| Compose BOM | 2026.04.01 |
| Dependency locking | Enabled for all configurations |
| Repositories | Google Maven Central; project repositories are disallowed |

The expected `gradle/wrapper/gradle-wrapper.properties` path is absent (GitHub contents API returned 404). CI installs Gradle 8.13 through `gradle/actions/setup-gradle@v4`, rather than invoking a checked-in wrapper.

## 3. CI workflow audit

Workflow: `.github/workflows/android.yml`  
Runner: `ubuntu-24.04`  
JDK: 17  
Android SDK: platform/build-tools 36  
Instrumentation emulator: API 35, Google APIs, x86_64

The workflow runs on pushes to `main`, pull requests targeting `main` or `phase/**`, and manual dispatch. It performs:
- source-presence / model-assets guard checks;
- Gitleaks scan of the tracked working tree;
- JVM unit tests;
- debug and release APK builds;
- debug and release lint;
- runtime dependency lock generation;
- OSV dependency vulnerability scan;
- API 35 Android instrumentation tests;
- APK checksum generation and release-evidence artifact upload.

The workflow grants only `contents: read`. No workflow or permission change was made in this step.

## 4. Latest main CI evidence

**Run:** [Android CI #710](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44486550886)  
**Commit:** `cdb27a37b4a53e5f2208178c255f9a1ca05d9499`  
**Conclusion:** **FAILURE**  
**Job:** `Build, test and lint`  
**Job ID:** `135431460305`

Successful stages included checkout, JDK/SDK/Gradle setup, formatting guard, tracked-tree secret scan, JVM tests, debug/release builds, lint, dependency lock generation, OSV scan, and emulator package setup.

The API 35 instrumentation step executed 85 tests and reported 2 failures:

1. `AgentTaskWorkerTest.workerExecutesPersistedTaskAndFailsClosedWithoutPluginConsent` — expected `FAILED`, actual `WAITING_APPROVAL`.
2. `AgentTaskWorkerTest.workerReloadsImportedManifestAndStillRequiresExplicitConsent` — expected `FAILED`, actual `WAITING_APPROVAL`.

The test report shows the failures are assertion/result-state mismatches, not merely an emulator startup failure. Emulator ColorBuffer warnings also appeared, but the test suite continued and completed 85 tests. The two failed assertions are the concrete CI blocker to investigate in a later implementation/fix step. They are deliberately **not fixed here**, because Step 01 is a baseline-only audit.

## 5. Pull request and branch hygiene

At audit time:
- 84 remote branches were returned (single complete page).
- 11 pull requests were open; all 11 were drafts.
- 10 open PRs target `main`; all 10 record a base SHA older than the audited main HEAD.
- PR #51 targets `feature/on-device-litertlm-runtime`, not `main`.

Open PRs targeting main: #55, #54, #53, #52, #50, #49, #47, #46, #45, #44.  
Other base: #51 → `feature/on-device-litertlm-runtime`.

The stale base SHAs are not automatically proof of merge conflicts. They do mean each PR must be refreshed/rebased or otherwise validated against current `main` before merge decisions. No PR was retargeted, rebased, approved, closed, or merged during this audit.

Historical repository counts from the same remote audit: 65 closed PRs, of which 39 were merged and 26 were closed without merge.

## 6. Tree inventory

The audited recursive tree contains 140 files:
- 127 Kotlin source/test files;
- 7 XML files;
- 3 Gradle Kotlin build files;
- 1 GitHub Actions YAML workflow;
- 1 `.gitignore`;
- 1 properties file.

There were no Markdown files in the repository before this report was created. This report is intentionally the only Markdown artifact added by Step 01; no README or general product documentation was recreated.

## 7. Findings and handoff

### Confirmed findings

- **CI is red on current main.** Two API 35 instrumentation assertions fail with `WAITING_APPROVAL` where the tests expect `FAILED`.
- **PR base drift exists.** All 10 open PRs targeting `main` were based on older main SHAs at the time of inspection.
- **Branch protection is not fully auditable through the current integration.** Branch objects report `protected: false` and the rulesets collection is empty, but the detailed protection endpoint is denied (403).
- **Local device state is unknown.** The execution environment could not clone the repository; this report makes no assertion about the user's local working tree.
- **CI uses a remotely installed Gradle distribution.** The expected checked-in Gradle wrapper properties file is absent.

### Step 01 boundaries

This is a baseline and inventory step. No application behavior was changed, no failing test was weakened, no PR was merged, no branch was deleted, and no CI failure was hidden or reclassified as infrastructure-only.

### Required follow-up for later steps

1. Diagnose the two `AgentTaskWorkerTest` state-transition failures against the current implementation and preserve the explicit-consent fail-closed guarantee.
2. Refresh and validate the 10 main-targeting draft PRs individually; do not mass-merge.
3. Obtain an execution context with repository clone access to inspect local Git state if local-state verification is required.
4. Obtain permission to read detailed branch-protection settings, then record the exact required checks and review rules.

## 8. Audit conclusion

The remote baseline inventory is complete for the data exposed by the GitHub integration and is pinned to main SHA `cdb27a37b4a53e5f2208178c255f9a1ca05d9499`. The audit has surfaced and preserved the concrete CI and PR hygiene blockers rather than silently fixing or overlooking them.

**Step 01 status:** Baseline evidence captured. Local working-tree state and detailed branch-protection settings remain explicitly unverified due to environment/integration access limits. The application source and CI workflow remain untouched by this step.

## 9. Post-commit validation of the audit artifact

The audit-only commit `dc00d41687a6bed476174cb135ff1b48604c5eed` triggered Android CI run [#711](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44489789519). The run completed with **failure** at the same API 35 instrumentation step. All 85 tests executed; the same two `AgentTaskWorkerTest` assertions failed with expected `FAILED` versus actual `WAITING_APPROVAL`. JVM tests, debug/release builds, lint, dependency locking, and OSV scan completed successfully before the instrumentation failure.

This confirms the audit commit itself did not introduce a new compile, lint, or dependency-scan failure; however, the branch's CI status remains red. The failure is a pre-existing product/test behavior issue captured as a finding, not a condition that this read-only baseline step is authorized to fix.

**Scope completion:** The remote repository inventory, baseline evidence, CI failure capture, and audit artifact commit are complete. Device-local Git state and detailed branch-protection configuration remain outside the accessible evidence boundary and are explicitly not certified.

## 10. Final local-checkout and latest-CI verification

The user supplied the following Termux output from the local checkout:

- `git status --short --branch` → `## main...origin/main`
- `git branch --show-current` → `main`
- `git log -1 --oneline` → `30ac04d (HEAD -> main, origin/main, origin/HEAD) docs(audit): record post-commit baseline validation`

This is consistent with the GitHub `main` HEAD `30ac04df2ee92626ec72363fa16a3913fb89e95c`. The short status output contains no modified/untracked paths, and local `main` is aligned with `origin/main` at the displayed commit. This local-state evidence is user-provided terminal output, not a direct read of the device filesystem by the audit environment.

**Latest validation:** [Android CI #712](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44517128689), for commit `30ac04df2ee92626ec72363fa16a3913fb89e95c`, completed with **FAILURE**. The workflow passed setup, formatting, secret scan, JVM tests, debug/release builds, lint, dependency-lock generation, and OSV scan. API 35 instrumentation ran 85 tests and failed the same two assertions:

1. `AgentTaskWorkerTest.workerExecutesPersistedTaskAndFailsClosedWithoutPluginConsent` — expected `FAILED`, actual `WAITING_APPROVAL`.
2. `AgentTaskWorkerTest.workerReloadsImportedManifestAndStillRequiresExplicitConsent` — expected `FAILED`, actual `WAITING_APPROVAL`.

APK checksum generation and evidence upload were skipped after the instrumentation failure.

### Final Step 01 disposition

The baseline audit deliverable is complete: remote inventory, Android/CI configuration review, PR/branch hygiene snapshot, CI failure evidence through run #712, audit report commit, and user-provided local checkout synchronization/clean-status evidence are recorded. The application and CI workflow remain unchanged, as required by this baseline-only step.

Detailed branch-protection settings remain unverified because GitHub's detailed protection endpoint returns HTTP 403 to the connected integration. The branch listing reports `main` as `protected: false`, and the repository rulesets endpoint returned an empty list; this is recorded as an access limitation, not silently treated as a successful detailed protection audit.

**Step 01 completion:** Baseline audit scope complete. **Repository CI is not green**; the two confirmed instrumentation failures are handed off to the next bug-fix step. They must be corrected without weakening explicit plugin-consent enforcement.
