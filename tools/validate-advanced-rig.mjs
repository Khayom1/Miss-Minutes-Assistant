#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
const file = process.argv[2] ?? "assets/models/miss-minutes.glb";
const failures = [], passes = [], warnings = [];
function check(ok, message) { (ok ? passes : failures).push(message); }
function readGlb(filename) {
  const bytes = fs.readFileSync(filename);
  if (bytes.toString("ascii", 0, 4) !== "glTF") throw new Error("Invalid GLB magic");
  if (bytes.readUInt32LE(4) !== 2) throw new Error("GLB version must be 2");
  if (bytes.readUInt32LE(8) !== bytes.length) throw new Error("GLB total-length mismatch");
  let offset = 12, json = null, binary = null;
  while (offset < bytes.length) {
    if (offset + 8 > bytes.length) throw new Error("Truncated GLB chunk header");
    const length = bytes.readUInt32LE(offset), type = bytes.readUInt32LE(offset + 4);
    offset += 8;
    if (offset + length > bytes.length) throw new Error("Truncated GLB chunk");
    if (type === 0x4e4f534a) json = JSON.parse(bytes.toString("utf8", offset, offset + length).replace(/\\0+$/g, "").trim());
    if (type === 0x004e4942) binary = bytes.subarray(offset, offset + length);
    offset += length;
  }
  if (!json || !binary) throw new Error("GLB must contain JSON and BIN chunks");
  return { bytes, json };
}
function hasName(nodes, fragments) {
  const names = nodes.map(n => String(n.name ?? "").toLowerCase());
  return fragments.every(fragment => names.some(name => name.includes(fragment)));
}
try {
  const { bytes, json } = readGlb(file);
  const nodes = json.nodes ?? [], meshes = json.meshes ?? [], skins = json.skins ?? [];
  const primitives = meshes.flatMap(mesh => mesh.primitives ?? []);
  const skinned = primitives.filter(p => p.attributes?.JOINTS_0 !== undefined && p.attributes?.WEIGHTS_0 !== undefined);
  const morphPrimitives = primitives.filter(p => Array.isArray(p.targets) && p.targets.length > 0);
  const morphNames = new Set(meshes.flatMap(m => (m.extras?.targetNames ?? []).map(n => String(n).toLowerCase())));
  const names = nodes.map(n => String(n.name ?? "").toLowerCase());
  check(bytes.length > 1024, `GLB non-empty (${bytes.length} bytes)`);
  check(nodes.length >= 100, `Character hierarchy (${nodes.length} nodes)`);
  check(skins.length > 0, `Skins present (${skins.length})`);
  check(skinned.length > 0, `Skinned primitives (${skinned.length}/${primitives.length})`);
  check(skinned.length === primitives.filter(p => p.attributes?.JOINTS_0 !== undefined).length, "JOINTS_0 and WEIGHTS_0 are paired");
  const groups = [
    ["root", ["root"]],
    ["left/right arms", ["arm_l", "arm_r"]],
    ["left/right legs", ["leg_l", "leg_r"]],
    ["left/right feet", ["foot_l", "foot_r"]],
    ["left/right eyes", ["mesh_eyel", "mesh_eyer"]],
    ["left/right pupils", ["pupill", "pupilr"]],
    ["upper/lower eyelids", ["top_eyelid", "bottom_eyelid"]],
    ["upper/lower lips", ["top_lip", "bottom_lip"]],
    ["upper/lower teeth", ["teeth_up", "teeth_down"]],
    ["tongue mesh and controls", ["mesh_tongue", "tongue_base", "tongue_tip"]],
    ["clock dial and hand meshes", ["mesh_clock_marks", "mesh_hands"]]
  ];
  for (const [label, fragments] of groups) check(hasName(nodes, fragments), `${label}: ${hasName(nodes, fragments) ? "present" : "missing"}`);
  for (const side of ["l", "r"]) {
    for (const finger of ["thumb", "index", "middle", "ring", "pinky"]) {
      const found = names.some(n => n.includes(`finger_${finger}_${side}`) || n.includes(`${finger}_${side}`));
      check(found, `${side.toUpperCase()} ${finger} finger chain: ${found ? "present" : "missing"}`);
    }
    const brow = names.some(n => n.includes(`brow_${side}`) || n.includes(`eyebrow_${side}`));
    check(brow, `${side.toUpperCase()} eyebrow control: ${brow ? "present" : "missing"}`);
  }
  for (const hand of ["hour", "minute"]) {
    const found = names.some(n => n.includes(`${hand}_hand`) || n.includes(`clock_${hand}`));
    check(found, `${hand} clock-hand control: ${found ? "present" : "missing"}`);
  }
  check(morphPrimitives.length > 0, `Morph targets (${morphPrimitives.length} primitives)`);
  for (const target of ["blink_l","blink_r","smile","jaw_open","mouth_aa","mouth_ee","mouth_oh","brow_raise_l","brow_raise_r"]) {
    const found = [...morphNames].some(n => n.includes(target));
    check(found, `Facial target '${target}': ${found ? "present" : "missing"}`);
  }
  const hasVertexColors = primitives.some(p => p.attributes?.COLOR_0 !== undefined);
  const hasImages = (json.images ?? []).length > 0;
  check((json.materials ?? []).length > 0, `Materials present (${(json.materials ?? []).length})`);
  check(hasImages || hasVertexColors, "Appearance uses embedded images or vertex colors");
  check(json.scenes?.length > 0 && Number.isInteger(json.scene), "Default scene assigned");
  for (const [i, skin] of skins.entries()) {
    check(Array.isArray(skin.joints) && skin.joints.length > 0, `Skin ${i} has joints`);
    check(Array.isArray(skin.joints) && skin.joints.every(j => Number.isInteger(j) && nodes[j]), `Skin ${i} references valid nodes`);
  }
  if ((json.animations ?? []).length) warnings.push("Optional animation clips exist; autonomous motion must not depend on them.");
  console.log(`Advanced Rig release gate: ${path.resolve(file)}`);
  console.log(`GLB ${bytes.length} bytes | nodes ${nodes.length} | meshes ${meshes.length} | primitives ${primitives.length} | skins ${skins.length} | morph primitives ${morphPrimitives.length}`);
  for (const message of passes) console.log("PASS  " + message);
  for (const message of warnings) console.warn("WARN  " + message);
  for (const message of failures) console.error("FAIL  " + message);
  console.log(`RESULT ${failures.length ? "BLOCKED" : "PASS"} — ${passes.length} passed, ${failures.length} failed, ${warnings.length} warnings`);
  process.exitCode = failures.length ? 1 : 0;
} catch (error) {
  console.error("VALIDATOR ERROR: " + error.message);
  process.exitCode = 2;
}
