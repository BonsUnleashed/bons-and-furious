package bons.furious.mixin.chimes_coo49;

import bons.furious.patch.chimes_coo49.ChimesScans;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * chimes_client_phantom_scan_skip (Minecraft 1.20.1 Mob on Forge 47.4.28, client; part of the Chimes 2.0.1 client skip).
 * At every return of Mob.setTarget (m_6710_) this reads the target field (f_21362_) as setTarget left it (after Forge's
 * LivingChangeTargetEvent and after every other mod's injector into setTarget: priority 2000 places this one last) and
 * tells ChimesScans when a client-side Phantom now has a target; the client then searches as Chimes does for the rest of
 * the session. Reads only; nothing the game sees changes. ChimesPlugin also looks at the merged Mob class once this mixin
 * is applied (getTarget still the plain field read, no other writer of the field).
 */
@Mixin(value = Mob.class, priority = 2000, remap = false)
public abstract class MobClientTargetWatchMixin {
    @Shadow
    private LivingEntity f_21362_;

    @Inject(method = "m_6710_(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("RETURN"), require = 1)
    private void bons$chimesWatchTarget(LivingEntity requested, CallbackInfo ci) {
        LivingEntity now = this.f_21362_;
        if (now != null) ChimesScans.targetSet((Mob) (Object) this, now);
    }
}
