package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
import com.github.L_Ender.cataclysm.entity.effect.Flame_Strike_Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_area_attack_scans (Flame_Strike_Entity, since 1.0.49; L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code
 * is carried; 1.21.1 tested build: L_Ender's Cataclysm 1.21.1-3.33; both sides, the scan runs on both).
 *
 * tick(): when not waiting and tickCount % 5 == 0, for (living in getEntitiesOfClass(LivingEntity, its box)) damage(living);
 * damage() first asks getOwner() (which may resolve and keep the owner from its UUID on a server level), then hurts only
 * when the target is alive, not invulnerable, not the owner and tickCount % 2 == 0. The scan's list is only iterated by
 * that loop. So on the scan ticks with an odd tickCount (every 10th tick, offset 5) the loop's damage() calls cannot hurt
 * and their checks only read: the scan returns an empty list instead and the loop runs zero times. The skip also needs a
 * settled owner lookup (CataclysmWorldScans.skipAreaScan, as for the other area attacks), so getOwner() keeps an owner
 * exactly when it would; the tick's own end-of-life explosion reads the owner field, which is therefore the same too.
 *
 * 1.21.1: 3.33's tick() runs the scan after its side branches on both sides (the client's damage() calls reach
 * LivingEntity.hurt only on even ticks too, where 1.21.1's hurt answers false on a client level), and its getOwner()
 * resolves the owner only on a server level while the owner field is null: the same shape as 3.16.
 */
@Mixin(value = Flame_Strike_Entity.class, remap = false)
public abstract class FlameStrikeScanMixin {
    @Shadow
    private LivingEntity owner;

    @Shadow
    private UUID ownerUUID;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends Entity> List<T> bons$scanOnDamageTicks(Level level, Class<T> type, AABB box, Operation<List<T>> original) {
        if (CataclysmWorldScans.skipAreaScan((Entity) (Object) this, 2, this.owner, this.ownerUUID)) return List.of();
        return original.call(level, type, box);
    }
}
