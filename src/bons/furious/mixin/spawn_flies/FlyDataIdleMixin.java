package bons.furious.mixin.spawn_flies;

import bons.furious.patch.spawn_flies.IdleFlies;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ninni.spawn.server.gui.fly.FlyData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * spawn_flyless_particle_check (Spawn 4.0.7, All Rights Reserved; server side incl. the integrated server):
 * FlyData.spawnRandomFlyParticles(LivingEntity, int) goes through IdleFlies, which answers a call with 0 flies with the
 * same random draw Spawn's method makes and runs Spawn's method for everything else (see IdleFlies for why that is the
 * same). Spawn's code stays in its jar.
 */
@Mixin(value = FlyData.class, remap = false)
public abstract class FlyDataIdleMixin {
    @WrapMethod(method = "spawnRandomFlyParticles")
    private static void bons$flylessCheck(LivingEntity entity, int amount, Operation<Void> original) {
        IdleFlies.spawnRandomFlyParticles(entity, amount, original);
    }
}
