package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
import com.github.L_Ender.cataclysm.entity.projectile.Void_Rune_Entity;
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
 * cataclysm_area_attack_scans (Void_Rune_Entity; L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried; 1.21.1
 * tested build: L_Ender's Cataclysm 1.21.1-3.33; both sides, the scan itself runs on the server).
 *
 * tick(): for (living in getEntitiesOfClass(...)) damage(living); damage() calls getCaster() then hurts only when
 * tickCount % 5 == 0. The scan's list is only iterated by that loop. On every other tick the scan returns an empty list
 * instead, so the loop runs zero times: no entity could be hurt on such a tick and the skipped checks only read. The skip
 * also needs a settled caster lookup (CataclysmWorldScans.skipAreaScan), so getCaster() keeps a caster exactly when it
 * would.
 *
 * Ported to 1.21.1: unchanged. 3.33's tick() (server branch, warm-up window) / damage() / getCaster() have the same
 * shape and fields in Mojang names.
 */
@Mixin(value = Void_Rune_Entity.class, remap = false)
public abstract class VoidRuneScanMixin {
    @Shadow
    private LivingEntity caster;

    @Shadow
    private UUID casterUuid;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends Entity> List<T> bons$scanOnDamageTicks(Level level, Class<T> type, AABB box, Operation<List<T>> original) {
        if (CataclysmWorldScans.skipAreaScan((Entity) (Object) this, 5, this.caster, this.casterUuid)) return List.of();
        return original.call(level, type, box);
    }
}
