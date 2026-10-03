import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class TargetInventory {
    public static void main(String[] args)throws Exception {
        Path src=Path.of(args[0]);
        JsonObject inventory=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        JsonObject result=new JsonObject();
        for(var e:inventory.getAsJsonObject("guarded").entrySet()) {
            String key=e.getKey(); JsonObject row=new JsonObject();JsonArray targets=new JsonArray();boolean client=true;
            for(JsonElement el:e.getValue().getAsJsonObject().getAsJsonArray("mixins")) {
                String mixin=el.getAsString();
                if(!inventory.getAsJsonObject("mixins").get(mixin).getAsString().equals("client")) client=false;
                ClassNode n=new ClassNode();new ClassReader(Files.readAllBytes(src.resolve("build/classes/java/main/"+mixin.replace('.','/')+".class"))).accept(n,0);
                List<AnnotationNode> annotations=new ArrayList<>();if(n.visibleAnnotations!=null)annotations.addAll(n.visibleAnnotations);if(n.invisibleAnnotations!=null)annotations.addAll(n.invisibleAnnotations);
                for(var a:annotations)if(a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                    for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals("value")||a.values.get(i).equals("targets"))
                        for(Object target:(List<?>)a.values.get(i+1))targets.add(target instanceof Type t?t.getClassName():target.toString());
            }
            row.addProperty("client",client);row.add("targets",targets);result.add(key,row);
        }
        Files.writeString(src.resolve("src/probe/resources/probe-targets.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println(result.size()+" target groups written");
    }
}
