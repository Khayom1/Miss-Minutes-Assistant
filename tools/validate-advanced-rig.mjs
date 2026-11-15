#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";

const file = process.argv[2] ?? "assets/models/miss-minutes.glb";
const failures = [];
const passes = [];
const warnings = [];
const check = (ok, message) => (ok ? passes : failures).push(message);

const COMPONENTS = {
  5120: { bytes: 1, read: (view, offset) => view.getInt8(offset) },
  5121: { bytes: 1, read: (view, offset) => view.getUint8(offset) },
  5122: { bytes: 2, read: (view, offset) => view.getInt16(offset, true) },
  5123: { bytes: 2, read: (view, offset) => view.getUint16(offset, true) },
  5125: { bytes: 4, read: (view, offset) => view.getUint32(offset, true) },
  5126: { bytes: 4, read: (view, offset) => view.getFloat32(offset, true) }
};
const COMPONENT_COUNTS = { SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4, MAT2: 4, MAT3: 9, MAT4: 16 };

function readGlb(filename) {
  const bytes = fs.readFileSync(filename);
  if (bytes.toString("ascii", 0, 4) !== "glTF") throw new Error("Invalid GLB magic");
  if (bytes.readUInt32LE(4) !== 2) throw new Error("GLB version must be 2");
  if (bytes.readUInt32LE(8) !== bytes.length) throw new Error("GLB total-length mismatch");

  let offset = 12;
  let json = null;
  let binary = null;
  while (offset < bytes.length) {
    if (offset + 8 > bytes.length) throw new Error("Truncated GLB chunk header");
    const length = bytes.readUInt32LE(offset);
    const type = bytes.readUInt32LE(offset + 4);
    offset += 8;
    if (offset + length > bytes.length) throw new Error("Truncated GLB chunk");
    if (type === 0x4e4f534a) {
      json = JSON.parse(bytes.toString("utf8", offset, offset + length).replace(/\\0+$/g, "").trim());
    } else if (type === 0x004e4942) {
      binary = bytes.subarray(offset, offset + length);
    }
    offset += length;
  }
  if (!json || !binary) throw new Error("GLB must contain JSON and BIN chunks");
  if ((json.buffers?.[0]?.byteLength ?? 0) > binary.length) throw new Error("GLB buffer exceeds BIN chunk");
  return { bytes, json, binary };
}

function accessorReader(json, binary) {
  const accessors = json.accessors ?? [];
  const views = json.bufferViews ?? [];
  const dataView = new DataView(binary.buffer, binary.byteOffset, binary.byteLength);

  return (index) => {
    const accessor = accessors[index];
    if (!accessor || accessor.bufferView === undefined || accessor.sparse) {
      throw new Error(`Unsupported or missing accessor ${index}`);
    }
    const bufferView = views[accessor.bufferView];
    const component = COMPONENTS[accessor.componentType];
    const componentCount = COMPONENT_COUNTS[accessor.type];
    if (!bufferView || !component || !componentCount) throw new Error(`Invalid accessor layout ${index}`);
    const elementBytes = component.bytes * componentCount;
    const stride = bufferView.byteStride ?? elementBytes;
    if (stride < elementBytes) throw new Error(`Accessor ${index} has an invalid byteStride`);
    const start = (bufferView.byteOffset ?? 0) + (accessor.byteOffset ?? 0);
    const rows = new Array(accessor.count);

    for (let row = 0; row < accessor.count; row++) {
      const values = new Array(componentCount);
      for (let column = 0; column < componentCount; column++) {
        let value = component.read(dataView, start + row * stride + column * component.bytes);
        if (accessor.normalized && accessor.componentType !== 5126) {
          if (accessor.componentType === 5120) value = Math.max(value / 127, -1);
          else if (accessor.componentType === 5122) value = Math.max(value / 32767, -1);
          else value /= accessor.componentType === 5121 ? 255 : accessor.componentType === 5123 ? 65535 : 4294967295;
        }
        values[column] = value;
      }
      rows[row] = values;
    }
    return rows;
  };
}

function hasAllNames(nodes, fragments) {
  const names = nodes.map(node => String(node.name ?? "").toLowerCase());
  return fragments.every(fragment => names.some(name => name.includes(fragment)));
}

function validateHierarchy(json) {
  const nodes = json.nodes ?? [];
  const parentCount = new Array(nodes.length).fill(0);
  let invalidChild = false;
  for (const node of nodes) {
    for (const child of node.children ?? []) {
      if (!Number.isInteger(child) || child < 0 || child >= nodes.length) invalidChild = true;
      else parentCount[child]++;
    }
  }
  const multipleParents = parentCount.filter(count => count > 1).length;
  const roots = json.scenes?.[json.scene]?.nodes ?? [];
  const visited = new Set();
  const stack = [...roots];
  let cycle = false;
  while (stack.length) {
    const index = stack.pop();
    if (!Number.isInteger(index) || index < 0 || index >= nodes.length) {
      invalidChild = true;
      continue;
    }
    if (visited.has(index)) continue;
    visited.add(index);
    for (const child of nodes[index].children ?? []) stack.push(child);
  }
  return {
    valid: !invalidChild && multipleParents === 0 && !cycle && visited.size === nodes.length,
    invalidChild,
    multipleParents,
    reachableNodes: visited.size,
    totalNodes: nodes.length,
    cycle
  };
}

try {
  const { bytes, json, binary } = readGlb(file);
  const readAccessor = accessorReader(json, binary);
  const nodes = json.nodes ?? [];
  const meshes = json.meshes ?? [];
  const skins = json.skins ?? [];
  const primitives = meshes.flatMap(mesh => mesh.primitives ?? []);
  const skinnedNodes = nodes.filter(node => Number.isInteger(node.mesh) && Number.isInteger(node.skin));
  const morphPrimitives = primitives.filter(primitive => Array.isArray(primitive.targets) && primitive.targets.length > 0);
  const morphNames = new Set(meshes.flatMap(mesh => (mesh.extras?.targetNames ?? []).map(name => String(name).toLowerCase())));
  const names = nodes.map(node => String(node.name ?? "").toLowerCase());
  const hierarchy = validateHierarchy(json);

  let weightedPrimitiveCount = 0;
  let weightedVertexCount = 0;
  let maxJointIndex = 0;
  let invalidWeightVertices = 0;
  let maxWeightSumError = 0;
  let unpairedSkinAttributes = 0;

  for (const node of skinnedNodes) {
    const skin = skins[node.skin];
    if (!skin) {
      failures.push(`Node '${node.name ?? node.mesh}' references missing skin ${node.skin}`);
      continue;
    }
    for (const primitive of meshes[node.mesh]?.primitives ?? []) {
      const attributes = primitive.attributes ?? {};
      const hasJoints = attributes.JOINTS_0 !== undefined;
      const hasWeights = attributes.WEIGHTS_0 !== undefined;
      if (hasJoints !== hasWeights) {
        unpairedSkinAttributes++;
        continue;
      }
      if (!hasJoints) continue;

      const joints = readAccessor(attributes.JOINTS_0);
      const weights = readAccessor(attributes.WEIGHTS_0);
      weightedPrimitiveCount++;
      weightedVertexCount += joints.length;
      if (joints.length !== weights.length) {
        invalidWeightVertices++;
        continue;
      }
      for (let row = 0; row < joints.length; row++) {
        for (const joint of joints[row]) {
          maxJointIndex = Math.max(maxJointIndex, joint);
          if (!Number.isInteger(joint) || joint < 0 || joint >= skin.joints.length) invalidWeightVertices++;
        }
        const sum = weights[row].reduce((total, value) => total + value, 0);
        const error = Math.abs(sum - 1);
        maxWeightSumError = Math.max(maxWeightSumError, error);
        if (!Number.isFinite(sum) || error > 0.02 || weights[row].some(value => !Number.isFinite(value) || value < 0)) {
          invalidWeightVertices++;
        }
      }
    }
  }

  const triangleCount = primitives.reduce((total, primitive) => {
    const accessorIndex = primitive.indices ?? primitive.attributes?.POSITION;
    const count = json.accessors?.[accessorIndex]?.count ?? 0;
    const mode = primitive.mode ?? 4;
    return total + (mode === 4 ? Math.floor(count / 3) : mode === 5 || mode === 6 ? Math.max(0, count - 2) : 0);
  }, 0);
  const uniqueJointSets = new Set(skins.map(skin => JSON.stringify(skin.joints ?? []))).size;
  const inverseBindHashes = new Set();
  for (const skin of skins) {
    if (Number.isInteger(skin.inverseBindMatrices)) {
      const values = readAccessor(skin.inverseBindMatrices);
      inverseBindHashes.add(createHash("sha256").update(JSON.stringify(values)).digest("hex"));
    }
  }
  const vertexColors = primitives.some(primitive => primitive.attributes?.COLOR_0 !== undefined);
  const embeddedImages = (json.images ?? []).length;
  const embeddedTextures = (json.textures ?? []).length;

  check(bytes.length > 1024, `GLB non-empty (${bytes.length} bytes)`);
  check(nodes.length >= 100, `Character hierarchy (${nodes.length} nodes)`);
  check(hierarchy.valid, `Hierarchy valid; ${hierarchy.reachableNodes}/${hierarchy.totalNodes} nodes reachable, no invalid child or multiple parent`);
  check(skins.length > 0, `Skin objects present (${skins.length})`);
  check(skinnedNodes.length > 0, `Skinned mesh nodes (${skinnedNodes.length})`);
  check(weightedPrimitiveCount > 0, `Weighted primitives (${weightedPrimitiveCount})`);
  check(unpairedSkinAttributes === 0, "JOINTS_0 and WEIGHTS_0 are paired on every weighted primitive");
  check(invalidWeightVertices === 0, `Skin weights and joint indices valid (${invalidWeightVertices} invalid vertices; max joint index ${maxJointIndex})`);
  check(skins.every(skin => Array.isArray(skin.joints) && skin.joints.length > 0 && skin.joints.every(joint => Number.isInteger(joint) && nodes[joint])), "Every skin references valid joint nodes");
  check(skins.every(skin => skin.skeleton === undefined || (Number.isInteger(skin.skeleton) && nodes[skin.skeleton])), "Every skin skeleton root references a valid node");
  check(skins.every(skin => Number.isInteger(skin.inverseBindMatrices) && (json.accessors?.[skin.inverseBindMatrices]?.count === skin.joints.length)), "Every skin has one inverse-bind matrix per joint");
  check((json.materials ?? []).length > 0, `Materials present (${(json.materials ?? []).length})`);
  check(embeddedImages > 0 || vertexColors, `Appearance data present (images ${embeddedImages}, vertex colors ${vertexColors})`);
  check(json.scenes?.length > 0 && Number.isInteger(json.scene), "Default scene assigned");

  const requiredGroups = [
    ["left/right arms", ["arm_l", "arm_r"]],
    ["left/right legs", ["leg_l", "leg_r"]],
    ["left/right feet", ["foot_l", "foot_r"]],
    ["left/right eyes", ["mesh_eyel", "mesh_eyer"]],
    ["left/right pupils", ["pupill", "pupilr"]],
    ["upper/lower eyelids", ["top_eyelid", "bottom_eyelid"]],
    ["upper/lower lips", ["top_lip", "bottom_lip"]],
    ["upper/lower teeth", ["teeth_up", "teeth_down"]],
    ["tongue mesh and controls", ["mesh_tongue", "tongue_base", "tongue_tip"]],
    ["clock dial and hand mesh", ["mesh_clock_marks", "mesh_hands"]]
  ];
  for (const [label, fragments] of requiredGroups) {
    check(hasAllNames(nodes, fragments), `${label}: ${hasAllNames(nodes, fragments) ? "present" : "missing"}`);
  }

  for (const side of ["l", "r"]) {
    for (const finger of ["thumb", "index", "middle", "ring", "pinky"]) {
      const found = names.some(name => name.includes(`finger_${finger}_${side}`) || name.includes(`${finger}_${side}`));
      check(found, `${side.toUpperCase()} ${finger} finger chain: ${found ? "present" : "missing"}`);
    }
    const brow = names.some(name => name.includes(`brow_${side}`) || name.includes(`eyebrow_${side}`));
    check(brow, `${side.toUpperCase()} eyebrow control: ${brow ? "present" : "missing"}`);
  }
  for (const hand of ["hour", "minute"]) {
    const found = names.some(name => name.includes(`${hand}_hand`) || name.includes(`clock_${hand}`));
    check(found, `${hand} clock-hand control: ${found ? "present" : "missing"}`);
  }
  const jawControl = names.some(name => name.includes("jaw") || name.includes("mandible"));
  check(jawControl, `Jaw control: ${jawControl ? "present" : "missing"}`);

  check(morphPrimitives.length > 0, `Morph target primitives (${morphPrimitives.length})`);
  for (const target of ["blink_l", "blink_r", "smile", "jaw_open", "mouth_aa", "mouth_ee", "mouth_oh", "brow_raise_l", "brow_raise_r"]) {
    const found = [...morphNames].some(name => name.includes(target));
    check(found, `Facial target '${target}': ${found ? "present" : "missing"}`);
  }

  if ((json.animations ?? []).length === 0) warnings.push("No authored animation clips; runtime must supply procedural animation.");
  if (embeddedImages === 0) warnings.push("No embedded images; current appearance relies on materials/vertex colors.");
  console.log(`Advanced Rig release gate: ${path.resolve(file)}`);
  console.log(`GLB ${bytes.length} bytes | nodes ${nodes.length} | meshes ${meshes.length} | primitives ${primitives.length} | triangles ${triangleCount}`);
  console.log(`skins ${skins.length} | unique joint sets ${uniqueJointSets} | unique inverse-bind data ${inverseBindHashes.size} | skinned nodes ${skinnedNodes.length}`);
  console.log(`weighted primitives ${weightedPrimitiveCount} | weighted vertices ${weightedVertexCount} | max weight-sum error ${maxWeightSumError}`);
  console.log(`animations ${(json.animations ?? []).length} | morph primitives ${morphPrimitives.length} | materials ${(json.materials ?? []).length} | images ${embeddedImages} | textures ${embeddedTextures}`);
  for (const message of passes) console.log("PASS  " + message);
  for (const message of warnings) console.warn("WARN  " + message);
  for (const message of failures) console.error("FAIL  " + message);
  console.log(`RESULT ${failures.length ? "BLOCKED" : "PASS"} — ${passes.length} passed, ${failures.length} failed, ${warnings.length} warnings`);
  process.exitCode = failures.length ? 1 : 0;
} catch (error) {
  console.error("VALIDATOR ERROR: " + error.message);
  process.exitCode = 2;
}
