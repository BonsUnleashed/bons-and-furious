package bons.furious.mixin.celestialcore_precheck_c49;

import bons.furious.patch.celestialcore_precheck_c49.TransformPrecheck;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * celestialcore_transform_lookup_precheck (Celestial Core 1.6.1 and 1.5.8, both sides; the handler acts on the server): a
 * MixinSquared @TargetHandler @ModifyExpressionValue of the level.isClientSide() test at the first return of Celestial
 * Core's ItemEntityMixin.celestial_core$tick$recipe (merged into ItemEntity, run at the end of every tick): it reads true
 * when TransformPrecheck shows that no transformation can happen for this item on this tick, so the handler returns before
 * building its container and asking the recipe manager. Our own logic only (Celestial Core is LGPL-2.1; none of its code is
 * carried). Priority 1500: after Celestial Core's mixin.
 */
@Mixin(value = ItemEntity.class, priority = 1500, remap = false)
public abstract class CelestialTransformPrecheckMixin {
    @TargetHandler(mixin = "com.xiaoyue.celestial_core.mixin.ItemEntityMixin", name = "celestial_core$tick$recipe")
    @ModifyExpressionValue(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_5776_()Z"),
            require = 0, expect = 0, allow = 1)
    private boolean bons$celestialcore_precheck_c49Skip(boolean clientSide) {
        return clientSide || TransformPrecheck.nothingCanHappen((ItemEntity) (Object) this);
    }
}
