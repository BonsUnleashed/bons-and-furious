package bons.furious.mixin.benssharks;

import bons.furious.patch.benssharks.Dispatch;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): GreaterAxodileEntity declares tick() but not baseTick(): hook the head of
 * its tick (same tick, a moment earlier). Helper: {@link Dispatch}.
 */
@Mixin(targets = "net.mcreator.sharks.entity.GreaterAxodileEntity", remap = false)
public abstract class GreaterAxodileTickMixin {
    @Inject(method = "m_8119_()V", at = @At("HEAD"), remap = false)
    private void bons$sharkTick(CallbackInfo ci) {
        Dispatch.sharkTick((LivingEntity) (Object) this);
    }
}
