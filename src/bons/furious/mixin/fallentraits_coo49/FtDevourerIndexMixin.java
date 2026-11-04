package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtDevourers;
import dev.xkmc.l2hostility.content.capability.mob.MobTraitCap;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fallentraits_devourer_index (Fallen Traits & Relics 1.0.0 with L2 Hostility 2.5.19; server side; trade-off, off by
 * default): @Inject at the end (TAIL; the method has no other return) of L2 Hostility's MobTraitCap.tick(mob) records the
 * mob in the Devourer index while its Devourer level is above 0 and forgets it otherwise (FtDevourers.seen; reads only).
 * Our own logic only.
 */
@Mixin(targets = "dev.xkmc.l2hostility.content.capability.mob.MobTraitCap", remap = false)
public abstract class FtDevourerIndexMixin {
    @Inject(method = "tick(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("TAIL"), require = 1, allow = 1)
    private void bons$fallentraitsCoo49DevourerSeen(LivingEntity mob, CallbackInfo ci) {
        FtDevourers.seen((MobTraitCap) (Object) this, mob);
    }
}
