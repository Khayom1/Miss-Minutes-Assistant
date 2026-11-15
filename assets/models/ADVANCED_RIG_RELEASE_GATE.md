# Miss Minutes Advanced Rig — Release Gate

This is the acceptance contract for the final Android-ready character. A GLB is not production-ready merely because it loads or contains a skeleton.

## Source of truth
- Preserve the supplied editable Blender project as the authoring source.
- Export glTF 2.0 binary (.glb) for Android runtime.
- Never overwrite the original source during repair/export.

## Required capabilities
- Full body: root, torso, both arms, both legs, feet and toes.
- Both hands: thumb, index, middle, ring and pinky chains, with usable deformation weights.
- Face: independently controllable eyes, pupils, upper/lower eyelids, brows, upper/lower lips, jaw, upper/lower teeth and tongue.
- Clock: dial plus independently addressable hour and minute hands.
- Facial deformation: named morph targets for blink, smile, jaw opening, phoneme mouth shapes and brow movement, or a verified equivalent rig with equivalent independent controls.
- Runtime: idle, gestures, gaze, expressions and speech must not require pre-authored animation clips.
- Appearance: valid materials and either embedded images or vertex colors.
- Structure: valid GLB 2.0, valid skin joint references, JOINTS_0/WEIGHTS_0 pairing and a valid default scene.

## Release check
Run: node tools/validate-advanced-rig.mjs assets/models/miss-minutes.glb

The release gate must return exit code 0. It is intentionally not wired into CI until the replacement asset passes, so the current app build is not broken while the rig is being authored.

## Current supplied GLB audit
The supplied Rig - Miss Minutes.glb is a valid GLB 2.0 file (2,732,724 bytes) with 245 nodes, 88 meshes/primitives, 53 skin objects and 4 materials. It has skinned primitives and contains body, eyes, pupils, eyelids, lips, teeth, tongue, clock dial and clock-hand geometry.

It currently fails the final release contract:
- Ring-finger chains are absent from the exported node hierarchy.
- Independent eyebrow controls are absent from the exported node hierarchy.
- Separate hour-hand and minute-hand controls are absent from the exported node hierarchy.
- No morph targets are exported.
- No embedded images are present; vertex colors are present, so this alone is not a blocker.

The GLB contains no animation clips. This is acceptable for the intended procedural-motion architecture.

## Definition of done
Do not label the asset production-ready until the release gate passes, the Blender source has been reopened and checked, the exported GLB has loaded in the Android renderer, and hand/foot/face/clock controls have passed visual deformation tests on the target Android device.
