package bons.furious.mixin.touchnmotion_c49;

import bons.furious.patch.touchnmotion_c49.TnmFrame;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * touchnmotion_pose_block_memo (Touch'n Motion 1.0.4, client, since 1.0.49): at the start of every frame
 * (GameRenderer.render, m_109093_) marks that the memo switch is applied, so the direct probes of that frame keep their
 * block reads (TnmFrame, TnmMemo). Minecraft code is not changed.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class TnmMemoFrameMixin {
    @Inject(method = "m_109093_", at = @At("HEAD"))
    private void bons$touchnmotionC49MemoArm(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        TnmFrame.armMemo();
    }
}
