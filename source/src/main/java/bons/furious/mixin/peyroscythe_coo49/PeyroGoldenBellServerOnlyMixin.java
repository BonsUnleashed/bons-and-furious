package bons.furious.mixin.peyroscythe_coo49;

import bons.furious.patch.peyroscythe_coo49.PeyroAuras;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * peyroscythe_aura_server_only (Peyro's Scythes &amp; Spells 1.21.1-1.3.2 for NeoForge, GPLv3; client; trade-off, off by
 * default): MixinExtras @WrapOperation on the updateTrackingEntities() call in the Golden Bell (Holy Bell Suppression, 40
 * ticks) aura's tick (GoldenBellEntity.tick): on a client level the update is left out, so the tracking list stays the
 * empty list the aura starts with and the aura's loop hands out nothing on the client; on a server level the update runs as
 * shipped. The aura (the call's receiver) is taken as @Coerce Object. See PeyroAuras. Our own logic only; nothing of the
 * mod is carried.
 */
@Mixin(targets = "com.rinko1231.peyroscythe.spellentity.holy.GoldenBellEntity", remap = false)
public abstract class PeyroGoldenBellServerOnlyMixin {
    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lcom/rinko1231/peyroscythe/spellentity/holy/GoldenBellEntity;updateTrackingEntities()V"),
            require = 1, allow = 1)
    private void bons$peyroscytheCoo49GoldenBellTracking(@Coerce Object aura, Operation<Void> original) {
        if (PeyroAuras.clientTracking(aura)) return;
        original.call(aura);
    }
}
