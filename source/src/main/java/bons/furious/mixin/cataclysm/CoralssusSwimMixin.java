package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
import com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Coralssus_Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_coral_swim_checks (Coralssus_Entity; L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried; 1.21.1
 * tested build: L_Ender's Cataclysm 1.21.1-3.33; both sides).
 *
 * tick(): if (eyes in a swimmable fluid) { if (level.noCollision(this, box) && !getSwim()) setSwim(true); } else if
 * (level.noCollision(this, box) && getSwim()) setSwim(false). noCollision is a pure collision query (blocks, entities,
 * world border); getSwim() is a synced-data read. When the swim flag already makes the && false, the query answers false
 * without being made; otherwise it runs. Same flag writes, same everything else.
 *
 * Ported to 1.21.1: unchanged. 3.33's tick() has the same two Level.noCollision(Entity, AABB) && getSwim() tests
 * (canInFluidType(getEyeInFluidType()) decides the branch); vanilla 1.21.1's CollisionGetter.noCollision is still a pure
 * query.
 */
@Mixin(value = Coralssus_Entity.class, remap = false)
public abstract class CoralssusSwimMixin {
    @Shadow
    public abstract boolean getSwim();

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", ordinal = 0))
    private boolean bons$swimFlagFirst(Level level, Entity self, AABB box, Operation<Boolean> original) {
        if (CataclysmWorldScans.coralSwimChecks && this.getSwim()) {
            CataclysmWorldScans.coralSwimActs();
            return false;
        }
        return original.call(level, self, box);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", ordinal = 1))
    private boolean bons$landFlagFirst(Level level, Entity self, AABB box, Operation<Boolean> original) {
        if (CataclysmWorldScans.coralSwimChecks && !this.getSwim()) {
            CataclysmWorldScans.coralSwimActs();
            return false;
        }
        return original.call(level, self, box);
    }
}
