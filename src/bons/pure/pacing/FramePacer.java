package bons.pure.pacing;
import com.mojang.blaze3d.pipeline.RenderCall;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Queue;
import org.apache.logging.log4j.LogManager;
/** Runtime switch for qualification and compatibility; no FPS target is imposed. */
public final class FramePacer {
 private static volatile boolean enabled=Boolean.parseBoolean(System.getProperty("bons_and_furious.framePacing","true"));
 private FramePacer(){}
 public static boolean isEnabled(){return enabled;}
 public static void setEnabled(boolean value){enabled=value;}
 /**
  * Queues RenderSystem.limitDisplayFPS(limit) for the next replay of the render queue (the display update, just before
  * the buffer swap). The call is bound straight to the vanilla method, as a method reference would be, so neither the
  * wait nor input GLFW dispatches during it runs under a frame of ours: a crash in a menu click no longer names this
  * mod as the suspect. Null when the call cannot be built; the limiter then runs after the display update as shipped.
  */
 public static RenderCall queueLimiter(int limit){
  MethodHandle factory=Limiter.factory;
  if(factory==null)return null;
  RenderCall call;
  try{call=(RenderCall)factory.invokeExact(limit);}catch(Throwable t){Limiter.off(t);return null;}
  RenderSystem.recordRenderCall(call);
  return call;
 }
 /** True when the call had not run yet and was taken back out of the render queue. */
 public static boolean withdraw(RenderCall call){return Limiter.queue.remove(call);}
 /** Built on the render thread at the first capped frame, never while mixins are being set up. */
 private static final class Limiter{
  static final Queue<RenderCall> queue;
  static volatile MethodHandle factory;
  static{
   Queue<RenderCall> q=null;MethodHandle f=null;
   try{
    Field field=RenderSystem.class.getDeclaredField("recordingQueue");field.setAccessible(true);
    @SuppressWarnings("unchecked") Queue<RenderCall> recording=(Queue<RenderCall>)field.get(null);
    Method execute=null;
    for(Method m:RenderCall.class.getMethods())if(Modifier.isAbstract(m.getModifiers()))execute=m;
    MethodHandles.Lookup lookup=MethodHandles.lookup();
    MethodHandle limiter=lookup.findStatic(RenderSystem.class,"limitDisplayFPS",MethodType.methodType(void.class,int.class));
    f=LambdaMetafactory.metafactory(lookup,execute.getName(),MethodType.methodType(RenderCall.class,int.class),
      MethodType.methodType(void.class),limiter,MethodType.methodType(void.class)).getTarget();
    q=recording;
   }catch(Throwable t){warn(t);f=null;}
   queue=q;factory=f;
  }
  static void off(Throwable t){if(factory!=null){factory=null;warn(t);}}
  private static void warn(Throwable t){
   LogManager.getLogger("Bons and Furious").warn("Bons and Furious: frame_pacing could not queue the FPS limiter for the display update ({}); the limiter runs after the display update as in vanilla",t.toString());
  }
 }
}
