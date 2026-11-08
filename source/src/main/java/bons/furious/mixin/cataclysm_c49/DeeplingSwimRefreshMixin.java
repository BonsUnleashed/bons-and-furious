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
 * cataclysm_deepling_swim_refresh_skip (L_Ender's Cataclysm; 1.21.1 tested build: L_Ender's Cataclysm 1.21.1-3.33; client;
 * since 1.0.49; a trade-off, off by default): the two Level.noCollision box tests of AbstractDeepling.tick (ordinal 0: the
 * swimming box in water, ordinal 1: the normal box out of water) answer "not free" when the swim flag and the size are
 * already what that branch would set (DeeplingSwim). Our own logic only.
 *
 * Ported to 1.21.1: 3.33 runs the two tests and their refreshes inside an isClientSide branch (3.16 and 3.31 ran them on
 * both sides), so the mixin is a client mixin; the swim and normal sizes are both the type's size scaled by getScale(),
 * and the Lionfish is no longer an AbstractDeepling.
 */
@Mixin(value = AbstractDeepling.class, remap = false)
public abstract class DeeplingSwimRefreshMixin {
    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", ordinal = 0),
            require = 1, allow = 1)
    private boolean bons$swimmingRoom(Level level, Entity entity, AABB box, Operation<Boolean> original) {
        return DeeplingSwim.room((AbstractDeepling) (Object) this, true, level, entity, box, original);
    }

    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", ordinal = 1),
            require = 1, allow = 1)
    private boolean bons$normalRoom(Level level, Entity entity, AABB box, Operation<Boolean> original) {
        return DeeplingSwim.room((AbstractDeepling) (Object) this, false, level, entity, box, original);
    }
}
