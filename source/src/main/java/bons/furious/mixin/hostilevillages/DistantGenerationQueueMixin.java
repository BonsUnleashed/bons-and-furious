package bons.furious.mixin.hostilevillages;

import agentcraft.hostilevillages.DistantGenerationCompat;
import com.hostilevillages.event.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * hostilevillages_distant_generation_queue (Hostile Villages 1.20.1-5.7).
 *
 * Hostile Villages replaces the villagers that village generation creates: it queues the replacement mob together
 * with the live ServerLevel, and the next live level tick adds it there. For a village generated inside a Distant
 * Horizons temporary region, that queue carried the temporary entity into the live world, where its spawn setup forced
 * chunk loads on the server thread. When the accessor belongs to Distant Horizons' generation region, the method now
 * returns false at its start, before the queue, the village state and any random draw; every other call is unchanged.
 */
@Mixin(value = EventHandler.class, remap = false)
public abstract class DistantGenerationQueueMixin {
    @Inject(method = "replaceEntityOnSpawn", at = @At("HEAD"), cancellable = true)
    private static void bons$skipDistantGeneration(Entity entity, ServerLevelAccessor world, CallbackInfoReturnable<Boolean> cir) {
        if (DistantGenerationCompat.isDistantGeneration(world)) {
            cir.setReturnValue(false);
        }
    }
}
