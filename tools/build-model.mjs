import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
function buildMissMinutesGLB() {
  function utf8(s){const u=encodeURIComponent(s),a=[];for(let i=0;i<u.length;){if(u[i]==="%"){a.push(parseInt(u.slice(i+1,i+3),16));i+=3;}else{a.push(u.charCodeAt(i));i++;}}return new Uint8Array(a);}
  const chunks=[];let binLength=0;
  const g={asset:{version:"2.0",generator:"Miss Minutes Character Foundry 1.0",copyright:"Unofficial fan-made recreation."},scene:0,scenes:[{name:"Miss Minutes Character Scene",nodes:[]}],nodes:[],meshes:[],materials:[],accessors:[],bufferViews:[],animations:[],buffers:[]},roots=[],nodeMap={},cache=new Map();
  const mats=[
    ["Clock Orange",[1,.255,.035,1],.12,.3],["Face Amber",[1,.43,.075,1],.04,.42],["Rim Deep Orange",[.48,.075,.018,1],.28,.3],["Rim Highlight",[1,.60,.16,1],.16,.28],["Ink Dark Brown",[.075,.027,.018,1],0,.58],["Eye Ivory",[1,.965,.83,1],0,.24],["Pupil Black",[.012,.008,.007,1],.05,.16],["Specular White",[1,1,1,1],.08,.08],["Glove Porcelain",[.93,.92,.86,1],0,.33],["Glove Shadow",[.57,.53,.46,1],0,.48],["Leg Dark Umber",[.12,.052,.026,1],0,.5],["Boot Orange",[1,.31,.035,1],.05,.32],["Boot Sole",[.25,.055,.018,1],0,.62],["Mouth Interior",[.12,.012,.018,1],0,.5],["Tongue Coral",[.86,.085,.12,1],0,.38]
  ];
  for(const [name,baseColorFactor,metallicFactor,roughnessFactor] of mats)g.materials.push({name,pbrMetallicRoughness:{baseColorFactor,metallicFactor,roughnessFactor},doubleSided:true});
  const align4=n=>(n+3)&~3;
  function append(t){const b=new Uint8Array(t.buffer,t.byteOffset,t.byteLength),o=binLength,l=align4(b.length),p=new Uint8Array(l);p.set(b);chunks.push(p);binLength+=l;return{o,length:b.length};}
  function acc(v,ct,type,target,bounds){const T=ct===5126?Float32Array:ct===5123?Uint16Array:Uint32Array,t=new T(v),r=append(t),view=g.bufferViews.length;g.bufferViews.push({buffer:0,byteOffset:r.o,byteLength:r.length,...(target?{target}:{})});const count=v.length/({SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT4:16}[type]),a={bufferView:view,componentType:ct,count,type};if(bounds){a.min=bounds.min;a.max=bounds.max;}g.accessors.push(a);return g.accessors.length-1;}
  function mesh(name,p,n,ix,mat){let min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];for(let i=0;i<p.length;i+=3)for(let k=0;k<3;k++){min[k]=Math.min(min[k],p[i+k]);max[k]=Math.max(max[k],p[i+k]);}const pa=acc(p,5126,"VEC3",34962,{min,max}),na=acc(n,5126,"VEC3",34962),ia=acc(ix,5125,"SCALAR",34963);g.meshes.push({name,primitives:[{attributes:{POSITION:pa,NORMAL:na},indices:ia,material:mat,mode:4}]});return g.meshes.length-1;}
  function sphereGeo(lat=24,lon=32){const p=[],n=[],ix=[];for(let y=0;y<=lat;y++){const th=y/lat*Math.PI;for(let x=0;x<=lon;x++){const ph=x/lon*Math.PI*2,nx=Math.sin(th)*Math.cos(ph),ny=Math.cos(th),nz=Math.sin(th)*Math.sin(ph);p.push(nx,ny,nz);n.push(nx,ny,nz);}}for(let y=0;y<lat;y++)for(let x=0;x<lon;x++){const a=y*(lon+1)+x,b=a+lon+1;ix.push(a,b,a+1,b,b+1,a+1);}return{p,n,ix};}
  const unitSphere=sphereGeo();
  function primitive(type,mat){const key=type+":"+mat;if(cache.has(key))return cache.get(key);let q;if(type==="sphere")q=unitSphere;else if(type==="cylinder"){const s=20,p=[],n=[],ix=[];for(let y=0;y<=1;y++)for(let j=0;j<=s;j++){const a=j/s*Math.PI*2,x=Math.cos(a),z=Math.sin(a);p.push(x,y,z);n.push(x,0,z);}for(let j=0;j<s;j++){const a=j,b=a+s+1;ix.push(a,b,a+1,b,b+1,a+1);}const c=p.length/3;p.push(0,0,0,0,1,0);n.push(0,-1,0,0,1,0);for(let j=0;j<s;j++){ix.push(c,j+1,j);ix.push(c+1,s+1+j,s+2+j);}q={p,n,ix};}else if(type==="torus"){const A=64,B=10,R=1,T=.055,p=[],n=[],ix=[];for(let i=0;i<=A;i++){const u=i/A*Math.PI*2;for(let j=0;j<=B;j++){const v=j/B*Math.PI*2,nx=Math.cos(u)*Math.cos(v),ny=Math.sin(u)*Math.cos(v),nz=Math.sin(v);p.push((R+T*Math.cos(v))*Math.cos(u),(R+T*Math.cos(v))*Math.sin(u),T*Math.sin(v));n.push(nx,ny,nz);}}for(let i=0;i<A;i++)for(let j=0;j<B;j++){const a=i*(B+1)+j,b=a+B+1;ix.push(a,b,a+1,b,b+1,a+1);}q={p,n,ix};}else return -1;const id=mesh(type+"_"+mat,q.p,q.n,q.ix,mat);cache.set(key,id);return id;}
  const sph=m=>primitive("sphere",m),cyl=m=>primitive("cylinder",m),tor=m=>primitive("torus",m);
  function qz(a){return[0,0,Math.sin(a/2),Math.cos(a/2)];}
  function qFromY(v0){const l=Math.hypot(...v0),v=v0.map(x=>x/l),d=v[1];if(d<-.999999)return[1,0,0,0];const c=[v[2],0,-v[0]],s=Math.sqrt((1+d)*2);return[c[0]/s,c[1]/s,c[2]/s,s/2];}
  function node(name,parent=null,o={}){const n={name};if(o.translation)n.translation=o.translation;if(o.rotation)n.rotation=o.rotation;if(o.scale)n.scale=o.scale;if(o.mesh!==undefined&&o.mesh!==null)n.mesh=o.mesh;if(o.extras)n.extras=o.extras;const id=g.nodes.length;g.nodes.push(n);nodeMap[name]=id;if(parent===null)roots.push(id);else{g.nodes[parent].children??=[];g.nodes[parent].children.push(id);}return id;}
  function part(name,parent,m,t=[0,0,0],s=[1,1,1],r=null){return node(name,parent,{mesh:m,translation:t,scale:s,rotation:r||undefined});}
  function seg(name,parent,a,b,r,m){const d=[b[0]-a[0],b[1]-a[1],b[2]-a[2]],len=Math.hypot(...d);return part(name,parent,cyl(m),a,[r,len,r],qFromY(d));}
  function ring(name,parent,t,s,m,r){return part(name,parent,tor(m),t,s,r||undefined);}
  function polygon(name,pts,z,mat,th=.012){const p=[],n=[],ix=[],cx=pts.reduce((s,v)=>s+v[0],0)/pts.length,cy=pts.reduce((s,v)=>s+v[1],0)/pts.length;p.push(cx,cy,z+th);n.push(0,0,1);for(const v of pts){p.push(v[0],v[1],z+th);n.push(0,0,1);}const bc=p.length/3;p.push(cx,cy,z-th);n.push(0,0,-1);const bs=p.length/3;for(const v of pts){p.push(v[0],v[1],z-th);n.push(0,0,-1);}for(let i=0;i<pts.length;i++){const j=(i+1)%pts.length;ix.push(0,1+i,1+j,bc,bs+j,bs+i);const f1=1+i,f2=1+j,b1=bs+i,b2=bs+j;ix.push(f1,b1,f2,f2,b1,b2);}return mesh(name,p,n,ix,mat);}
  function mouthShape(kind){if(kind==="surprise"){const p=[];for(let i=0;i<36;i++){const a=i/36*Math.PI*2;p.push([Math.cos(a)*.12,-.30+Math.sin(a)*.16]);}return p;}const top=[],bottom=[],w=.34;for(let i=0;i<=24;i++){const u=i/24*2-1,x=u*w;let y,b;if(kind==="frown"){y=-.30-.09*(1-u*u);b=y-.07*(1-u*u);}else{y=-.30+.10*(1-u*u);b=y-.15*(1-u*u);}top.push([x,y]);bottom.push([x,b]);}return top.concat(bottom.reverse());}

  const root=node("MissMinutes_Root",null,{extras:{role:"character_root",forward:"+Z",up:"+Y"}});
  const body=node("Body_CTRL",root,{extras:{control:"body",pivot:"character_center"}});
  part("Body_ClockShell",body,sph(0),[0,0,0],[1.02,1.02,.25]);
  part("Body_RearCasing",body,sph(2),[0,0,-.11],[.99,.99,.20]);
  part("Face_MainDial",body,sph(1),[0,0,.205],[.94,.94,.105]);
  ring("Bezel_OuterRim",body,[0,0,.205],[1.005,1.005,1],2);
  ring("Bezel_GoldHighlight",body,[0,0,.251],[.955,.955,.42],3);
  for(let i=0;i<12;i++){const a=i*Math.PI/6,major=i%3===0;part("Dial_Tick_"+String(i+1).padStart(2,"0"),body,sph(4),[Math.sin(a)*.82,Math.cos(a)*.82,.304],[major ? .026 : .013,major ? .083 : .061,.012],qz(-a));}
  part("Nose_Button",body,sph(4),[0,-.015,.342],[.047,.047,.025]);
  const eyes=[];
  for(const side of [-1,1]){
    const label=side<0?"R":"L",eye=node("Eye_"+label+"_CTRL",body,{translation:[side*.315,.235,.305],extras:{control:"eye",side:label}});eyes.push(eye);
    part("Eye_"+label+"_White",eye,sph(5),[0,0,0],[.185,.265,.075]);
    const pupil=node("Eye_"+label+"_Pupil_CTRL",eye,{translation:[side*.018,-.01,.067]});
    part("Eye_"+label+"_Pupil",pupil,sph(6),[0,0,0],[.087,.157,.035]);
    part("Eye_"+label+"_Glint_Main",pupil,sph(7),[-.028,.078,.031],[.027,.041,.012]);
    part("Eye_"+label+"_Glint_Small",pupil,sph(7),[.028,-.055,.03],[.012,.018,.009]);
    for(let j=0;j<3;j++){const fx=side*(.09+j*.037),fy=.205+(j===1?.075:.045);seg("Eye_"+label+"_Lash_"+(j+1),body,[side*.315+fx,.235+fy,.365],[side*.315+fx+side*.025,.235+fy+.07,.365],.012,4);}
    const brow=node("Brow_"+label+"_CTRL",body,{translation:[side*.315,.53,.335],extras:{control:"eyebrow"}});
    part("Brow_"+label+"_Shape",brow,sph(4),[0,0,0],[.15,.023,.014],qz(-side*.12));
  }
  const smile=node("Mouth_Smile_CTRL",body,{extras:{expression:"smile",defaultVisible:true}});
  part("Mouth_Smile_Interior",smile,polygon("MouthSmileShape",mouthShape("smile"),.326,13,.014));
  part("Mouth_Smile_Teeth",smile,polygon("MouthTeethShape",[[-.245,-.245],[-.18,-.22],[-.09,-.205],[0,-.201],[.09,-.205],[.18,-.22],[.245,-.245],[.21,-.272],[.12,-.282],[0,-.284],[-.12,-.282],[-.21,-.272]],.345,5,.006));
  const talk=node("Mouth_Talk_CTRL",body,{scale:[0,0,0],extras:{expression:"talk"}});
  part("Mouth_Talk_Interior",talk,sph(13),[0,-.30,.33],[.205,.14,.035]);
  part("Mouth_Talk_Tongue",talk,sph(14),[0,-.37,.365],[.09,.035,.012]);
  const surprise=node("Mouth_Surprise_CTRL",body,{scale:[0,0,0],extras:{expression:"surprise"}});
  part("Mouth_Surprise_O",surprise,polygon("MouthSurpriseShape",mouthShape("surprise"),.33,13,.012));
  const frown=node("Mouth_Frown_CTRL",body,{scale:[0,0,0],extras:{expression:"angry"}});
  part("Mouth_Frown_Interior",frown,polygon("MouthFrownShape",mouthShape("frown"),.33,13,.012));

  const arms={};
  function buildArm(side,raised){const label=side<0?"R":"L",sh=node("Arm_"+label+"_Shoulder_CTRL",root,{translation:[side*.91,-.03,0],extras:{control:"shoulder",side:label}});arms[label]=sh;part("Arm_"+label+"_ShoulderJoint",sh,sph(0),[0,0,0],[.105,.105,.10]);const elbow=raised?[side*.25,.10,0]:[side*.25,-.27,0];seg("Arm_"+label+"_Upper",sh,[0,0,0],elbow,.064,0);part("Arm_"+label+"_Elbow",sh,sph(0),elbow,[.07,.07,.07]);const fore=node("Arm_"+label+"_Forearm_CTRL",sh,{translation:elbow,extras:{control:"elbow"}}),wristAt=raised?[side*.10,.24,0]:[side*.14,-.23,0];seg("Arm_"+label+"_Forearm",fore,[0,0,0],wristAt,.058,0);const wrist=node("Arm_"+label+"_Wrist_CTRL",fore,{translation:wristAt,extras:{control:"wrist"}});part("Arm_"+label+"_Wrist",wrist,sph(0),[0,0,0],[.062,.062,.062]);ring("Arm_"+label+"_Cuff_01",wrist,[0,-.035,0],[.067,.067,.44],2,[Math.SQRT1_2,0,0,Math.SQRT1_2]);ring("Arm_"+label+"_Cuff_02",wrist,[0,-.065,0],[.067,.067,.44],3,[Math.SQRT1_2,0,0,Math.SQRT1_2]);const hand=node("Hand_"+label+"_CTRL",wrist,{translation:[side*.025,raised ? .045 : -.01,0],extras:{control:"hand",gesture:"open_cartoon_glove"}});part("Hand_"+label+"_Palm",hand,sph(8),[0,0,0],[.115,.12,.085]);const starts=[[-.073,.065,0],[-.026,.105,0],[.026,.096,0],[.074,.067,0]],ends=[[-.105,.205,0],[-.04,.275,0],[.045,.245,0],[.105,.19,0]];for(let j=0;j<4;j++){seg("Hand_"+label+"_Finger_"+(j+1),hand,starts[j],ends[j],j===1 ? .031 : .029,8);part("Hand_"+label+"_FingerTip_"+(j+1),hand,sph(8),ends[j],[.03,.032,.031]);}seg("Hand_"+label+"_Thumb",hand,[side*.085,-.015,0],[side*.17,.055,0],.037,8);part("Hand_"+label+"_ThumbTip",hand,sph(8),[side*.17,.055,0],[.038,.04,.037]);}
  buildArm(-1,true);buildArm(1,false);
  function buildLeg(side){const label=side<0?"R":"L",hip=node("Leg_"+label+"_Hip_CTRL",root,{translation:[side*.36,-.87,0],extras:{control:"hip",side:label}});part("Leg_"+label+"_HipJoint",hip,sph(10),[0,0,0],[.07,.07,.07]);seg("Leg_"+label+"_Upper",hip,[0,-.02,0],[0,-.34,0],.037,10);const knee=node("Leg_"+label+"_Knee_CTRL",hip,{translation:[0,-.34,0],extras:{control:"knee"}});part("Leg_"+label+"_Knee",knee,sph(10),[0,0,0],[.04,.04,.04]);seg("Leg_"+label+"_Lower",knee,[0,0,0],[0,-.34,0],.032,10);const foot=node("Foot_"+label+"_CTRL",knee,{translation:[0,-.33,.015],extras:{control:"ankle"}});part("Boot_"+label+"_Sole",foot,sph(12),[0,-.05,.02],[.21,.09,.31]);part("Boot_"+label+"_Upper",foot,sph(11),[0,.01,.02],[.19,.16,.27]);part("Boot_"+label+"_ToeCap",foot,sph(3),[0,-.015,.19],[.15,.085,.13]);ring("Boot_"+label+"_Cuff",foot,[0,.105,0],[.15,.15,.75],3,[Math.SQRT1_2,0,0,Math.SQRT1_2]);}
  buildLeg(-1);buildLeg(1);
  part("Rear_WindingHub",body,sph(2),[0,0,-.30],[.16,.16,.09]);
  part("Rear_WindingCap",body,sph(3),[0,0,-.37],[.09,.09,.045]);

  function anim(name,tracks,loop=true){const samplers=[],channels=[];for(const t of tracks){const input=acc(t.times,5126,"SCALAR",null,{min:[Math.min(...t.times)],max:[Math.max(...t.times)]}),sz=t.path==="rotation"?4:3,output=acc(t.values,5126,sz===4?"VEC4":"VEC3",null),s=samplers.length;samplers.push({input,output,interpolation:t.interpolation||"LINEAR"});channels.push({sampler:s,target:{node:t.node,path:t.path}});}g.animations.push({name,samplers,channels,extras:{loop}});}
  const v3=(x,y,z)=>[x,y,z];
  anim("Idle_Breathe",[
    {node:body,path:"translation",times:[0,1,2,3,4],values:[0,0,0,0,.025,0,0,0,0,0,.025,0,0,0,0]},
    {node:body,path:"rotation",times:[0,1,2,3,4],values:[...qz(0),...qz(.012),...qz(0),...qz(-.012),...qz(0)]},
    {node:eyes[0],path:"scale",times:[0,1.45,1.54,1.64,3.2,3.29,3.4,4],values:[...v3(1,1,1),...v3(1,1,1),...v3(1,.09,1),...v3(1,1,1),...v3(1,1,1),...v3(1,.09,1),...v3(1,1,1),...v3(1,1,1)]},
    {node:eyes[1],path:"scale",times:[0,1.45,1.54,1.64,3.2,3.29,3.4,4],values:[...v3(1,1,1),...v3(1,1,1),...v3(1,.09,1),...v3(1,1,1),...v3(1,1,1),...v3(1,.09,1),...v3(1,1,1),...v3(1,1,1)]}
  ]);
  anim("Blink",[
    {node:eyes[0],path:"scale",times:[0,.09,.18,.28],values:[...v3(1,1,1),...v3(1,.055,1),...v3(1,.055,1),...v3(1,1,1)]},
    {node:eyes[1],path:"scale",times:[0,.09,.18,.28],values:[...v3(1,1,1),...v3(1,.055,1),...v3(1,.055,1),...v3(1,1,1)]}
  ],false);
  anim("Wave_Greeting",[
    {node:arms.R,path:"rotation",times:[0,.25,.55,.85,1.15,1.45,1.8],values:[...qz(0),...qz(-.35),...qz(-.85),...qz(-.25),...qz(-.85),...qz(-.25),...qz(0)]},
    {node:body,path:"rotation",times:[0,.9,1.8],values:[...qz(0),...qz(.025),...qz(0)]}
  ],false);
  anim("Pointing",[
    {node:arms.R,path:"rotation",times:[0,.35,1.2],values:[...qz(0),...qz(-.95),...qz(-.95)]},
    {node:arms.L,path:"rotation",times:[0,.35,1.2],values:[...qz(0),...qz(.20),...qz(.20)]}
  ],false);
  anim("Talk_Loop",[
    {node:smile,path:"scale",times:[0,.08,.16,.24,.32,.4],values:[...v3(1,1,1),...v3(0,0,0),...v3(0,0,0),...v3(1,1,1),...v3(0,0,0),...v3(1,1,1)]},
    {node:talk,path:"scale",times:[0,.08,.16,.24,.32,.4],values:[...v3(0,0,0),...v3(1,1,1),...v3(0,0,0),...v3(0,0,0),...v3(1,1,1),...v3(0,0,0)]}
  ]);
  anim("Laugh",[
    {node:body,path:"translation",times:[0,.16,.32,.48,.64,.8],values:[0,0,0,0,.07,0,0,0,0,0,.07,0,0,0,0,0,0,0]},
    {node:body,path:"rotation",times:[0,.16,.32,.48,.64,.8],values:[...qz(0),...qz(.04),...qz(-.04),...qz(.04),...qz(-.02),...qz(0)]},
    {node:smile,path:"scale",times:[0,.1,.8],values:[...v3(1,1,1),...v3(0,0,0),...v3(0,0,0)]},
    {node:talk,path:"scale",times:[0,.1,.8],values:[...v3(0,0,0),...v3(1,1,1),...v3(1,1,1)]}
  ],false);
  anim("Surprised",[
    {node:eyes[0],path:"scale",times:[0,.16,.9],values:[...v3(1,1,1),...v3(1.1,1.12,1),...v3(1.1,1.12,1)]},
    {node:eyes[1],path:"scale",times:[0,.16,.9],values:[...v3(1,1,1),...v3(1.1,1.12,1),...v3(1.1,1.12,1)]},
    {node:smile,path:"scale",times:[0,.12,.9],values:[...v3(1,1,1),...v3(0,0,0),...v3(0,0,0)]},
    {node:surprise,path:"scale",times:[0,.12,.9],values:[...v3(0,0,0),...v3(1,1,1),...v3(1,1,1)]}
  ],false);
  anim("Angry_Expression",[
    {node:nodeMap.Brow_R_CTRL,path:"rotation",times:[0,.18,1],values:[...qz(0),...qz(.28),...qz(.28)]},
    {node:nodeMap.Brow_L_CTRL,path:"rotation",times:[0,.18,1],values:[...qz(0),...qz(-.28),...qz(-.28)]},
    {node:smile,path:"scale",times:[0,.14,1],values:[...v3(1,1,1),...v3(0,0,0),...v3(0,0,0)]},
    {node:frown,path:"scale",times:[0,.14,1],values:[...v3(0,0,0),...v3(1,1,1),...v3(1,1,1)]}
  ],false);
  anim("Sleep_Idle",[
    {node:eyes[0],path:"scale",times:[0,.2,1.5],values:[...v3(1,1,1),...v3(1,.08,1),...v3(1,.08,1)]},
    {node:eyes[1],path:"scale",times:[0,.2,1.5],values:[...v3(1,1,1),...v3(1,.08,1),...v3(1,.08,1)]},
    {node:body,path:"rotation",times:[0,.2,1.5],values:[...qz(0),...qz(-.055),...qz(-.055)]},
    {node:body,path:"translation",times:[0,.3,1.5],values:[0,0,0,0,-.035,0,0,-.035,0]}
  ],false);
  anim("Glitch_Recovery",[
    {node:body,path:"translation",times:[0,.06,.12,.18,.24,.3,.42,.6],values:[0,0,0,.035,0,0,-.028,.012,0,.02,-.01,0,-.03,0,0,.01,.02,0,0,0,0,0,0,0]},
    {node:body,path:"rotation",times:[0,.06,.12,.18,.24,.3,.42,.6],values:[...qz(0),...qz(.08),...qz(-.1),...qz(.12),...qz(-.04),...qz(.05),...qz(0),...qz(0)]},
    {node:root,path:"scale",times:[0,.08,.16,.24,.32,.6],values:[...v3(1,1,1),...v3(1.03,.98,1),...v3(.98,1.02,1),...v3(1.02,.98,1),...v3(1,1,1),...v3(1,1,1)]}
  ],false);
  g.scenes[0].nodes=roots;g.nodes[root].extras.animations=g.animations.map(x=>x.name);
  const binary=new Uint8Array(binLength);let off=0;for(const c of chunks){binary.set(c,off);off+=c.length;}g.buffers=[{byteLength:binary.length}];
  const jb=utf8(JSON.stringify(g)),jp=align4(jb.length),bp=align4(binary.length),total=12+8+jp+8+bp,out=new Uint8Array(total),dv=new DataView(out.buffer);
  dv.setUint32(0,0x46546c67,true);dv.setUint32(4,2,true);dv.setUint32(8,total,true);dv.setUint32(12,jp,true);dv.setUint32(16,0x4e4f534a,true);out.fill(32,20,20+jp);out.set(jb,20);const bh=20+jp;dv.setUint32(bh,bp,true);dv.setUint32(bh+4,0x004e4942,true);out.set(binary,bh+8);
  return{bytes:out,stats:{nodes:g.nodes.length,meshes:g.meshes.length,materials:g.materials.length,animations:g.animations.map(x=>x.name),accessors:g.accessors.length,binaryBytes:binary.length,totalBytes:out.length}};
}
const result=buildMissMinutesGLB();const output=process.argv[2]||"assets/models/miss-minutes.glb";mkdirSync(dirname(output),{recursive:true});writeFileSync(output,result.bytes);console.log(JSON.stringify({output,...result.stats},null,2));
