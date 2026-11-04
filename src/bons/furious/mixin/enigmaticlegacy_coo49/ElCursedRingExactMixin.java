package bons.furious.mixin.enigmaticlegacy_coo49;

import bons.furious.patch.enigmaticlegacy_coo49.CursedRingScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * enigmaticlegacy_cursed_ring_anger_candidates_exact (Enigmatic Legacy 2.30.1, server side; exact; Enigmatic Legacy's own
 * licence, treated as All Rights Reserved: no code of the mod is carried). MixinExtras @WrapOperation on the
 * player.getVisibilityPercent(entity) call of CursedRing.curioTick: for an entity that is neither a Piglin nor a
 * NeutralMob the call is left out (the loop gets 1.0 and that pass ends in "continue" whatever the value) only while the
 * call can have no effect besides its value: no LivingVisibilityEvent listener (Forge listener census), plain
 * getVisibilityPercent and ForgeHooks.getEntityVisibilityMultiplier (the group plugin's look at the merged classes), and a
 * player class that does not override getVisibilityPercent (CursedRingScan.skipExact). Otherwise the call runs as shipped.
 */
@Mixin(targets = "com.aizistral.enigmaticlegacy.items.CursedRing", remap = false)
public abstract class ElCursedRingExactMixin {
    @WrapOperation(method = "curioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_20968_(Lnet/minecraft/world/entity/Entity;)D"),
            require = 1, allow = 1)
    private double bons$enigmaticlegacyCoo49RingExact(Player player, Entity checked, Operation<Double> original) {
        return CursedRingScan.skipExact(player, checked) ? CursedRingScan.SKIPPED : original.call(player, checked);
    }
}
