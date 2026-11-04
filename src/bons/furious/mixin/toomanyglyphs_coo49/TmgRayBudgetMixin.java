package bons.furious.mixin.toomanyglyphs_coo49;

import bons.furious.patch.toomanyglyphs_coo49.TmgRays;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.derringersmods.toomanyglyphs.common.network.PacketRayEffect;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * toomanyglyphs_ray_particle_budget (Too Many Glyphs 1.20.1-2.3.2.12345, LGPL-2.1+: no code carried; client; trade-off,
 * off by default). PacketRayEffect$ClientHandler.accept: at its start, a beam is skipped once the current client tick's
 * beams have handed out the budget (TmgRays.skipRay); each particle the beam hands to ClientLevel.addParticle is counted
 * (TmgRays.counted). A beam that is drawn is drawn exactly as before.
 */
@Mixin(targets = "io.github.derringersmods.toomanyglyphs.common.network.PacketRayEffect$ClientHandler", remap = false)
public abstract class TmgRayBudgetMixin {
    @Inject(method = "accept(Lio/github/derringersmods/toomanyglyphs/common/network/PacketRayEffect;Lnet/minecraftforge/network/NetworkEvent$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void bons$toomanyglyphsRayBudget(PacketRayEffect msg, NetworkEvent.Context context, CallbackInfo ci) {
        if (TmgRays.skipRay()) ci.cancel();
    }

    @WrapOperation(method = "accept(Lio/github/derringersmods/toomanyglyphs/common/network/PacketRayEffect;Lnet/minecraftforge/network/NetworkEvent$Context;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_7106_(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"),
            require = 1, allow = 1)
    private void bons$toomanyglyphsRayParticle(ClientLevel level, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz,
                                               Operation<Void> original) {
        original.call(level, particle, x, y, z, vx, vy, vz);
        TmgRays.counted();
    }
}
