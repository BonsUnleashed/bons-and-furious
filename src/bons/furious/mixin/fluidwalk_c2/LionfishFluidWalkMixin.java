package bons.furious.mixin.fluidwalk_c2;

import bons.furious.patch.fluidwalk_c2.FluidWalkCursor;
import bons.furious.patch.fluidwalk_c2.LionfishFluidWalk;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * lionfishapi_fluid_walk_scan (Lionfish API 2.8 and 3.0: byte-identical EntityMixin; both sides).
 *
 * Two MixinSquared injections into Lionfish API's EntityMixin.fluidCollision handler (merged into Entity, run from
 * Entity.move after collide()), without a CallbackInfo: the handler starts with "if (!(this instanceof LivingEntity))
 * return original;". While the runtime switch is on, that test reads false for a living entity, so the handler returns at
 * that first return, and the value returned there is replaced by LionfishFluidWalk.fluidCollision: the same cells read in
 * the same order (repeats skipped only where they are provably pure cache hits), the same shape test and strict comparison,
 * the same StandOnFluidEvent and the same cancel handling, without the 13 arrays and 12 BlockPos the handler allocated per
 * call. A non-living entity reaches the same return as before and keeps the original vector. The read cursor is kept per
 * entity (one field, created on the entity's first scan). With the switch off Lionfish's code runs unchanged.
 */
@Mixin(value = Entity.class, priority = 1500, remap = false)
public abstract class LionfishFluidWalkMixin {
    @Unique
    private FluidWalkCursor bons$lionfishFluidWalkCursor;

    /** The handler's "instanceof LivingEntity": false while the switch is on, so the handler returns at its first return. */
    @TargetHandler(mixin = "com.github.L_Ender.lionfishapi.mixin.EntityMixin", name = "fluidCollision")
    @ModifyExpressionValue(method = "@MixinSquared:Handler",
            at = @At(value = "CONSTANT", args = "classValue=net/minecraft/world/entity/LivingEntity", ordinal = 0))
    private boolean bons$takeOverFluidWalk(boolean isLiving) {
        return isLiving && !LionfishFluidWalk.enabled;
    }

    /** The first return ("return original"): for a living entity it was reached through the test above, so answer here. */
    @TargetHandler(mixin = "com.github.L_Ender.lionfishapi.mixin.EntityMixin", name = "fluidCollision")
    @ModifyReturnValue(method = "@MixinSquared:Handler", at = @At(value = "RETURN", ordinal = 0))
    private Vec3 bons$leanFluidWalk(Vec3 original) {
        if ((Object) this instanceof LivingEntity entity) {
            FluidWalkCursor cursor = this.bons$lionfishFluidWalkCursor;
            if (cursor == null) this.bons$lionfishFluidWalkCursor = cursor = new FluidWalkCursor();
            return LionfishFluidWalk.fluidCollision(entity, original, cursor);
        }
        return original;
    }
}
