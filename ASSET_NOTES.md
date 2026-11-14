# Miss Minutes 3D asset

Unofficial fan-made recreation of the animated TVA clock character. This is a real glTF 2.0 binary scene, not a rendered image or a 2D sprite.

## Files
- `assets/models/miss-minutes.glb` — geometry, PBR materials, node rig and animation clips.
- `assets/models/miss-minutes.manifest.json` — scene inventory and rig controls.
- `tools/build-model.mjs` — dependency-free Node.js generator.
- `tools/validate-model.mjs` — structural GLB validator.
- `preview.html` — interactive touch/mouse orbit preview and animation selector.

## Animation clips
- `Idle_Breathe`
- `Blink`
- `Wave_Greeting`
- `Pointing`
- `Talk_Loop`
- `Laugh`
- `Surprised`
- `Angry_Expression`
- `Sleep_Idle`
- `Glitch_Recovery`

## Rig
Independent controls are exposed for body, shoulders, elbows, wrists, hands, hips, knees, ankles, eyes, brows and mouth variants. Segmented cartoon geometry keeps the rig lightweight and suitable for additional runtime actions.

## Build
```bash
node tools/build-model.mjs
node tools/validate-model.mjs
```

The GLB can be imported into Three.js, Godot, Unity, Blender and other glTF-compatible tools. No extracted Marvel/Disney source assets are included.
