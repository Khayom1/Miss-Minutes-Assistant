# P0 Execution Plan

## Objective

Replace the legacy character prototype with a clean, buildable foundation for the approved Miss Minutes personal AI product.

## Ordered work

1. Capture repository baseline, tree inventory, branches, open PRs, and CI state.
2. Close obsolete PRs so the former 3D implementation cannot be merged accidentally.
3. Replace the tracked main tree with a clean Android foundation and P0 architecture/security/test documentation.
4. Reconcile all existing development branches to the new clean baseline so stale source trees are not left as active branch heads.
5. Trigger Android CI and verify unit tests, APK assembly, and lint.
6. Inspect every failing CI job and correct the foundation until all P0 gates pass.
7. Record final commit SHA, branch state, CI run, artifact, and unresolved platform limitations.
8. Mark P0 complete only after all mandatory acceptance criteria pass.

## Commit discipline

Each coherent repository change is committed directly to `main`. No uncommitted or hidden working tree is retained. The main branch is updated with a fast-forward commit. Other active branch heads are reconciled only after the clean main commit exists and with their previously observed head SHA as a lease.

## P0 exit gates

- Clean tree has no legacy 3D or character implementation.
- Android project configuration is reproducible in GitHub Actions.
- Unit tests, assembleDebug, and lintDebug pass.
- CI workflow is active and its result is recorded.
- Architecture, threat model, test strategy, risks, and acceptance criteria are documented.
- All active branch heads point to the clean baseline; obsolete PR #1 is closed.
- No provider secret is committed or embedded in the APK.

P0 does not include the user-facing ChatGPT-like product UI, provider integration, voice integration, cloud memory implementation, Android automation features, plugin builder, or autonomous agent execution. Those are subsequent gated phases.
