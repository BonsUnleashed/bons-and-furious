package bons.furious.mixin.fluidwalk_c2;

import bons.furious.patch.fluidwalk_c2.FluidWalkCursor;
import bons.furious.patch.fluidwalk_c2.RelicsFluidWalk;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * relics_fluid_walk_scan (Relics 1.20.1-0.8.0.13, both sides). Relics is All Rights Reserved: this injection carries
 * none of its code, and Relics' handler runs unchanged for every case the check below does not settle.
 *
 * A MixinSquared injection into Relics' EntityMixin.fluidCollision handler (merged into Entity, run from Entity.move
 * after collide()), without a CallbackInfo. The handler starts with "if (!(this instanceof LivingEntity)) return
 * original;". For a living entity, while the runtime switch is on, that test reads false exactly when
 * RelicsFluidWalk.dryEverywhere shows that all 12 cells the handler probes hold no fluid, in which case the handler's own
 * answer is the original vector with no event: it then returns that vector at the same return, without building its
 * offset table and 12 BlockPos. The cursor of those reads is kept per entity (one field, created on first use).
 */
@Mixin(value = Entity.class, priority = 1500, remap = false)
public abstract class RelicsFluidWalkMixin {
    @Unique
    private FluidWalkCursor bons$relicsFluidWalkCursor;

    @TargetHandler(mixin = "it.hurts.sskirillss.relics.mixin.EntityMixin", name = "fluidCollision")
    @ModifyExpressionValue(method = "@MixinSquared:Handler",
            at = @At(value = "CONSTANT", args = "classValue=net/minecraft/world/entity/LivingEntity", ordinal = 0))
    private boolean bons$skipDryFluidWalk(boolean isLiving, @Local(argsOnly = true) Vec3 original) {
        if (!isLiving || !RelicsFluidWalk.enabled) return isLiving;
        FluidWalkCursor cursor = this.bons$relicsFluidWalkCursor;
        if (cursor == null) this.bons$relicsFluidWalkCursor = cursor = new FluidWalkCursor();
        return !RelicsFluidWalk.dryEverywhere((LivingEntity) (Object) this, original, cursor);
    }
}
