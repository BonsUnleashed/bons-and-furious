import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.tree.*;

/** Structural inventory only: a present method is not a semantic port qualification. */
public final class PortAudit {
    public static void main(String[] a) throws Exception {
        var roots = new ArrayList<Path>();
        for (int i=2;i<a.length;i++) roots.add(Path.of(a[i]));
        var classes = new GuardTool.Classes(roots);
        JsonObject results = new JsonObject();
        for (Path p : GuardTool.jsonFiles(Path.of(a[0]), ".json")) {
            JsonObject doc=JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            for(var entry:doc.getAsJsonObject("keys").entrySet()) {
                JsonObject key=entry.getValue().getAsJsonObject();
                JsonArray gs=new JsonArray(); int missing=0;
                for(JsonElement el:key.getAsJsonArray("guard")) {
                    JsonObject g=el.getAsJsonObject().deepCopy();
                    String fp;
                    try { fp=GuardTool.fingerprint(classes,g); }
                    catch(Exception e) { fp=null;g.addProperty("error",e.toString()); }
                    g.addProperty("found",fp!=null);
                    if(fp!=null)g.addProperty("current_sha256",fp);
                    else {
                        missing++;
                        ClassNode c=classes.node(g.get("class").getAsString());
                        JsonArray methods=new JsonArray();
                        if(c!=null)for(MethodNode m:c.methods)methods.add(m.name+m.desc);
                        g.add("available_methods",methods);
                    }
                    gs.add(g);
                }
                JsonObject row=new JsonObject();row.addProperty("file",p.getFileName().toString());row.addProperty("missing",missing);row.add("guards",gs);results.add(entry.getKey(),row);
                System.out.println(entry.getKey()+": "+(gs.size()-missing)+"/"+gs.size()+" methods present");
            }
        }
        Files.writeString(Path.of(a[1]),new GsonBuilder().setPrettyPrinting().create().toJson(results)+"\n");
    }
}
