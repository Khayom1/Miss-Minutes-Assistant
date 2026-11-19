import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Conservative app-local bytecode reachability graph.
 * Direct invoke edges are exact at the bytecode symbolic-reference level.
 * Virtual/interface calls are expanded to all app classes that declare a compatible
 * name+descriptor method; this is intentionally conservative, not whole-program proof.
 */
public final class BytecodeReachability {
  record Method(String owner, String name, String desc) {
    String key() { return owner.replace('/','.') + "#" + name + desc; }
  }
  static final Map<String, Set<Method>> methodsByOwner = new HashMap<>();
  static final Map<Method, Set<Method>> edges = new HashMap<>();
  static final Map<Method, Set<String>> virtualCalls = new HashMap<>();
  static final Map<String, List<Method>> virtualTargets = new HashMap<>();
  static final Set<Method> methods = new TreeSet<>(Comparator.comparing(Method::key));
  static final Set<String> classes = new TreeSet<>();
  static final Set<Method> roots = new TreeSet<>(Comparator.comparing(Method::key));
  static String sig(String n,String d){ return n+d; }

  static void scan(Path root) throws Exception {
    if (!Files.exists(root)) return;
    try (var stream=Files.walk(root)) {
      for(Path p:(Iterable<Path>)stream.filter(x->x.toString().endsWith(".class"))::iterator) {
        ClassReader cr=new ClassReader(Files.readAllBytes(p));
        String owner=cr.getClassName(); classes.add(owner);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
          public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
            Method from=new Method(owner,name,desc); methods.add(from);
            methodsByOwner.computeIfAbsent(owner,k->new HashSet<>()).add(from);
            virtualTargets.computeIfAbsent(sig(name,desc),k->new ArrayList<>()).add(from);
            return new MethodVisitor(Opcodes.ASM9) {
              public void visitMethodInsn(int opcode,String target,String mn,String md,boolean itf) {
                edges.computeIfAbsent(from,k->new HashSet<>()).add(new Method(target,mn,md));
                if(opcode==Opcodes.INVOKEVIRTUAL || opcode==Opcodes.INVOKEINTERFACE)
                  virtualCalls.computeIfAbsent(from,k->new HashSet<>()).add(sig(mn,md));
              }
              public void visitInvokeDynamicInsn(String n,String d,Handle bsm,Object... args) {
                if(bsm.getTag()>=Opcodes.H_INVOKEVIRTUAL && bsm.getTag()<=Opcodes.H_INVOKEINTERFACE)
                  edges.computeIfAbsent(from,k->new HashSet<>()).add(new Method(bsm.getOwner(),bsm.getName(),bsm.getDesc()));
                for(Object a:args) if(a instanceof Handle h)
                  edges.computeIfAbsent(from,k->new HashSet<>()).add(new Method(h.getOwner(),h.getName(),h.getDesc()));
              }
            };
          }
        }, ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
      }
    }
  }
  static String normalize(String name,String pkg) {
    if(name.startsWith(".")) return pkg+name;
    if(!name.contains(".")) return pkg+"."+name;
    return name;
  }
  static void manifestRoots(Path manifest) throws Exception {
    if(!Files.exists(manifest)) return;
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    Document d=f.newDocumentBuilder().parse(manifest.toFile());
    Element app=(Element)d.getElementsByTagName("application").item(0);
    if(app==null)return;
    String pkg=d.getDocumentElement().getAttribute("package");
    if(pkg.isBlank()) pkg="com.khayom1.missminutes";
    String[] tags={"activity","activity-alias","service","receiver","provider"};
    for(String tag:tags) {
      NodeList ns=app.getElementsByTagName(tag);
      for(int i=0;i<ns.getLength();i++) {
        Element e=(Element)ns.item(i);
        String n=e.getAttributeNS("http://schemas.android.com/apk/res/android","name");
        if(n.isBlank()) n=e.getAttribute("android:name");
        if(n.isBlank())continue;
        String cls=normalize(n,pkg).replace('.','/');
        for(Method m:methodsByOwner.getOrDefault(cls,Set.of())) roots.add(m);
      }
    }
  }
  static void walk(Set<Method> reachable) {
    ArrayDeque<Method> q=new ArrayDeque<>(roots);
    while(!q.isEmpty()) {
      Method m=q.removeFirst();
      if(!reachable.add(m))continue;
      Set<Method> nextMethods=new HashSet<>(edges.getOrDefault(m,Set.of()));
      for(String signature:virtualCalls.getOrDefault(m,Set.of()))
        nextMethods.addAll(virtualTargets.getOrDefault(signature,List.of()));
      for(Method next:nextMethods) {
        if(methods.contains(next) && !reachable.contains(next))q.addLast(next);
      }
    }
  }
  public static void main(String[] a) throws Exception {
    if(a.length<3) throw new IllegalArgumentException("usage: BytecodeReachability <manifest> <out.json> <class-dir>...");
    for(int i=2;i<a.length;i++) scan(Path.of(a[i]));
    manifestRoots(Path.of(a[0]));
    // MainActivity is an app entry root even if a manifest merger changes its declaration.
    for(Method m:methods) if(m.owner().equals("com/khayom1/missminutes/MainActivity")) roots.add(m);
    Set<Method> reachable=new TreeSet<>(Comparator.comparing(Method::key)); walk(reachable);
    List<String> unreachable=new ArrayList<>();
    for(Method m:methods) if(!reachable.contains(m)) unreachable.add(m.key());
    StringBuilder j=new StringBuilder("{\n  \"analysis\": \"app-local JVM bytecode graph; conservative virtual expansion\",\n");
    j.append("  \"class_count\": ").append(classes.size()).append(",\n  \"method_count\": ").append(methods.size()).append(",\n");
    j.append("  \"root_count\": ").append(roots.size()).append(",\n  \"reachable_method_count\": ").append(reachable.size()).append(",\n");
    j.append("  \"unreachable_candidate_count\": ").append(unreachable.size()).append(",\n  \"roots\": [");
    int i=0; for(Method m:roots){if(i++>0)j.append(",");j.append("\n    \"").append(m.key()).append("\"");}
    j.append("\n  ],\n  \"unreachable_candidates\": ["); i=0;
    for(String s:unreachable){if(i++>0)j.append(",");j.append("\n    \"").append(s.replace("\\","\\\\").replace("\"","\\\"")).append("\"");}
    j.append("\n  ]\n}\n");
    Path out=Path.of(a[1]);Files.createDirectories(out.getParent());Files.writeString(out,j);
    System.out.println("Classes="+classes.size()+" methods="+methods.size()+" roots="+roots.size()+" reachable="+reachable.size()+" candidates="+unreachable.size());
  }
}