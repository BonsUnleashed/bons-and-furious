package bons.pure.pacing.mixin;
import bons.pure.pacing.FramePacer;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.pipeline.RenderCall;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reuse the vanilla limiter once, before display update, absorbing variable tick/render work. Since 1.0.41+mc1.21.1 (as
 * Forge 1.0.41) the limiter call is queued for the display update (RenderSystem.flipFrame replays the render queue just
 * before the buffer swap) instead of made from here, so no method of ours is on the stack while it waits and GLFW
 * dispatches input.
 */
@Mixin(value=net.minecraft.client.Minecraft.class,remap=false)
public abstract class MinecraftPacingMixin {
 @Shadow private int getFramerateLimit(){throw new AssertionError();}
 @Inject(method="runTick(Z)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/Window;updateDisplay()V"))
 private void bonspure$beforeDisplay(CallbackInfo ci,@Share("bons$limiter") LocalRef<RenderCall> queued){
  if(!FramePacer.isEnabled())return;
  int limit=getFramerateLimit();if(limit>=260)return;
  queued.set(FramePacer.queueLimiter(limit));
 }
 /** The original call after the display update runs only when nothing was queued, or the queued call never ran. */
 @WrapWithCondition(method="runTick(Z)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
 private boolean bonspure$afterDisplay(int limit,@Share("bons$limiter") LocalRef<RenderCall> queued){
  RenderCall call=queued.get();
  return call==null||FramePacer.withdraw(call);
 }
}
