package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
import com.github.L_Ender.cataclysm.entity.effect.Sandstorm_Entity;
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
 * cataclysm_area_attack_scans (Sandstorm_Entity; L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried; 1.21.1
 * tested build: L_Ender's Cataclysm 1.21.1-3.33; both sides).
 *
 * tick(): for (living in getEntitiesOfClass(...)) damage(living); damage() hurts only when tickCount % 3 == 0 (after pure
 * checks and getCaster(), which tick() already called this tick). The scan's list is only iterated by that loop. On every
 * other tick the scan returns an empty list instead, so the loop runs zero times: no entity could be hurt on such a tick
 * and the skipped checks only read. The skip also needs a settled caster lookup (CataclysmWorldScans.skipAreaScan), so
 * getCaster() keeps a caster exactly when it would.
 *
 * Ported to 1.21.1: unchanged. 3.33's tick() / damage() / getCaster() have the same shape (Mojang names; the damage is a
 * constant 7 instead of a config value), the caster / casterUuid fields are the same.
 */
@Mixin(value = Sandstorm_Entity.class, remap = false)
public abstract class SandstormScanMixin {
    @Shadow
    private LivingEntity caster;

    @Shadow
    private UUID casterUuid;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends Entity> List<T> bons$scanOnDamageTicks(Level level, Class<T> type, AABB box, Operation<List<T>> original) {
        if (CataclysmWorldScans.skipAreaScan((Entity) (Object) this, 3, this.caster, this.casterUuid)) return List.of();
        return original.call(level, type, box);
    }
}
