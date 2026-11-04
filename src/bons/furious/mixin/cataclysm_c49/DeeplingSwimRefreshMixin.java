package bons.furious.mixin.cataclysm_c49;

import bons.furious.patch.cataclysm_c49.DeeplingSwim;
import com.github.L_Ender.cataclysm.entity.Deepling.AbstractDeepling;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_deepling_swim_refresh_skip (L_Ender's Cataclysm 3.16 / 3.31; both sides; since 1.0.49; a trade-off, off by
 * default): the two Level.noCollision box tests of AbstractDeepling.tick (ordinal 0: the swimming box in water, ordinal 1:
 * the normal box out of water) answer "not free" when the swim flag and the size are already what that branch would set
 * (DeeplingSwim). Our own logic only.
 */
@Mixin(value = AbstractDeepling.class, remap = false)
public abstract class DeeplingSwimRefreshMixin {
    @WrapOperation(method = "m_8119_()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45756_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", ordinal = 0, remap = false),
            require = 1, allow = 1, remap = false)
    private boolean bons$swimmingRoom(Level level, Entity entity, AABB box, Operation<Boolean> original) {
        return DeeplingSwim.room((AbstractDeepling) (Object) this, true, level, entity, box, original);
    }

    @WrapOperation(method = "m_8119_()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45756_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", ordinal = 1, remap = false),
            require = 1, allow = 1, remap = false)
    private boolean bons$normalRoom(Level level, Entity entity, AABB box, Operation<Boolean> original) {
        return DeeplingSwim.room((AbstractDeepling) (Object) this, false, level, entity, box, original);
    }
}
