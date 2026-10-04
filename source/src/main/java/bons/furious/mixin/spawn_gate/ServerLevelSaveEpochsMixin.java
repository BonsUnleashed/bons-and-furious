package bons.furious.mixin.spawn_gate;

import bons.furious.patch.spawn_gate.SpawnGate;
import bons.furious.patch.spawn_gate.VisibilityEpochs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_spawn_gate_visibility_memo (Minecraft 1.21.1 with NeoForge 21.1.252; server): when ServerLevel.save returns,
 * every epoch group of the level's entity manager is bumped. On 1.20.1 this covered C2ME 0.2.0's opts.scheduling
 * shutdown module, which replaces the saveAll call inside this method and has its own access to chunkVisibility
 * (metadata scan; its code is not read); the bump stays so that any change a mod makes to the map around a save
 * invalidates every remembered answer. A save is rare; the CallbackInfo does not matter. No Minecraft code is carried.
 *
 * Ported to 1.21.1: selected by its full descriptor; NeoForge's save body now also waits for the IO worker on a flush
 * (IOUtilities.waitUntilIOWorkerComplete, no chunkVisibility access); the bump still runs at every RETURN.
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelSaveEpochsMixin {
    @Inject(method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V", at = @At("RETURN"))
    private void bons$bumpAllAfterSave(ProgressListener progress, boolean flush, boolean skipSave, CallbackInfo ci) {
        Object manager = ((ServerLevelEntityManagerAccessor) this).bons$entityManager();
        if (manager instanceof VisibilityEpochs epochs) SpawnGate.bumpAll(epochs.bons$visEpochs());
    }
}
