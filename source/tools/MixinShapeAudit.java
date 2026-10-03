import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class MixinShapeAudit {
    static GuardTool.Classes classes;
    static List<AnnotationNode> annotations(List<AnnotationNode> a,List<AnnotationNode> b) {
        var out=new ArrayList<AnnotationNode>();if(a!=null)out.addAll(a);if(b!=null)out.addAll(b);return out;
    }
    static List<String> targets(ClassNode n) {
        var out=new ArrayList<String>();
        for(var a:annotations(n.visibleAnnotations,n.invisibleAnnotations))if(a.desc.endsWith("/Mixin;"))
            for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals("value")||a.values.get(i).equals("targets"))
                for(Object target:(List<?>)a.values.get(i+1))out.add(target instanceof Type t?t.getInternalName():target.toString().replace('.','/'));
        return out;
    }
    static boolean has(String owner,String name,String desc,boolean field,Set<String> seen)throws Exception {
        if(owner==null || !seen.add(owner))return false;
        var c=classes.node(owner);if(c==null)return false;
        if(field) {for(var f:c.fields)if(f.name.equals(name)&&f.desc.equals(desc))return true;}
        else {for(var m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return true;}
        if(has(c.superName,name,desc,field,seen))return true;
        for(var parent:c.interfaces)if(has(parent,name,desc,field,seen))return true;
        return false;
    }
    static Object value(AnnotationNode a,String key) {
        if(a.values!=null)for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals(key))return a.values.get(i+1);
        return null;
    }
    static List<?> list(Object v) {return v==null?List.of():v instanceof List<?> l?l:List.of(v);}
    static int anchors(MethodNode method,AnnotationNode at) {
        String kind=String.valueOf(value(at,"value")),target=String.valueOf(value(at,"target"));
        if(!kind.startsWith("INVOKE") && !kind.equals("FIELD") && !kind.equals("NEW"))return -1;
        int count=0;
        for(var n:method.instructions) {
            if(n instanceof MethodInsnNode m && kind.startsWith("INVOKE") && ("L"+m.owner+";"+m.name+m.desc).equals(target))count++;
            if(n instanceof FieldInsnNode f && kind.equals("FIELD") && ("L"+f.owner+";"+f.name+":"+f.desc).equals(target))count++;
            if(n instanceof MethodInsnNode m && kind.equals("NEW") && m.name.equals("<init>")) {
                String constructor=m.desc.substring(0,m.desc.length()-1)+"L"+m.owner+";";
                if(constructor.equals(target)||target.equals("L"+m.owner+";")||target.equals(m.owner))count++;
            }
        }
        return count;
    }
    public static void main(String[] args)throws Exception {
        Path source=Path.of(args[0]).toAbsolutePath().normalize();var roots=new ArrayList<Path>();for(int i=2;i<args.length;i++)roots.add(Path.of(args[i]));classes=new GuardTool.Classes(roots);
        var doc=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();JsonArray failures=new JsonArray();int checks=0;
        for(var entry:doc.getAsJsonObject("mixins").entrySet()) {
            String mixin=entry.getKey();ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(source.resolve("build/classes/java/main/"+mixin.replace('.','/')+".class"))).accept(c,0);
            for(String target:targets(c)) {
                for(var f:c.fields)for(var a:annotations(f.visibleAnnotations,f.invisibleAnnotations))if(a.desc.endsWith("/Shadow;")) {
                    checks++;if(!has(target,f.name,f.desc,true,new HashSet<>()))failures.add(mixin+" -> "+target+" field "+f.name+" "+f.desc);
                }
                for(var m:c.methods)for(var a:annotations(m.visibleAnnotations,m.invisibleAnnotations))if(a.desc.endsWith("/Shadow;")||a.desc.endsWith("/Overwrite;")) {
                    checks++;boolean found=has(target,m.name,m.desc,false,new HashSet<>());
                    if(!found&&a.values!=null)for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals("aliases"))
                        for(var alias:(List<?>)a.values.get(i+1))if(has(target,alias.toString(),m.desc,false,new HashSet<>()))found=true;
                    if(!found)failures.add(mixin+" -> "+target+" method "+m.name+m.desc);
                }
                ClassNode targetNode=classes.node(target);
                for(var m:c.methods) {
                    var ans=annotations(m.visibleAnnotations,m.invisibleAnnotations);
                    if(ans.stream().anyMatch(a->a.desc.endsWith("/TargetHandler;")))continue;
                    for(var a:ans) {
                        Object selectors=value(a,"method");if(selectors==null)continue;
                        for(Object raw:list(selectors)) {
                            String selector=raw.toString();if(selector.contains("*")||selector.startsWith("@"))continue;
                            List<MethodNode> matches=targetNode.methods.stream().filter(t->selector.equals(t.name)||selector.equals(t.name+t.desc)).toList();
                            if(matches.isEmpty()) {failures.add(mixin+" injector "+m.name+" unresolved method "+selector+" (possibly merged by another mod)");continue;}
                            for(Object rawAt:list(value(a,"at")))if(rawAt instanceof AnnotationNode at) {
                                int found=matches.stream().mapToInt(t->anchors(t,at)).sum();
                                int ordinal=value(at,"ordinal") instanceof Integer i?i:-1;
                                if(found==0 || (found>0 && ordinal>=found))failures.add(mixin+" injector "+m.name+" missing "+value(at,"value")+" "+value(at,"target")+" in "+selector+" ordinal="+ordinal);
                                if(found>=0)checks++;
                            }
                        }
                    }
                }
            }
        }
        var result=new JsonObject();result.addProperty("checks",checks);result.add("unresolved",failures);
        Files.writeString(source.getParent().resolve("evidence/mixin-shape-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println(checks+" member signatures checked; "+failures.size()+" unresolved");for(var f:failures)System.out.println(f.getAsString());
    }
}
