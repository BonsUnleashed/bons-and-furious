package bons.furious.mixin.benssharks;

import bons.furious.patch.benssharks.Dispatch;
import net.minecraft.world.entity.animal.Dolphin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): Ben's DolphinEatSardine only ever acts on dolphins: run it from the
 * dolphin's own tick instead of every living entity's. Helper: {@link Dispatch}.
 */
@Mixin(value = Dolphin.class, remap = false)
public abstract class DolphinTickMixin {
    @Inject(method = "m_8119_()V", at = @At("HEAD"), remap = false)
    private void bons$sharksDolphinTick(CallbackInfo ci) {
        Dispatch.dolphinTick((Dolphin) (Object) this);
    }
}
