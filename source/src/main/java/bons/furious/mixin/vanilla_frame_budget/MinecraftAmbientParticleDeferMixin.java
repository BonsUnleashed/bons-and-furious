package bons.furious.mixin.vanilla_frame_budget;

import bons.furious.patch.vanilla_frame_budget.FrameBudget;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_ambient_particle_defer (Minecraft 1.21.1 on NeoForge 21.1.252, client; trade-off, off by default):
 * Minecraft.tick calls ClientLevel.animateTick(x, y, z) once per client tick (when the game is not paused and the level's
 * tick rate runs normally); the MixinExtras condition records the call with its arguments instead
 * (FrameBudget.animateTickNow answers false), and the HEAD of the next Minecraft.runTick makes it
 * (FrameBudget.runDeferredAnimateTick) when the client still plays that level. The call itself is unchanged, so every other
 * mod's hooks inside animateTick (Embeddium's doAnimateTick overwrite and its local RandomSource, our own
 * vanilla_animate_tick_uniform_biome) run as before, one frame later. require = 0.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftAmbientParticleDeferMixin {
    @WrapWithCondition(method = "tick()V", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;animateTick(III)V"))
    private boolean bons$deferAmbientParticles(ClientLevel level, int x, int y, int z) {
        return FrameBudget.animateTickNow(level, x, y, z);
    }

    @Inject(method = "runTick(Z)V", require = 0, at = @At("HEAD"))
    private void bons$runDeferredAmbientParticles(boolean renderLevel, CallbackInfo ci) {
        FrameBudget.runDeferredAnimateTick((Minecraft) (Object) this);
    }
}
