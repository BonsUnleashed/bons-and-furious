package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
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
import com.github.L_Ender.cataclysm.entity.projectile.Ashen_Breath_Entity;

/**
 * cataclysm_area_attack_scans (Ashen_Breath_Entity, L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is
 * carried; both sides).
 *
 * hitEntities(): for (living in getEntityLivingBaseNearby(7, 7, 7, 7)) { ...angles...; if ((inRange && yaw && pitch ||
 * close) && tickCount % 3 == 0 && !isAlliedTo(living) && living != caster) hurt }. Everything before the tick test only
 * computes local floats (the caster is read from its field, no lookup), and the list is used by this loop only. On ticks
 * where tickCount % 3 != 0 the query returns an empty list instead.
 */
@Mixin(value = Ashen_Breath_Entity.class, remap = false)
public abstract class AshenBreathScanMixin {
    @WrapOperation(method = "hitEntities", at = @At(value = "INVOKE",
            target = "Lcom/github/L_Ender/cataclysm/entity/projectile/Ashen_Breath_Entity;getEntityLivingBaseNearby(DDDD)Ljava/util/List;"))
    private List<LivingEntity> bons$breathScanOnDamageTicks(Ashen_Breath_Entity self, double a, double b, double c, double d, Operation<List<LivingEntity>> original) {
        if (CataclysmWorldScans.skipAreaScan(self, 3)) return List.of();
        return original.call(self, a, b, c, d);
    }
}
