# Day 1 / Step 01 — Immutable Repository Baseline

**Repository:** [Khayom1/Miss-Minutes-Assistant](https://github.com/Khayom1/Miss-Minutes-Assistant)  
**Baseline capture window:** 2026-11-19 07:40:29Z–07:43:47Z (12:40:29–12:43:47, Asia/Dushanbe, UTC+05:00)  
**Final main-ref revalidation:** 2026-11-19T07:43:47.824Z  
**Evidence status:** Remote GitHub baseline captured. Local working-tree state is not accessible through the connected GitHub API and is explicitly left unverified.

> This document records the remote repository as observed during the capture window. The immutable baseline commit is `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab`. The audit-document commit itself is a later documentation-only commit and must not be confused with the captured baseline SHA.

## 1. Executive baseline

| Field | Verified result |
|---|---|
| Repository | `Khayom1/Miss-Minutes-Assistant` |
| Repository ID | `1457992031` |
| Visibility | Public |
| Owner | `Khayom1` |
| Authenticated GitHub login | `Khayom1` |
| Connected account permission | `admin=true`, `maintain=true`, `push=true`, `pull=true`, `triage=true` |
| Default branch | `main` |
| Captured `refs/heads/main` | `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab` |
| Main commit timestamp | `2026-11-19T07:05:15Z` |
| Parent of captured main | `4c9974b6e22e211a2fd4bef6ed0a7ad838e7e023` |
| Remote branches | 84 |
| Pull requests | 76 total: 14 open, 62 closed |
| Closed PR outcome | 36 merged, 26 closed without merge |
| Latest main Actions run | Run #696 — **failure** |
| PR #76 exact-head CI | Run #700 — **success** |
| PR #77 exact-head CI | Run #701 — **success** |
| Local clone / working tree | Not exposed; cannot verify current local branch, index, tracked modifications or untracked files |
| Source-code changes made by this step | None |
| Build/workflow/branch/PR changes made by this step | None |

The main ref was read at the beginning and re-read at the end of the capture. It remained `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab` throughout the evidence window.

## 2. Source of truth and collection method

All repository facts below were retrieved from live GitHub repository/API endpoints during the stated capture window. No remembered SHA or cached summary was used as the source of truth.

Primary sources:

- [Repository metadata](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant)
- [Main ref](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant/git/ref/heads/main)
- [All remote branches](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant/branches?per_page=100&page=1)
- [Recent main commits](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant/commits?sha=main&per_page=20)
- [Open PRs](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant/pulls?state=open&per_page=100&page=1)
- [Closed PRs](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant/pulls?state=closed&per_page=100&page=1)
- [Recursive source tree at captured main](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant/git/trees/ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab?recursive=1)
- [Actions workflow runs](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant/actions/runs?per_page=100&page=1)
- [Captured-main check runs](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant/commits/ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab/check-runs?per_page=100)

The branch listing returned 84 entries on page 1 and no additional entries on page 2. The recursive tree contained 196 entries and was not truncated.

## 3. Repository access, default branch and protection signals

- The repository is public and the connected GitHub account resolves to `Khayom1`, the repository owner.
- Repository metadata reports administrator-level access for the connected account.
- The branch-list API returned `protected=false` for `main` and for all 84 returned branch refs.
- A separate request to the branch-protection details endpoint returned HTTP 403, `Resource not accessible by integration`. Therefore, detailed branch-protection settings (required reviews, required checks, restrictions and bypass rules) could not be read. The result is not evidence that those detailed settings were absent; the branch endpoint's `protected=false` signal is the available repository-level evidence.

Sources: [Repository metadata](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant), [branch list](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant/branches?per_page=100&page=1), [branch protection endpoint](https://api.github.com/repos/Khayom1/Miss-Minutes-Assistant/branches/main/protection).

## 4. Captured main commit

**Ref:** `refs/heads/main`  
**SHA:** `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab`  
**Commit:** [`ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab`](https://github.com/Khayom1/Miss-Minutes-Assistant/commit/ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab)  
**Subject:** `fix(voice): invalidate stale dictation sessions and stop on background`  
**Author date:** `2026-11-19T07:05:15Z`  
**Committer:** GitHub, `2026-11-19T07:05:15Z`  
**Parent:** `4c9974b6e22e211a2fd4bef6ed0a7ad838e7e023`

This is the baseline against which the remainder of this report classifies the remote state. PR #74 merged into this commit at 07:05:15Z; the current main ref revalidation returned the same SHA.

## 5. Recent main history — last 20 commits

The following records are the most recent 20 commits returned by the live commits API, ordered newest first.

| # | Commit SHA | Committer date (UTC) | Subject |
|---:|---|---|---|
| 1 | `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab` | 2026-11-19 07:05:15 | fix(voice): invalidate stale dictation sessions and stop on background |
| 2 | `4c9974b6e22e211a2fd4bef6ed0a7ad838e7e023` | 2026-11-19 06:54:59 | test(security): verify Android Keystore conversation encryption |
| 3 | `b9914dd0890dd068fd011e480ee2144db34cc64f` | 2026-11-19 06:47:55 | fix(ai): reject duplicate side effects without blocking repeated reads |
| 4 | `4df25ebd68cdf696e3805e4fa3f8c96e438d86df` | 2026-11-19 06:36:33 | fix(p0): close remaining agent, privacy and lifecycle safety gaps |
| 5 | `b88664b18d1ed2634576a1e8ad25af6a0b1ff20e` | 2026-11-19 06:11:01 | fix(ui): prevent chat list disposal race during navigation |
| 6 | `efe685b352afd2775fcb08d002b9e6dfa6264910` | 2026-11-19 06:10:50 | fix(accessibility): harden automation timeouts and credential handling |
| 7 | `f79c1c13543e94c78b42b46598d4412c81963458` | 2026-11-19 05:59:17 | fix(agent): make active task cancellation interruptible |
| 8 | `0bd2ad6f76e52f92654774ad415c004438341f60` | 2026-11-19 05:48:33 | test(plugins): verify built-in manifests during startup |
| 9 | `34f4e93ffa0840379104b94848fcd4e7644cdf44` | 2026-11-18 16:10:59 | fix(calendar): select writable calendars and rollback incomplete reminders |
| 10 | `be2c42a20b2e376576ed921665f9d8af5115010a` | 2026-11-18 16:00:46 | ci: prioritize exact latest commit validation per ref |
| 11 | `2087163f2198bf3d4724cb55da1841e1ece3f688` | 2026-11-18 15:57:26 | ci(security): lock resolved dependencies and scan with OSV |
| 12 | `f303d321b682ac615d4e7075fc0dae7b0fa5ef9f` | 2026-11-18 15:55:32 | test(android): repair settings, SMS manifest and approval UI assertions |
| 13 | `76c65488079567ff2555e8a0e4fb237fe5f59709` | 2026-11-18 15:53:33 | docs(p0): record evidence ledger and unresolved acceptance gates |
| 14 | `110938912ef93132ca0306948861ab10978d5897` | 2026-11-18 15:47:18 | fix(security): detect negated voice intent at token boundary |
| 15 | `331cf93e307d3d883b94a99167997a314bb0c048` | 2026-11-18 15:46:13 | test(security): restrict voice approvals to direct imperative requests |
| 16 | `a5df69b5620523bbc28bd34c469873550bd18f43` | 2026-11-18 15:44:27 | fix(agent): share UI plugin registry and gate voice approvals by intent |
| 17 | `d5196c3b4304056859b35de10a6672ace80c5570` | 2026-11-18 15:43:19 | fix(android): keep accessibility plugin manifest within validated limits |
| 18 | `b5efe5cd54807c7f0cb5c73b334b08b031a47e16` | 2026-11-18 15:39:30 | feat(security): require explicit approval for consequential AI tools |
| 19 | `0e74df8bd8320a30c7f6822e078e7d8976534dd9` | 2026-11-18 15:33:31 | test(plugins): avoid schema name shadowing in regression test |
| 20 | `23610f6fb2c3a6669ed677158de548d5e78991f4` | 2026-11-18 15:33:00 | fix(plugins): accept validated camelCase Android argument names |

Source: [main commit history](https://github.com/Khayom1/Miss-Minutes-Assistant/commits/main).

## 6. Remote branch inventory and duplicate refs

The live API returned **84 remote branches**. The complete branch-name-to-head-SHA inventory at capture time is recorded below.

| Remote branch | Head SHA |
|---|---|
| `architecture/jarvis-class-product-blueprint` | `48a06aea28b2faa3904fa3557d92c0a8a22e8346` |
| `copilot/overlay-performance-default-compact` | `4887143a56674c950012349020e5a7316593d335` |
| `delivery/mm-autonomous-2026-11-16` | `4887143a56674c950012349020e5a7316593d335` |
| `feature/ai-runtime-profiles` | `89f035b19b42acf4a63112458dbee4d23e8a2f57` |
| `feature/ai-runtime-profiles-rebased` | `1c69ef20efdbf5e2a29c9543f13224d7abacc8e6` |
| `feature/android17-ai-voice-runtime-rebased` | `9a82888b6e1853aefd2d8cb20af220c57b401252` |
| `feature/android17-api37-readiness-mainline` | `815403ed9d7b7d539c0c6cf37e3447e5ec9b4b4f` |
| `feature/android17-api37-readiness-mainline-45a230d` | `940fa1069a9c75aa9c1fbf03a36d4caa4e0790c0` |
| `feature/android17-api37-readiness-mainline-641571` | `a8e9c49ca93e5e72269e6995edfab01950bede8` |
| `feature/android17-api37-readiness-rebased` | `93808de19f1bc4e9019dff0641ac93fa73e4d3fd` |
| `feature/android17-speech-input-rebased` | `4a254a978e8ef3d8b111dd5b733fe3762ee5d353` |
| `feature/android-17-api37-readiness` | `e225f9ae5ab1b6c10e0ad61cdba3942e743c155e` |
| `feature/android-17-api37-readiness-main` | `0b7bbdeb25744726dd4d1590175cf4fa146248aa` |
| `feature/approval-flow-android17-port` | `b38d12c3e903279fdbcaf6bae4c4028ddb362c2d` |
| `feature/backup-bundle-integration` | `1f401375d47f225a8b162dbabd248c276aa0e601` |
| `feature/backup-restore-foundation` | `ac3d051eb53a9cc4723bb6e65cdab218baa0f925` |
| `feature/backup-settings-plugin-adapters` | `6ec3dc2d6bf2a91062540092d9b7db1abb37ee84` |
| `feature/conversation-backup-restore-integration` | `a6e7d5c417a20cc3434af0f7bf1eca12aaa42934` |
| `feature/explicit-plugin-tool-approvals` | `0702d1124bb4d83505e33b5b7a1089a2e58833ca` |
| `feature/local-plugin-composer` | `80189234daae1279ab0179be3a346e6ca27b3630` |
| `feature/local-plugin-package-management` | `2d3ee313d1e75c472e9233327e136e7140e87ada` |
| `feature/local-plugin-package-management-main` | `71dd6f8f50bebb7b3b463b29e73fa87c5a21c535` |
| `feature/missing-credential-settings-cta` | `beebc3c7fb3e6ddfed700f1a10ea8d333f79b9c6` |
| `feature/mm-providers-localization-elevenlabs` | `4887143a56674c950012349020e5a7316593d335` |
| `feature/offline-first-product-audit` | `bb4fb22e1b164cf19c45c56bcc3c484c7acd5cc2` |
| `feature/offline-first-remove-supabase` | `7514ac44ca262ee2b380752c88391238091ce4fd` |
| `feature/on-device-litertlm-runtime` | `517c772dd9c87c3fb6440d595df6a6a25b6cedac` |
| `feature/p8-floating-assistant-overlay` | `6252b4568c0d255b5be768ec6a8fec45b56cb82d` |
| `feature/p8-overlay-lifecycle-hardening` | `0d849709ad74e0c8661a113c176779474e6ad849` |
| `feature/p8-plugin-lifecycle-audit` | `edd6843aca4121a1e5d8a9531f8fc6a083176035` |
| `feature/p8-settings-voice-backup-hardening` | `ee16097c46d7daf61aa3f50970c5b75b12390be9` |
| `feature/plugin-autodiscovery-runtime` | `6e5c80ce76c1892c6e7c66af8d9ab6260f78ba1e` |
| `feature/plugin-persistence-and-backup-adapters` | `5769675d3a882e95c8dfad647c6743a6a9833fe9` |
| `feature/plugin-runtime-autodiscovery-rebased` | `44be4ec4fa720d056c89f146459ba1d9914122b0` |
| `feature/plugin-tool-runtime-offline` | `036946c359a64194371d85f99018954c227e0dc0` |
| `feature/remove-legacy-auth-hosted-voice` | `ade41f9fbf7c9ce5ba62fb01471c3a3017ec00a9` |
| `feature/secure-backup-archive-core` | `df5810fb0be413a798a4fce71e317f3bedd371b1` |
| `feature/settings-navigation-background-runtime` | `f6f5ba81cdeacc3012d044de5b3e2681f97a2145` |
| `feature/settings-overlay-voice-hardening` | `d07a661065b37678c4d1c871c80b8b4295e8eb93` |
| `feature/sketchfab-miss-minutes-rigged-animations` | `4887143a56674c950012349020e5a7316593d335` |
| `feature/speech-input-session-cancellation` | `e5e8ae870c2fea0f331b37d31871ba037676b6c9` |
| `feature/user-owned-deepseek-key` | `80619230db5259ab159c6ca66c61de14523482ce` |
| `fix/assistant-recognition-session-ownership` | `4fa5f926f036a94dd243a27724496ddf3e1ad421` |
| `fix/guest-entry-instrumentation-visibility` | `afc8393b9eba1b472af3b903931ce8c1abcefaec` |
| `fix/mm-overlay-security-localization` | `4887143a56674c950012349020e5a7316593d335` |
| `fix/mm-timer-and-gl-fallback` | `4887143a56674c950012349020e5a7316593d335` |
| `fix/optional-supabase-guest-mode` | `779fefc260ee6bd199c1ec238413f3e3f851f252` |
| `fix/ui-reset-dictation-state-on-resume` | `9212cc8c1aeee072c05e65d5fbc9d63d8d50f4b9` |
| `fix/voice-wake-phrase-punctuation` | `d8cc314c78cfac7aea70f102c94ff2676a2b290c` |
| `main` | `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab` |
| `p0/accessibility-timeout-cancellation` | `06e0d5225c629eca7f3921724501fd912cb391d4` |
| `p0/agent-cancellation-recovery` | `3cf98d18ee9a61878d165546d3b6d4f611074dee` |
| `p0/agent-cancellation-recovery-final` | `13056be433c23562625b3c9762128dfeefb60388` |
| `p0/agent-uncertain-outcomes` | `1481bc5fcc83772e70b53a1096041e2f6b21925f` |
| `p0/encrypt-pending-voice-command` | `ca6a453ac5d25a286d18263ec1b6e172870c1872` |
| `p0/fix-chat-list-disposal` | `069295ca1108f214e5ab414be2d03ec35c2d31a3` |
| `p0/fix-chat-list-disposal-final` | `64cb7408112d85e0b30438087a85b0fbc32aeaf2` |
| `p0/fix-chat-list-disposal-rebased` | `54e137ff20499091383bf5cafb4e35c51e3961c7` |
| `p0/fix-plugin-manifest-validation` | `64811d1c899a5d4df77e4f34b9670410d80ff62c` |
| `p0/preserve-coroutine-cancellation` | `2d3da5f07476968cf94935fc2aaeea9910f244d4` |
| `p0/prevent-duplicate-tool-side-effects` | `99c8654447323d1ff28b3965d3cfdb78549bfae1` |
| `p0/remaining-foundation-hardening` | `2fcf6c077d668798052d12b2762a2de5b3bb4dc9` |
| `p0/scope-duplicate-detection-to-side-effects` | `227028f4a57ff0296f9a321afdcf646d8715d53c` |
| `p0/speech-input-session-lifecycle-safety` | `95a003fb90ceff4473f1afd866341d95f18391ae` |
| `p0/stop-tts-on-background` | `aeba062fb45407c8ae50e38e0230979d9b674aed` |
| `p0/test-keystore-conversation-cipher` | `9654ce85760f83cc184c4e9f276ce06f276d2fb1` |
| `p0/test-keystore-conversation-cipher-rebased` | `c0400939f0ba721debb8f8d5fda1b2101bf5bea1` |
| `p0/update-compose-stable-bom` | `7e10ae779f2e828a1096b7a1b098afe9d309bd60` |
| `p2-validation-826e590` | `aff38b54230c33a4aa29644cfc8ecfc07b683bfa` |
| `p3-validation-ce173667` | `ce168c976bfcaed03a17aba70b3c9d23ae15cef6` |
| `phase/p1-chat-ui` | `0514ba4cd9fc47bdf87b738a1ad70014b92775fa` |
| `phase/p2-ai-provider` | `aff38b54230c33a4aa29644cfc8ecfc07b683bfa` |
| `phase/p2-provider-abstraction` | `1c3ea7ae03be0c48805f9ef64809fe84afb516e0` |
| `phase/p3-hybrid-voice` | `c3ba66f40f6b58120370fc9c1d8e9be47f088fd3` |
| `phase/p3-tts-only` | `1744365c4af249149b6285857f9ca9fcb0605e55` |
| `phase/p4-local-memory` | `3f38e7218f0676542f910ecb6daabb3c81fdd1d6` |
| `phase/p4-mainline` | `cb319483209ec751938b1c6c822e80259076e9d4` |
| `phase/p4-persistent-memory` | `f51f4becaa03c970db4e9a51549c76cdc00ee812` |
| `phase/p5-device-tools` | `63eb313eee037c6198dfda66c4591c1fcabdc8ac` |
| `phase/p5-mainline` | `1c6bfe7e573ca30c18f3b5399c5bf1ba8d092357` |
| `phase/p6-plugin-registry` | `384b812298d416e798e9898b3ea9911f4e3c81f2` |
| `phase/p7-bounded-agent-orchestration` | `98a323fe128c8d3375a6c9c10265e1e364cb4fdd` |
| `phase/p8-production-hardening` | `34e03cb58b938e2cde1f99c28f8b79a05a130fc0` |
| `phase/voice-validation` | `04f9ca1e870c64f1e7164d5a280f0c0895be62e2` |

### Exact duplicate head groups

1. Six refs point to the exact same commit `4887143a56674c950012349020e5a7316593d335`:
   - `copilot/overlay-performance-default-compact`
   - `delivery/mm-autonomous-2026-11-16`
   - `feature/mm-providers-localization-elevenlabs`
   - `feature/sketchfab-miss-minutes-rigged-animations`
   - `fix/mm-overlay-security-localization`
   - `fix/mm-timer-and-gl-fallback`

   Comparing `main...4887143a56674c950012349020e5a7316593d335` returned `ahead_by=0`, `behind_by=180`. These six names currently represent the same old commit, not six distinct branch tips.

2. Two refs point to the exact same commit `aff38b54230c33a4aa29644cfc8ecfc07b683bfa`:
   - `p2-validation-826e590`
   - `phase/p2-ai-provider`

   Comparing that tip with main returned `ahead_by=43`, `behind_by=180`. This is a shared, divergent commit history; it is not already contained in the captured main history.

All branch refs were read; no branch was created, deleted, moved or otherwise modified during this step.

## 7. Open pull-request ledger

The live open-PR endpoint returned **14 open PRs**. The head/base SHAs below are taken from each live PR record, not inferred from branch names.

| PR | State | Title | Head ref @ SHA | Base ref @ SHA | Exact-head CI |
|---:|---|---|---|---|---|
| [#77](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/77) | Open, ready; mergeable=true | fix(ui): reset dictation state after foreground resume | `fix/ui-reset-dictation-state-on-resume` @ `9212cc8c1aeee072c05e65d5fbc9d63d8d50f4b9` | `main` @ `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab` | **Success**, run [#701](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44478360379) |
| [#76](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/76) | Open, ready; mergeable=true | fix(voice): isolate Android Assistant recognizer sessions | `fix/assistant-recognition-session-ownership` @ `4fa5f926f036a94dd243a27724496ddf3e1ad421` | `main` @ `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab` | **Success**, run [#700](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44477918285) |
| [#75](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/75) | Open, ready | fix(privacy): stop speech playback when app backgrounds | `p0/stop-tts-on-background` @ `aeba062fb45407c8ae50e38e0230979d9b674aed` | `main` @ `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab` | **Success**, run [#697](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44477030551) |
| [#55](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/55) | Open, draft | build(android): validate Android 17 on latest mainline | `feature/android17-api37-readiness-mainline-641571` @ `a8e9c49ca93e5e72269e6995edfab01950bede8` | `main` @ `641571587211e541bd308ba94395534a7f933e6e` | No exact-head run returned |
| [#54](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/54) | Open, draft | build(android): validate Android 17 on latest main | `feature/android17-api37-readiness-mainline-45a230d` @ `940fa1069a9c75aa9c1fbf03a36d4caa4e0790c0` | `main` @ `45a230d3578515d37e50d253be2d6a2f56c44fde` | **Success**, run [#607](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44231965095) |
| [#53](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/53) | Open, draft | fix(voice): strip punctuation after Miss Minutes wake phrase | `fix/voice-wake-phrase-punctuation` @ `d8cc314c78cfac7aea70f102c94ff2676a2b290c` | `main` @ `e0ab7143a22064ca916ef697d8da838bdb2f2e9a` | **Success**, run [#603](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44231563582) |
| [#52](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/52) | Open, draft | build(android): validate Android 17 API 37 on current main | `feature/android17-api37-readiness-mainline` @ `815403ed9d7b7d539c0c6cf37e3447e5ec9b4b4f` | `main` @ `e0ab7143a22064ca916ef697d8da838bdb2f2e9a` | **Failure**, run [#604](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44231580895) |
| [#51](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/51) | Open, draft | feat(plugins): add safe local plugin composer | `feature/local-plugin-composer` @ `80189234daae1279ab0179be3a346e6ca27b3630` | `feature/on-device-litertlm-runtime` @ `c8bb9f42dd5240d9b9dc91aa8ff60d37ff84747a` | No exact-head run returned |
| [#50](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/50) | Open, draft | feat(ai): add offline local model import and LiteRT-LM runtime | `feature/on-device-litertlm-runtime` @ `517c772dd9c87c3fb6440d595df6a6a25b6cedac` | `main` @ `03dee4d7b89917720e3be61239bc2e9a2038492b` | No exact-head run returned |
| [#49](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/49) | Open, draft | feat(ai): require explicit approval for consequential plugin actions | `feature/explicit-plugin-tool-approvals` @ `0702d1124bb4d83505e33b5b7a1089a2e58833ca` | `main` @ `d9347134def2284da1c78c5e80312e88b788deef` | **Failure**, run [#605](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44231637059) |
| [#47](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/47) | Open, draft | feat(ai): integrate runtime profiles and credential recovery | `feature/ai-runtime-profiles-rebased` @ `1c69ef20efdbf5e2a29c9543f13224d7abacc8e6` | `main` @ `d9347134def2284da1c78c5e80312e88b788deef` | **Failure**, run [#576](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44228839005) |
| [#46](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/46) | Open, draft | feat(settings): guide users to missing AI credentials | `feature/missing-credential-settings-cta` @ `beebc3c7fb3e6ddfed700f1a10ea8d333f79b9c6` | `main` @ `d9347134def2284da1c78c5e80312e88b788deef` | **Failure**, run [#511](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44203469055) |
| [#45](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/45) | Open, draft | build(android): validate Android 17 on API 37 | `feature/android17-api37-readiness-rebased` @ `93808de19f1bc4e9019dff0641ac93fa73e4d3fd` | `main` @ `03dee4d7b89917720e3be61239bc2e9a2038492b` | **Success**, run [#575](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44228833970) |
| [#44](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/44) | Open, draft | feat(ai): add capability-aware runtime profiles | `feature/ai-runtime-profiles` @ `89f035b19b42acf4a63112458dbee4d23e8a2f57` | `main` @ `7c14e600b95a000fd21a0ebaaf7fcebdab189e8b` | No exact-head run returned |

**Open-PR CI tally:** 6 exact-head successes, 4 exact-head failures, 4 head SHAs with no workflow run returned by the Actions API query. “No run returned” is not represented as success or failure.

### Required review of PR #76 and #77

- **PR #76:** state=open, merged=false, draft=false, mergeable=true; head `4fa5f926f036a94dd243a27724496ddf3e1ad421`; base `main` at `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab`; 2 commits, 3 changed files, +238/-41; exact-head run #700 succeeded.
- **PR #77:** state=open, merged=false, draft=false, mergeable=true; head `9212cc8c1aeee072c05e65d5fbc9d63d8d50f4b9`; base `main` at `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab`; 1 commit, 3 changed files, +37/-17; exact-head run #701 succeeded.

Neither PR is merged. Their successful PR-triggered runs do not override or repair the failed check on the captured main commit.

## 8. Closed PR outcomes: merged versus closed without merge

The closed-PR endpoint returned 62 records: **36 merged** and **26 closed without merge**. “Closed without merge” is the verified GitHub state; it does not, by itself, establish the author's intent or prove that the work was discarded.

### Merged PRs

| PR | Merged change | Merged at (UTC) |
|---:|---|---|
| [#74](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/74) | Invalidate stale dictation sessions and stop on background | 2026-11-19 07:05:15 |
| [#73](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/73) | Verify Android Keystore conversation encryption (rebased branch) | 2026-11-19 06:55:00 |
| [#71](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/71) | Scope duplicate detection to side effects | 2026-11-19 06:47:56 |
| [#70](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/70) | Remaining agent, privacy and lifecycle safety hardening | 2026-11-19 06:36:33 |
| [#65](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/65) | Chat-list disposal race fix (rebased branch) | 2026-11-19 06:11:01 |
| [#63](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/63) | Accessibility timeout cancellation | 2026-11-19 06:10:51 |
| [#61](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/61) | Interruptible active-task cancellation (final branch) | 2026-11-19 05:59:17 |
| [#58](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/58) | Built-in plugin manifest validation | 2026-11-19 05:48:33 |
| [#42](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/42) | Local declarative plugin package management | 2026-11-17 22:43:51 |
| [#39](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/39) | Connected local tools in offline AI runtime | 2026-11-17 22:25:55 |
| [#38](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/38) | Remove legacy auth and hosted voice backend | 2026-11-17 20:40:22 |
| [#35](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/35) | Offline-first product audit / account-free workspace | 2026-11-17 19:42:11 |
| [#33](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/33) | Durable plugin revocation and uninstall | 2026-11-17 18:50:47 |
| [#32](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/32) | Floating overlay lifecycle and accessibility hardening | 2026-11-17 18:50:30 |
| [#31](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/31) | Floating assistant overlay | 2026-11-17 17:47:40 |
| [#30](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/30) | Background voice retries, task progress and backup memory | 2026-11-17 18:32:17 |
| [#29](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/29) | Background agent runtime and persisted plugin loading | 2026-11-17 18:20:05 |
| [#28](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/28) | Encrypted backup, SAF restore and plugin manifests | 2026-11-17 17:35:02 |
| [#27](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/27) | Plugin persistence and backup adapters | 2026-11-17 15:58:24 |
| [#26](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/26) | Declarative plugin backup codec | 2026-11-17 15:34:19 |
| [#25](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/25) | Conversation backup and restore | 2026-11-17 15:26:24 |
| [#24](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/24) | Password-encrypted backup archive core | 2026-11-17 15:09:43 |
| [#23](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/23) | Guest-entry instrumentation visibility | 2026-11-17 14:59:48 |
| [#21](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/21) | Optional Supabase guest mode | 2026-11-17 14:34:30 |
| [#20](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/20) | P8 production hardening and release evidence | 2026-11-17 14:00:13 |
| [#19](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/19) | P7 bounded agent orchestration core | 2026-11-17 13:30:16 |
| [#18](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/18) | P6 secure typed plugin registry | 2026-11-17 12:11:35 |
| [#17](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/17) | P5 safe Android device tools | 2026-11-17 11:53:05 |
| [#16](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/16) | P4 encrypted local conversation history and memory | 2026-11-17 11:41:26 |
| [#15](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/15) | P5 safe Android device tools (mainline) | 2026-11-17 11:34:44 |
| [#11](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/11) | P3 offline voice runtime | 2026-11-17 11:33:26 |
| [#10](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/10) | User-owned DeepSeek key stored locally | 2026-11-17 05:20:31 |
| [#5](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/5) | P1 chat UI foundation | 2026-11-16 18:02:44 |
| [#4](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/4) | Multilingual AI and lightweight overlay fixes | 2026-11-16 14:05:15 |
| [#3](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/3) | AI providers, localization, ElevenLabs and behavior | 2026-11-16 14:05:17 |
| [#2](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/2) | P0 audit, acceptance matrix and delivery checkpoint | 2026-11-16 03:56:40 |

### Closed without merge — retained remote heads

The compare values below are from `main...<recorded PR head SHA>`. Positive `ahead` means commits exist on that PR head that are not in the captured main history. These records are therefore not safe to delete or label “discarded” solely because their PR is closed.

| PR | Closed change | Recorded PR head SHA | Ahead / behind main |
|---:|---|---|---:|
| [#72](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/72) | Keystore conversation encryption (pre-rebase attempt) | `9654ce85760f83cc184c4e9f276ce06f276d2fb1` | 1 / 3 |
| [#69](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/69) | Compose BOM upgrade | `7e10ae779f2e828a1096b7a1b098afe9d309bd60` | 2 / 6 |
| [#68](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/68) | Recovery after uncertain plugin outcomes | `1481bc5fcc83772e70b53a1096041e2f6b21925f` | 2 / 6 |
| [#67](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/67) | Duplicate tool side-effect rejection | `99c8654447323d1ff28b3965d3cfdb78549bfae1` | 4 / 6 |
| [#66](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/66) | Coroutine cancellation and rollback | `2d3da5f07476968cf94935fc2aaeea9910f244d4` | 3 / 6 |
| [#64](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/64) | Encrypt pending voice-command handoff | `ca6a453ac5d25a286d18263ec1b6e172870c1872` | 6 / 7 |
| [#62](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/62) | Chat-list disposal race (final-named attempt) | `64cb7408112d85e0b30438087a85b0fbc32aeaf2` | 1 / 7 |
| [#60](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/60) | Chat-list disposal race (initial attempt) | `069295ca1108f214e5ab414be2d03ec35c2d31a3` | 1 / 8 |
| [#59](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/59) | Interruptible active-task cancellation | `3cf98d18ee9a61878d165546d3b6d4f611074dee` | 4 / 8 |
| [#56](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/56) | JARVIS-class product blueprint | `48a06aea28b2faa3904fa3557d92c0a8a22e8346` | 13 / 21 |
| [#48](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/48) | Cancel stale dictation and stop on background | `e5e8ae870c2fea0f331b37d31871ba037676b6c9` | 4 / 62 |
| [#43](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/43) | Android 17 / API 37 readiness | `0b7bbdeb25744726dd4d1590175cf4fa146248aa` | 16 / 63 |
| [#41](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/41) | Local plugin package import/export/uninstall | `2d3ee313d1e75c472e9233327e136e7140e87ada` | 12 / 81 |
| [#40](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/40) | Android 17 / API 37 readiness | `e225f9ae5ab1b6c10e0ad61cdba3942e743c155e` | 37 / 90 |
| [#37](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/37) | Offline-first guest core and connected plugin tools | `7514ac44ca262ee2b380752c88391238091ce4fd` | 68 / 129 |
| [#36](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/36) | Local plugin tools and DeepSeek runtime | `44be4ec4fa720d056c89f146459ba1d9914122b0` | 31 / 129 |
| [#34](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/34) | Persistent connections and automatic tool discovery | `6e5c80ce76c1892c6e7c66af8d9ab6260f78ba1e` | 4 / 134 |
| [#22](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/22) | Offline backup/restore and plugin architecture | `ac3d051eb53a9cc4723bb6e65cdab218baa0f925` | 1 / 145 |
| [#14](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/14) | P5 Android device tools | `63eb313eee037c6198dfda66c4591c1fcabdc8ac` | 104 / 179 |
| [#13](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/13) | P4 encrypted conversation history and memory | `f51f4becaa03c970db4e9a51549c76cdc00ee812` | 84 / 179 |
| [#12](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/12) | P4 encrypted local conversation history | `3f38e7218f0676542f910ecb6daabb3c81fdd1d6` | 95 / 179 |
| [#9](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/9) | P3 validation commit | `ce168c976bfcaed03a17aba70b3c9d23ae15cef6` | 48 / 180 |
| [#8](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/8) | P3 offline TTS and wake phrase | `1744365c4af249149b6285857f9ca9fcb0605e55` | 93 / 180 |
| [#7](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/7) | P2 response-bound validation | `aff38b54230c33a4aa29644cfc8ecfc07b683bfa` | 43 / 180 |
| [#6](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/6) | P2 authenticated AI provider gateway | `aff38b54230c33a4aa29644cfc8ecfc07b683bfa` | 43 / 180 |
| [#1](https://github.com/Khayom1/Miss-Minutes-Assistant/pull/1) | Semantic motion intent / Sketchfab animation integration | `d276799d13d2fe36f4bfaf2d0edd63f4056acd0d` | 102 / 211 |

The 26 closed-unmerged PRs contain 25 distinct recorded head SHAs because #6 and #7 share one exact SHA. Every distinct recorded head compared is ahead of main. The compare counts indicate retained commit history; they do not prove that every line of every old branch remains independently useful, because equivalent changes may have been reimplemented or merged through other PRs.

## 9. Duplicate, superseded and moved-ref observations

Verified overlap / duplication signals:

- **Exact duplicate PR tip:** PRs #6 and #7 have the identical recorded head SHA `aff38b54230c33a4aa29644cfc8ecfc07b683bfa`. Their two branch refs also point to that same SHA.
- **Exact duplicate old branch tip:** six refs listed in §6 point to `4887143a...`, which is 180 commits behind current main and has zero commits ahead.
- **Rebased/superseding sequence:** #72 and #73 have the same Keystore-encryption title; #72 is closed-unmerged and #73 is merged. Their tips differ.
- **Rebased/superseding sequence:** #60 and #62 have the same chat-list disposal title; #65 is the merged rebased variant.
- **Final/rebased cancellation sequence:** #59 is closed-unmerged while #61, with the same interruptible task-cancellation objective, is merged.
- **Duplicate side-effect scope:** #67 is closed-unmerged; #71 with the refined side-effect-only scope is merged.
- **Overlapping voice lifecycle work:** #48 is closed-unmerged; #74's stale-session invalidation/background-stop change is merged. The titles indicate overlapping scope, not byte-for-byte identity.
- **Parallel Android 17/API 37 attempts:** #40 and #43 are closed-unmerged; #45, #52, #54 and #55 remain open, draft variants of the same broad readiness effort.
- **Parallel runtime-profile work:** #44 and #47 remain open with the same title family; #47 is explicitly the rebased branch.
- **Local plugin package work:** #41 is closed-unmerged while #42's local declarative package-management change is merged. Treat this as overlapping scope pending diff-level consolidation, not proof of identical patches.

**Moved ref observation:** PR #1's historical PR record reports head SHA `d276799d13d2fe36f4bfaf2d0edd63f4056acd0d`, but the current remote branch with that name, `feature/sketchfab-miss-minutes-rigged-animations`, now points to `4887143a56674c950012349020e5a7316593d335`. This is a material ref mismatch and should be treated as a branch movement/retargeting signal. No force-push or deletion was performed by this audit.

## 10. GitHub Actions baseline

### Latest main run

- Workflow: **Android CI**
- Run: [#696](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44476916681)
- Event: `push`
- Ref: `main`
- Head SHA: `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab`
- Started: `2026-11-19T07:05:18Z`
- Completed: `2026-11-19T07:13:11Z`
- Conclusion: **failure**
- Check run: **Build, test and lint**, completed with failure.

Job-step evidence:
- Checkout, JDK 17 setup, Android SDK setup, platform installation, Gradle setup and basic source checks passed.
- Gitleaks secret scan passed.
- JVM tests, Debug/Release builds and lint passed.
- Dependency lock generation and OSV scan passed.
- API 35 emulator instrumentation failed.
- The log identifies `com.khayom1.missminutes.MainActivityTest > chatComposerCallsInjectedProviderAndRendersItsResponse` as failed; the instrumentation runner then reported a process crash and `connectedDebugAndroidTest` failed.
- APK checksum generation and release-evidence upload were skipped after the test failure.

This is a failing main baseline. The workflow is **not green** at the captured main SHA.

### Exact-head results for the two requested PRs

| PR | Exact head SHA | Workflow run | Conclusion | Result URL |
|---:|---|---:|---|---|
| #76 | `4fa5f926f036a94dd243a27724496ddf3e1ad421` | #700 | **success** | [Run 44477918285](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44477918285) |
| #77 | `9212cc8c1aeee072c05e65d5fbc9d63d8d50f4b9` | #701 | **success** | [Run 44478360379](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44478360379) |

Additional latest P0 PR: #75 exact-head run #697 also succeeded. Successful PR runs do not change the failure conclusion attached to current main.

### Exact-head CI matrix for all currently open PRs

| PR | Exact-head conclusion | Run |
|---:|---|---|
| #77 | success | [#701](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44478360379) |
| #76 | success | [#700](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44477918285) |
| #75 | success | [#697](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44477030551) |
| #55 | no exact-head run returned | — |
| #54 | success | [#607](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44231965095) |
| #53 | success | [#603](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44231563582) |
| #52 | failure | [#604](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44231580895) |
| #51 | no exact-head run returned | — |
| #50 | no exact-head run returned | — |
| #49 | failure | [#605](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44231637059) |
| #47 | failure | [#576](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44228839005) |
| #46 | failure | [#511](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44203469055) |
| #45 | success | [#575](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44228833970) |
| #44 | no exact-head run returned | — |

The commit-status endpoint for the captured main SHA returned zero legacy status contexts and a `pending` aggregate state. The separate Checks API returned one completed check run with a **failure** conclusion. The completed check-run result is the relevant evidence for the Android CI outcome; the empty legacy-status list must not be interpreted as a passing build.

Source: [main run #696](https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44476916681).

## 11. Build and CI configuration at captured main

All values below were read from files at captured main `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab`.

| File | Blob SHA | Verified configuration |
|---|---|---|
| [`app/build.gradle.kts`](https://github.com/Khayom1/Miss-Minutes-Assistant/blob/ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab/app/build.gradle.kts) | `fa8ecd854290763162abdb533ad86f716fa216d4` | Namespace/application ID `com.khayom1.missminutes`; minSdk 26; compileSdk 36; targetSdk 36; versionCode 2; versionName 0.2.0; AndroidJUnitRunner; Compose enabled; Java 17; Kotlin JVM target 17; lint aborts on error; dependency locking enabled |
| [`build.gradle.kts`](https://github.com/Khayom1/Miss-Minutes-Assistant/blob/ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab/build.gradle.kts) | `51d342ab01356af29e57fa0b7254e99e749a3f72` | Android Gradle Plugin 8.13.2; Kotlin Android/Compose/serialization plugins 2.4.21 |
| [`settings.gradle.kts`](https://github.com/Khayom1/Miss-Minutes-Assistant/blob/ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab/settings.gradle.kts) | `2a073dcd01c48905417f882eccfc14911a4d58d4` | Google, Maven Central and Gradle Plugin Portal; root project `MissMinutes`; includes `:app` |
| [`gradle.properties`](https://github.com/Khayom1/Miss-Minutes-Assistant/blob/ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab/gradle.properties) | `ce96a98f6ca371784e10e24da9db65d4d997d492` | Gradle JVM max heap 3 GB; parallel execution; AndroidX; official Kotlin style; non-transitive R classes |
| [`.github/workflows/android.yml`](https://github.com/Khayom1/Miss-Minutes-Assistant/blob/ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab/.github/workflows/android.yml) | `7efa187d1375a1f11dbd874bef9948255100ace1` | Single Android CI workflow; triggers on push to main, PRs targeting main or phase/**, and manual dispatch |

Additional build facts:
- CI runner: Ubuntu 24.04.
- CI JDK: Temurin 17.
- CI installs Android platform 36 and build-tools 36.0.0.
- CI installs Gradle 8.13 through `gradle/actions/setup-gradle@v4` and invokes `gradle` directly.
- API 35 instrumentation uses an x86_64 Google APIs emulator with emulator-runner.
- CI runs Gitleaks v8.24.2 and OSV scanner action v2.7.1.
- The workflow executes JVM tests, Debug/Release assembly and lint, dependency-lock generation, OSV scanning, API 35 connected instrumentation, APK checksums and release-evidence artifact upload.
- App dependencies directly specify Compose BOM `2026.04.01`, Activity Compose `1.12.0`, Core KTX `1.17.0`, Lifecycle `2.9.4`, WorkManager `2.10.5`, Kotlin serialization JSON `1.9.0`, coroutines Android `1.10.2`, OkHttp `4.12.0` and JUnit `4.13.2`.
- The recursive tree contains no tracked Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`) and no `gradle/libs.versions.toml` catalog.
- The recursive tree contains no tracked `app/gradle.lockfile`; the workflow generates dependency locks using `--write-locks` and then asserts that `app/gradle.lockfile` exists in the CI working directory.

## 12. Working tree, local branch and concurrent modification limits

**Exact blocker:** the connected GitHub interface exposes the remote repository, refs, commits, PRs, files and Actions data. It does not expose the user's local clone filesystem, Git index, checked-out local branch or local `git status`.

Consequently, these local-only fields are **NOT VERIFIABLE** from the available access:
- Current locally checked-out branch.
- Local HEAD versus upstream.
- Modified, staged, untracked or ignored local files.
- Local merge/rebase/cherry-pick state and reflog.
- Unsaved editor changes or other processes writing to a local clone.

No claim is made that a local working tree is clean. Remote branch refs and PR head/base refs are verified separately above. The main ref was stable during this capture, but that does not prove the absence of pushes to other branches or local concurrent work.

Additional coordination risks visible in the remote state:
- 14 open PRs remain, including parallel Android 17/API 37 and AI runtime-profile variants.
- PR #51 targets another feature branch rather than main.
- Several older PR records have base SHAs different from captured main.
- Multiple closed PR heads remain ahead of main.
- Branch API flags show no branch as protected; detailed rules could not be read due the 403 noted in §3.

No existing branch or PR was changed to resolve these risks.

## 13. Acceptance and blockers

| Acceptance item | Result |
|---|---|
| Latest main SHA fetched live and revalidated | **Pass** — `ddedf7cc1c2b2d2ff1a581fcbd5ad3d194368aab` |
| All remote branches enumerated | **Pass** — 84 refs, second page empty |
| Open and closed PR state inventory | **Pass** — 14 open, 62 closed |
| PR #76 and #77 exact head/base SHAs verified | **Pass** |
| Latest main and all open PR exact-head CI outcomes classified | **Pass** — including explicit missing-run cases |
| Recent commit history captured | **Pass** — latest 20 |
| Repository permission/default branch/build config captured | **Pass** |
| Merged, closed-unmerged, duplicate and moved-ref evidence classified | **Pass**, with unknown closure intent explicitly preserved |
| Local working tree / current local branch / uncommitted changes | **Blocked** — local clone not exposed |
| Full branch-protection rules | **Blocked** — protection endpoint returned HTTP 403 |
| Application/build/workflow/branch/PR untouched | **Pass** — no such changes made during audit |

**Baseline disposition:** the remote GitHub baseline is recorded with exact evidence and limitations. The directive's local-working-tree acceptance gate cannot be certified from this connection. The current main baseline is also not CI-green: its latest exact-head Actions run failed in API 35 instrumentation.

## 14. Scope guard

This step was read-only during evidence collection. The sole intended repository mutation is creation of this file, `docs/audit/day-1/01-baseline.md`, as a documentation-only commit. No application source, build scripts, CI workflow, existing branch head, PR metadata, PR branch or prior audit document is changed by this step.

---

*End of Day 1 / Step 01 baseline.*
