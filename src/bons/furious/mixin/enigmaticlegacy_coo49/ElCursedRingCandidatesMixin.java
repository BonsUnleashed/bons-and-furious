package bons.furious.mixin.enigmaticlegacy_coo49;

import bons.furious.patch.enigmaticlegacy_coo49.CursedRingScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * enigmaticlegacy_cursed_ring_anger_candidates (Enigmatic Legacy 2.30.1, server side; change, off by default; Enigmatic
 * Legacy's own licence, treated as All Rights Reserved: no code of the mod is carried). MixinExtras @WrapOperation on the
 * player.getVisibilityPercent(entity) call of CursedRing.curioTick (its only call): for an entity that is neither a Piglin
 * nor a NeutralMob the call is left out and the loop gets 1.0; that loop pass ends in "continue" whatever the value
 * (CursedRingScan). What differs: LivingVisibilityEvent listeners and code merged into getVisibilityPercent no longer see
 * those pairs from the ring.
 */
@Mixin(targets = "com.aizistral.enigmaticlegacy.items.CursedRing", remap = false)
public abstract class ElCursedRingCandidatesMixin {
    @WrapOperation(method = "curioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_20968_(Lnet/minecraft/world/entity/Entity;)D"),
            require = 1, allow = 1)
    private double bons$enigmaticlegacyCoo49RingCandidates(Player player, Entity checked, Operation<Double> original) {
        return CursedRingScan.skipCandidates(checked) ? CursedRingScan.SKIPPED : original.call(player, checked);
    }
}
