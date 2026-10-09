package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
import com.github.L_Ender.cataclysm.entity.Deepling.Coral_Golem_Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_coral_swim_checks (Coral_Golem_Entity, L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is carried; both sides).
 *
 * tick(): if (eyes in a swimmable fluid) { if (level.noCollision(this, box) && !getSwim()) setSwim(true); } else if
 * (level.noCollision(this, box) && getSwim()) setSwim(false). noCollision is a pure collision query (blocks, entities,
 * world border); getSwim() is a synced-data read. When the swim flag already makes the && false, the query answers false
 * without being made; otherwise it runs. Same flag writes, same everything else.
 */
@Mixin(value = Coral_Golem_Entity.class, remap = false)
public abstract class CoralGolemSwimMixin {
    @Shadow
    public abstract boolean getSwim();

    @WrapOperation(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45756_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", ordinal = 0))
    private boolean bons$swimFlagFirst(Level level, Entity self, AABB box, Operation<Boolean> original) {
        if (CataclysmWorldScans.coralSwimChecks && this.getSwim()) {
            CataclysmWorldScans.coralSwimActs();
            return false;
        }
        return original.call(level, self, box);
    }

    @WrapOperation(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45756_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", ordinal = 1))
    private boolean bons$landFlagFirst(Level level, Entity self, AABB box, Operation<Boolean> original) {
        if (CataclysmWorldScans.coralSwimChecks && !this.getSwim()) {
            CataclysmWorldScans.coralSwimActs();
            return false;
        }
        return original.call(level, self, box);
    }
}
