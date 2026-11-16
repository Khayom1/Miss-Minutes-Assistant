# P0 Acceptance Criteria

P0 is complete only when every mandatory item is verified.

- [ ] Main tree replaced with a clean, non-3D Android foundation.
- [ ] No legacy GLB/GLTF, SceneView, character animation, overlay, or old assistant runtime remains in the active branch tree.
- [ ] Obsolete PR #1 is closed.
- [ ] Every active development branch head is reconciled to the clean baseline.
- [ ] Android project builds with the pinned JDK/Gradle/AGP/Kotlin/SDK toolchain in GitHub Actions.
- [ ] Unit tests pass.
- [ ] Debug APK assembles.
- [ ] Android Lint passes with abortOnError enabled.
- [ ] CI result is inspected and linked.
- [ ] Architecture, execution plan, security model, test strategy, repository audit, and risk register are committed.
- [ ] No provider key or other production secret exists in the repository.
- [ ] README remains deferred until the first product implementation is stable.

Do not mark P0 complete while any mandatory checkbox is unchecked or any required CI result is missing.
