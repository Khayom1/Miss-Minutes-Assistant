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
The supplied `Rig - Miss Minutes.glb` is a valid GLB 2.0 file (2,732,724 bytes) with 245 nodes, 88 mesh definitions, 53 skin objects and 4 materials. Its hierarchy contains 138 joint references per skin and includes the body, arms, legs, feet, hands, eyes, pupils, eyelids, lips, teeth, tongue and clock geometry. The visible hand hierarchy includes thumb, index, middle and pinky chains; the ring finger is absent. Both eye assemblies contain separate upper/lower eyelid control geometry. The clock is currently represented by a combined hands mesh.

A structural audit of the supplied export confirms these release blockers:
- **Hands:** left and right ring-finger chains are missing; existing finger coverage is not a complete five-digit hand rig.
- **Face:** no independent eyebrow controls are present.
- **Clock:** hour and minute hands do not have independently addressable controls in the exported hierarchy.
- **Facial deformation:** no glTF morph targets are exported. Separate eyelid and lip bones/meshes exist, but the required expression/phoneme set is not yet proven.
- **Appearance:** no embedded images are present; vertex colors and materials are present, so this is not by itself a blocker.
- **Runtime:** the GLB contains no animation clips. This is acceptable only if the Android runtime drives the rig procedurally; that connection is not yet implemented or device-verified.

The editable `.blend` source must be treated as the authoring source. The GLB is an export, not a substitute for the source rig. No destructive edits should be made until the Blender scene is inspected and its armature, mesh ownership, vertex groups and bone parenting are mapped.

## Definition of done
Do not label the asset production-ready until the release gate passes, the Blender source has been reopened and checked, the exported GLB has loaded in the Android renderer, and hand/foot/face/clock controls have passed visual deformation tests on the target Android device.
