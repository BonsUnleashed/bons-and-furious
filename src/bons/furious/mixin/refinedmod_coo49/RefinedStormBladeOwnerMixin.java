package bons.furious.mixin.refinedmod_coo49;

import bons.furious.patch.refinedmod_coo49.RefinedFixes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * refinedmod_storm_blade_ownerless_discard (Refined Mod 1.1.6, All Rights Reserved; server; since 1.0.49; change, gameplay
 * fix, off by default): at the head of StormBladeCollider.onHitEntity (m_5790_), on a server level, a collider whose caster
 * is not a living entity (gone) is discarded and the method returns, before its impact particles. Helper RefinedFixes.
 */
@Mixin(targets = "net.refinedrain.refinedmod.entity.spells.storm_blade.StormBladeCollider", remap = false)
public abstract class RefinedStormBladeOwnerMixin {
    @Inject(method = "m_5790_(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$refinedmodCoo49OwnerlessDiscard(EntityHitResult hit, CallbackInfo ci) {
        if (RefinedFixes.discardOwnerless((Projectile) (Object) this)) ci.cancel();
    }
}
