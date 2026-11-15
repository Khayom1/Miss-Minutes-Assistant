# Miss Minutes Assistant — Engineering status

This file records verified implementation state. It is deliberately separate from the product README.

## Verified in the current branch

- Android app uses SceneView 4.18.0 to load the packaged GLB.
- The current runtime GLB passes the structural/animation validator: 120 nodes, 24 mesh definitions, 15 materials, 24,680 triangles, 17 animation clips and 61 animation channels.
- AI response parsing produces bounded semantic mood, gaze, head tilt, body lean, gesture, energy and emphasis values.
- The Android renderer maps semantic mood/gesture to the GLB's named clips through SceneView's `ModelNode.animationName` API, plays one-shot gestures once, returns to idle and adjusts playback speed from AI energy.
- The floating overlay now observes the same persisted AI motion and speech state as the main activity; it no longer runs an unrelated random gesture loop.
- ElevenLabs voice `Miss minutes` (`3ooIiYiDLwKz4dFFc7WM`) is configured in the app with `eleven_multilingual_v2`; API key remains encrypted with Android Keystore.
- Playback state selects `Talk_Loop` while speech is active.
- Android 14+ speech recognition requests automatic Russian/English switching; older versions retain system-language recognition.
- Per-turn response language is selected from the detected script (Cyrillic/Russian or Latin/English).
- Unit tests cover semantic parsing, bounds, animation selection, playback speed and language routing.

## Not yet production complete

### 3D model and rig
The GLB currently staged by Android CI has 120 nodes, 24 mesh definitions and 17 animation clips. It contains separate node-transform controls for eyes, pupils, brows and mouth expressions, but no glTF skins, weighted primitives, articulated finger joints, morph targets, embedded textures or separate clock-hand controls. It is a transform-animated stylized recreation, not a production skeletal/facial rig.

The separate user-supplied `Rig - Miss Minutes.blend` and `Rig - Miss Minutes.glb` are not yet integrated into the Android asset pipeline. The supplied advanced GLB audit recorded 245 nodes, 88 mesh/primitives, 53 skin objects and 4 materials; it still lacked ring-finger chains, independent eyebrow controls, separate hour/minute clock controls and morph targets.

The editable Blender source must be opened in Blender, its missing controls and weights authored, then exported and validated. Blender is not available in the current build environment, so no claim is made that the source .blend has been modified.

### Procedural motion
Current runtime behavior is AI-directed named-clip scheduling with one-shot return-to-idle and playback-speed modulation. It is not yet a continuous procedural joint solver, layered skeletal animation system or morph-target controller.

### Speech synchronization
The voice output is wired to playback and the Talk_Loop clip, including in the floating overlay. Phoneme timing, viseme weights, mouth/jaw deformation and live audio lip-sync are not implemented.

### Android release
A previous Android CI run failed at Kotlin compilation because of a missing `rememberEngine` import and an OverlayService argument type mismatch. Both source errors have been fixed in this branch. A fresh successful CI run and physical-device verification are still required before calling the release complete.

## Release acceptance criteria

1. The advanced skinned GLB passes a validator that requires actual skins, weighted primitives, named finger/brow/clock controls and facial morph targets or an independently verified equivalent.
2. Android runtime can address and animate those controls.
3. AI semantic intent drives the runtime controller without depending exclusively on canned clips.
4. Russian/English speech recognition and ElevenLabs playback are verified on device.
5. `testDebugUnitTest`, `assembleDebug` and `lintDebug` pass in CI.
6. APK installs and passes visual, FPS, memory and thermal checks on the target Android device.
