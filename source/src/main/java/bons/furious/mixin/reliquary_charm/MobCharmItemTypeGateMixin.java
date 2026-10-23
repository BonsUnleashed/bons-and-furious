package bons.furious.mixin.reliquary_charm;

import bons.furious.patch.reliquary_charm.ReliquaryCharmGate;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import reliquary.item.MobCharmItem;

/**
 * reliquary_mob_charm_type_gate (Reliquary 2.0.45.1248, licence unstated: no Reliquary code is carried; server; since
 * 1.0.46).
 *
 * MobCharmItem.onLivingUpdate(LivingTickEvent) runs for every living entity every tick: for a server-side Mob it looks for a
 * real player (target, last attacker, five brain memories) and, only when one is found, for the charm definition of the
 * mob's type; without one nothing happens. An @Inject at its HEAD returns at once when ReliquaryCharmGate says the mob's
 * type has no charm definition (see there for why every skipped read is pure); otherwise Reliquary's handler runs
 * unchanged. The CallbackInfo is the only allocation (scalar-replaced once inlined).
 */
@Mixin(value = MobCharmItem.class, remap = false)
public abstract class MobCharmItemTypeGateMixin {
    @Inject(method = "onLivingUpdate", at = @At("HEAD"), cancellable = true, remap = false)
    private void bons$noCharmForType(EntityTickEvent.Pre event, CallbackInfo ci) {
        if (event.getEntity() instanceof LivingEntity living && ReliquaryCharmGate.noCharm(living)) ci.cancel();
    }
}
