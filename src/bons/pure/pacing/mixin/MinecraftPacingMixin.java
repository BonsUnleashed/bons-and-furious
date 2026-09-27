package bons.pure.pacing.mixin;
import bons.pure.pacing.FramePacer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuse the vanilla limiter once, before display update, absorbing variable tick/render work. */
@Mixin(value=net.minecraft.client.Minecraft.class,remap=false)
public abstract class MinecraftPacingMixin {
 @Unique private boolean bonspure$paced;
 @Shadow private int m_91275_(){throw new AssertionError();}
 @Inject(method="m_91383_(Z)V",at=@At("HEAD"))
 private void bonspure$begin(CallbackInfo ci){bonspure$paced=false;}
 @Inject(method="m_91383_(Z)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/Window;m_85435_()V"))
 private void bonspure$beforeDisplay(CallbackInfo ci){
  if(!FramePacer.isEnabled())return;
  int limit=m_91275_();if(limit>=260)return;
  RenderSystem.limitDisplayFPS(limit);bonspure$paced=true;
 }
 @Redirect(method="m_91383_(Z)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
 private void bonspure$afterDisplay(int limit){if(!bonspure$paced)RenderSystem.limitDisplayFPS(limit);}
}
