import { readFileSync } from "node:fs";

const file = process.argv[2] || "assets/models/miss-minutes.glb";
const bytes = readFileSync(file);
const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
const fail = (message) => { throw new Error(message); };

if (bytes.length < 28) fail("GLB is too small");
if (view.getUint32(0, true) !== 0x46546c67) fail("Invalid GLB magic");
if (view.getUint32(4, true) !== 2) fail("GLB version is not 2");
if (view.getUint32(8, true) !== bytes.length) fail("GLB total length mismatch");

let offset = 12, json = null, binary = null;
while (offset < bytes.length) {
  if (offset + 8 > bytes.length) fail("Truncated GLB chunk header");
  const length = view.getUint32(offset, true), type = view.getUint32(offset + 4, true);
  offset += 8;
  if (length % 4 !== 0 || offset + length > bytes.length) fail("Invalid GLB chunk length/alignment");
  const chunk = bytes.subarray(offset, offset + length);
  if (type === 0x4e4f534a) json = JSON.parse(new TextDecoder().decode(chunk).trim());
  if (type === 0x004e4942) binary = chunk;
  offset += length;
}
if (!json || !binary) fail("Missing JSON or BIN chunk");
if (json.asset?.version !== "2.0") fail("glTF asset version is not 2.0");
if (!json.nodes?.some((node) => node.name === "MissMinutes_Root")) fail("Character root node missing");

const required = [
  "Idle_Breathe","Blink","Wave_Greeting","Pointing","Talk_Loop","Laugh",
  "Surprised","Angry_Expression","Sleep_Idle","Glitch_Recovery","Walk_Cycle",
  "Run_Cycle","Look_Around","Peek_Around","Spin_In_Place","Nervous_Glance","Cheerful_Bounce"
];
const actual = new Set((json.animations || []).map((animation) => animation.name));
for (const name of required) if (!actual.has(name)) fail("Missing animation: " + name);
if (json.nodes.length < 100 || json.meshes.length < 20 || json.materials.length < 12) fail("Model inventory below expected thresholds");

const byteSizes = {5121:1,5123:2,5125:4,5126:4};
const arities = {SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT2:4,MAT3:9,MAT4:16};
for (const [i, bufferView] of json.bufferViews.entries()) {
  if (bufferView.buffer !== 0 || (bufferView.byteOffset || 0) + bufferView.byteLength > binary.length) fail("bufferView out of bounds: " + i);
}
for (const [i, accessor] of json.accessors.entries()) {
  const bv = json.bufferViews[accessor.bufferView], size = byteSizes[accessor.componentType], arity = arities[accessor.type];
  if (!bv || !size || !arity) fail("Invalid accessor: " + i);
  if ((accessor.byteOffset || 0) + accessor.count * size * arity > bv.byteLength) fail("Accessor exceeds bufferView: " + i);
  if (!Number.isInteger(accessor.count) || accessor.count < 1) fail("Invalid accessor count: " + i);
}
const read = (accessorIndex, componentIndex) => {
  const a=json.accessors[accessorIndex],bv=json.bufferViews[a.bufferView];
  const p=(bv.byteOffset||0)+(a.byteOffset||0)+componentIndex*byteSizes[a.componentType];
  const d=new DataView(binary.buffer,binary.byteOffset,binary.byteLength);
  if(a.componentType===5126)return d.getFloat32(p,true);
  if(a.componentType===5125)return d.getUint32(p,true);
  if(a.componentType===5123)return d.getUint16(p,true);
  return d.getUint8(p);
};

let triangles=0,inverted=0;
for(const mesh of json.meshes)for(const primitive of mesh.primitives||[]){
  const position=primitive.attributes?.POSITION,normal=primitive.attributes?.NORMAL,indices=primitive.indices;
  if(position===undefined||normal===undefined)fail("Missing vertex attributes: "+mesh.name);
  const pa=json.accessors[position],ia=json.accessors[indices];
  if(!pa||!ia||pa.type!=="VEC3"||ia.componentType!==5125||ia.count%3!==0)fail("Invalid primitive accessors: "+mesh.name);
  for(let i=0;i<ia.count;i+=3){
    const ids=[read(indices,i),read(indices,i+1),read(indices,i+2)];
    if(ids.some(id=>id>=pa.count))fail("Index overflow: "+mesh.name);
    const p=ids.map(id=>[0,1,2].map(axis=>read(position,id*3+axis)));
    const u=p[1].map((x,j)=>x-p[0][j]),v=p[2].map((x,j)=>x-p[0][j]);
    const c=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]];
    const n=[0,1,2].map(axis=>ids.reduce((s,id)=>s+read(normal,id*3+axis),0)/3);
    const dot=c.reduce((s,x,j)=>s+x*n[j],0);
    if(!Number.isFinite(dot))fail("Non-finite vertex normal: "+mesh.name);
    if(dot < -1e-8)inverted++;
    triangles++;
  }
}
if(inverted)fail("Inward-facing triangles detected: "+inverted);

let tracks=0;
for(const animation of json.animations||[])for(const channel of animation.channels||[]){
  const sampler=animation.samplers?.[channel.sampler],input=sampler&&json.accessors[sampler.input],output=sampler&&json.accessors[sampler.output];
  if(!sampler||!input||!output)fail("Broken animation sampler: "+animation.name);
  if(channel.target.node<0||channel.target.node>=json.nodes.length)fail("Invalid animation target: "+animation.name);
  if(!["translation","rotation","scale","weights"].includes(channel.target.path))fail("Invalid animation path: "+animation.name);
  if(input.type!=="SCALAR"||input.count!==output.count)fail("Animation input/output mismatch: "+animation.name);
  let last=-Infinity;
  for(let i=0;i<input.count;i++){const time=read(sampler.input,i);if(!Number.isFinite(time)||time<=last)fail("Non-monotonic keyframes: "+animation.name);last=time;}
  tracks++;
}
if(tracks<40)fail("Too few animation tracks");

console.log(JSON.stringify({
  valid:true,bytes:bytes.length,nodes:json.nodes.length,meshes:json.meshes.length,
  materials:json.materials.length,triangles,animationCount:actual.size,animationTracks:tracks,
  skinnedMeshes:(json.skins||[]).length>0,
  morphTargets:json.meshes.some(m=>m.primitives.some(p=>(p.targets||[]).length>0)),
  textures:(json.textures||[]).length,animations:required
},null,2));
