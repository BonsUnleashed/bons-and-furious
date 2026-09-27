import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
/** Relocate our own helper classes; upstream dependencies are not copied. */
public final class Relocate {
 public static void main(String[] args)throws Exception{
  Map<String,String> names=new HashMap<>();for(int i=2;i<args.length;i+=2)names.put(args[i],args[i+1]);
  Path root=Path.of(args[0]),out=Path.of(args[1]);
  try(var files=Files.walk(root)){for(Path p:files.filter(x->x.toString().endsWith(".class")).toList()){
   var reader=new ClassReader(Files.readAllBytes(p));var writer=new ClassWriter(0);
   reader.accept(new ClassRemapper(writer,new SimpleRemapper(names)),0);
   Path dest=out.resolve(names.getOrDefault(reader.getClassName(),reader.getClassName())+".class");
   Files.createDirectories(dest.getParent());Files.write(dest,writer.toByteArray());
  }}
 }
}
