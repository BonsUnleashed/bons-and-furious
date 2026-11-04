package bons.furious.mixin.l2complements_coo49;

import bons.furious.patch.l2complements_coo49.BreakerSet;
import java.util.Set;
import java.util.UUID;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * l2complements_breaker_set_concurrent (L2 Complements 2.6.1, both sides; kind fix): at the RETURN of
 * RangeDiggingEnchantment's static initialiser (which only creates BREAKER as a HashSet), BREAKER is replaced by
 * BreakerSet.replace's copy-on-write SnapshotSet (the field's final flag is removed by @Mutable; the assignment happens
 * inside the static initialiser, before any other code can see the field). Our own logic only (All Rights Reserved target;
 * none of its code is carried).
 */
@Mixin(targets = "dev.xkmc.l2complements.content.enchantment.digging.RangeDiggingEnchantment", remap = false)
public abstract class BreakerSetMixin {
    @Shadow
    @Final
    @Mutable
    private static Set<UUID> BREAKER;

    @Inject(method = "<clinit>", at = @At("RETURN"), require = 1, allow = 1)
    private static void bons$l2complements_coo49Breaker(CallbackInfo ci) {
        if (BreakerSet.enabled) BREAKER = BreakerSet.replace(BREAKER);
    }
}
