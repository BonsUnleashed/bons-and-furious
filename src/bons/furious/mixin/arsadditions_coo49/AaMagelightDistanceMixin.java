package bons.furious.mixin.arsadditions_coo49;

import bons.furious.patch.arsadditions_coo49.AaLanterns;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * arsadditions_magelight_particle_distance (Ars Additions 1.20.1-1.6.7, LGPL-3.0: no code carried; client; trade-off, off
 * by default). MagelightLanternTile.tick: at its start AaLanterns.decideDistance decides whether every spawn point of the burst lies
 * more than N blocks (the value) from the camera; if so the burst's GlowParticleData.createData calls answer null and its
 * Level.addParticle calls are left out, while the colour step and both ParticleUtil.inRange draws per particle still run.
 */
@Mixin(targets = "com.github.jarva.arsadditions.common.block.tile.MagelightLanternTile", remap = false)
public abstract class AaMagelightDistanceMixin {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void bons$arsadditionsDistanceDecide(CallbackInfo ci) {
        AaLanterns.decideDistance((BlockEntity) (Object) this);
    }

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lcom/hollingsworth/arsnouveau/client/particle/GlowParticleData;createData(Lcom/hollingsworth/arsnouveau/client/particle/ParticleColor;)Lnet/minecraft/core/particles/ParticleOptions;"),
            require = 1, allow = 1)
    private ParticleOptions bons$arsadditionsDistanceData(ParticleColor color, Operation<ParticleOptions> original) {
        return AaLanterns.skipDistance ? null : original.call(color);
    }

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7106_(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), require = 1, allow = 1)
    private void bons$arsadditionsDistanceParticle(Level level, ParticleOptions data, double x, double y, double z, double vx, double vy, double vz,
                                                   Operation<Void> original) {
        if (AaLanterns.skipDistance) {
            AaLanterns.particlesSkippedDistance++;
            return;
        }
        original.call(level, data, x, y, z, vx, vy, vz);
    }
}
