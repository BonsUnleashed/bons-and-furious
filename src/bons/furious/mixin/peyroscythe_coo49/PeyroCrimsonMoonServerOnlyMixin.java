package bons.furious.mixin.peyroscythe_coo49;

import bons.furious.patch.peyroscythe_coo49.PeyroAuras;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rinko1231.peyroscythe.spellentity.CrimsonMoon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * peyroscythe_aura_server_only (Peyro's Scythes &amp; Spells 1.20.1-1.3.3, GPLv3; client; trade-off, off by default):
 * MixinExtras @WrapOperation on the updateTrackingEntities() call in the Crimson Moon (Moon Frenzy, 40 ticks; a successful add also writes the entity's moon frenzy marks) aura's tick
 * (CrimsonMoon.m_8119_): on a client level the update is left out, so the tracking list stays the empty list the aura
 * starts with and the aura's loop hands out nothing on the client; on a server level the update runs as shipped. See
 * PeyroAuras. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.rinko1231.peyroscythe.spellentity.CrimsonMoon", remap = false)
public abstract class PeyroCrimsonMoonServerOnlyMixin {
    @WrapOperation(method = "m_8119_()V",
            at = @At(value = "INVOKE", target = "Lcom/rinko1231/peyroscythe/spellentity/CrimsonMoon;updateTrackingEntities()V"),
            require = 1, allow = 1)
    private void bons$peyroscytheCoo49CrimsonMoonTracking(CrimsonMoon aura, Operation<Void> original) {
        if (PeyroAuras.clientTracking(aura)) return;
        original.call(aura);
    }
}