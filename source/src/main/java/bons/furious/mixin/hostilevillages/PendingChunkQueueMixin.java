package bons.furious.mixin.hostilevillages;

import bons.furious.patch.hostilevillages.PendingChunkQueue;
import com.hostilevillages.event.EventHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hostilevillages_pending_chunk_queue (Hostile Villages 1.20.1-5.7, both sides).
 *
 * addToWorld empties Hostile Villages' spawn queue at the end of every level tick, and the difficulty lookup in each mob's
 * spawn setup made the server thread wait for a village chunk that was still generating. The loop condition now also stops
 * at a queue head in exactly that state, so the entry is processed on a later tick (see PendingChunkQueue), and the queue
 * becomes a synchronized list because worldgen threads fill it. Hostile Villages is All Rights Reserved: this mixin only
 * wraps the loop condition and the end of the static initializer and carries none of its code.
 */
@Mixin(value = EventHandler.class, remap = false)
public abstract class PendingChunkQueueMixin {
    @Shadow
    private static List<Tuple<Entity, ServerLevel>> toAdd;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void bons$synchronizedQueue(CallbackInfo ci) {
        toAdd = PendingChunkQueue.synchronizedQueue(toAdd);
    }

    // ordinal 0 is the early return at the start of addToWorld, ordinal 1 the condition of the draining loop
    @WrapOperation(method = "addToWorld", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 1))
    private static boolean bons$holdUnfinishedChunk(List<?> queue, Operation<Boolean> original) {
        return original.call(queue) || PendingChunkQueue.holdBack(queue);
    }
}
