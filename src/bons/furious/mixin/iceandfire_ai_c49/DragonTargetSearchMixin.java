package bons.furious.mixin.iceandfire_ai_c49;

import bons.furious.patch.iceandfire_ai_c49.DragonSearchHeight;
import com.github.alexthe666.iceandfire.entity.ai.DragonAITarget;
import com.github.alexthe666.iceandfire.entity.ai.DragonAITargetNonTamed;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * iceandfire_dragon_target_search_height (Ice and Fire 2.1.13-1.20.1-beta-5, server, since 1.0.49, off by default): the
 * AABB.inflate(x, y, z) call of both dragon target goals' getTargetSearchArea gets the switch's vertical half-extent as its
 * y (bons.furious.patch.iceandfire_ai_c49.DragonSearchHeight); x and z are passed on unchanged.
 */
@Mixin(value = {DragonAITarget.class, DragonAITargetNonTamed.class}, remap = false)
public abstract class DragonTargetSearchMixin {
    @WrapOperation(method = "m_7255_(D)Lnet/minecraft/world/phys/AABB;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;m_82377_(DDD)Lnet/minecraft/world/phys/AABB;"), remap = false)
    private AABB bons$searchHeight(AABB box, double x, double y, double z, Operation<AABB> original) {
        return original.call(box, x, DragonSearchHeight.height(y), z);
    }
}
