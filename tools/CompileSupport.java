import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Local compiler view of the fields the runtime adapter adds; never packaged. */
public final class CompileSupport {
 public static void main(String[] args)throws Exception{
  String name="org/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet.class";
  try(var z=new ZipFile(args[0])){
   var c=new ClassNode();new ClassReader(z.getInputStream(z.getEntry(name)).readAllBytes()).accept(c,0);
   for(String f:new String[]{"acVsMods","acVsExtents","acVsWorld"}){
    if(c.fields.stream().anyMatch(x->x.name.equals(f)))throw new IllegalArgumentException("Expected unmodified Valkyrien Skies dependency");
    c.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_TRANSIENT,f,f.equals("acVsMods")?"J":"Ljava/lang/Object;",null,null));
   }
   var w=new ClassWriter(0);c.accept(w);Path p=Path.of(args[1],name);Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());
  }
 }
}
