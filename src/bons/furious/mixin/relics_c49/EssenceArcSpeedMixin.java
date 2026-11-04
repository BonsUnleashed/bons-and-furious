package bons.furious.mixin.relics_c49;

import bons.furious.patch.relics_c49.EssenceSpeed;
import com.llamalad7.mixinextras.sugar.Local;
import it.hurts.sskirillss.relics.entities.DeathEssenceEntity;
import it.hurts.sskirillss.relics.entities.LifeEssenceEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * relics_essence_speed_cap (Relics 0.8.0.13, both sides; a gameplay fix, off by default): @ModifyArg on the Vec3.scale
 * (m_82490_) argument in DeathEssenceEntity / LifeEssenceEntity.moveTowardsTargetInArc (distance x age x 0.005): the
 * smallest of that, the configured cap and the remaining distance to the target point (the method's first local,
 * MixinExtras @Local), see EssenceSpeed. Injector only (Relics is All Rights Reserved).
 */
@Mixin(value = {DeathEssenceEntity.class, LifeEssenceEntity.class}, remap = false)
public abstract class EssenceArcSpeedMixin {
    @ModifyArg(method = "moveTowardsTargetInArc", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;m_82490_(D)Lnet/minecraft/world/phys/Vec3;"))
    private double bons$relicsCap(double scale, @Local(ordinal = 0) Vec3 targetPos) {
        return EssenceSpeed.speed(scale, (Entity) (Object) this, targetPos);
    }
}
