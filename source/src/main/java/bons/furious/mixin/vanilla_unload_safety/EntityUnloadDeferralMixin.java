package bons.furious.mixin.vanilla_unload_safety;

import bons.furious.patch.vanilla_unload_safety.UnloadDeferral;
import bons.furious.patch.vanilla_unload_safety.UnloadSnapshot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_entity_unload_snapshot (Minecraft 1.21.1 with NeoForge 21.1.252, server side incl. the integrated server):
 * processUnloads runs its chunksToUnload.removeIf unchanged, inside a deferral scope (begin before, end in a finally
 * after); updateChunkStatus(ChunkPos, Visibility) queues its chunksToUnload.add / remove while that scope is open on this
 * manager (see UnloadSnapshot). MixinExtras @WrapOperation on all three calls, so the other hooks on these methods (the
 * HEAD injection of vanilla_spawn_gate_visibility_memo in updateChunkStatus among them) stay as they are.
 * updateChunkStatus is selected by its full descriptor: the FullChunkStatus overload shares its name and only forwards to
 * it.
 *
 * The mixin applies after other mods' replacements of these methods (priority 1500): Mixin lets an injector into another
 * mixin's replacement only when the injector's priority is higher, and otherwise refuses it before require is read, which
 * stops the server while it starts. Concurrent Chunk Management Engine 0.4.0-alpha.0.122 (its chunk system rewrite,
 * MixinServerEntityManager, mixin priority 1100) replaces processUnloads with a loop that takes one chunk at a time off
 * the front of its own LongLinkedOpenHashSet: no iterator, so a listener's changes cannot break that walk. The
 * processUnloads wrap has require = 0 and finds no removeIf there; no scope is ever opened and updateChunkStatus applies
 * every change at once, as without the switch. A replacement that still walks chunksToUnload with removeIf gets the wrap
 * on its own call.
 */
@Mixin(value = PersistentEntitySectionManager.class, remap = false, priority = 1500)
public abstract class EntityUnloadDeferralMixin implements UnloadDeferral {
    @Shadow
    @Final
    private LongSet chunksToUnload;
    @Unique
    private final UnloadSnapshot.Walk bons$unloadWalk = new UnloadSnapshot.Walk();

    @Override
    public void bons$beginUnloadWalk() {
        this.bons$unloadWalk.begin();
    }

    @Override
    public void bons$endUnloadWalk() {
        this.bons$unloadWalk.end(this.chunksToUnload);
    }

    @WrapOperation(method = "processUnloads()V", require = 0,
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;removeIf(Lit/unimi/dsi/fastutil/longs/LongPredicate;)Z"))
    private boolean bons$deferredWalk(LongSet set, LongPredicate predicate, Operation<Boolean> original) {
        if (!UnloadSnapshot.enabled || set != this.chunksToUnload || set.isEmpty()) {
            return original.call(set, predicate);
        }
        this.bons$unloadWalk.begin();
        try {
            return original.call(set, predicate);
        } finally {
            this.bons$unloadWalk.end(this.chunksToUnload);
        }
    }

    @WrapOperation(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;add(J)Z"))
    private boolean bons$deferAdd(LongSet set, long chunk, Operation<Boolean> original) {
        if (set == this.chunksToUnload && this.bons$unloadWalk.defer(UnloadSnapshot.ADD, chunk)) {
            return true;
        }
        return original.call(set, chunk);
    }

    @WrapOperation(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;remove(J)Z"))
    private boolean bons$deferRemove(LongSet set, long chunk, Operation<Boolean> original) {
        if (set == this.chunksToUnload && this.bons$unloadWalk.defer(UnloadSnapshot.REMOVE, chunk)) {
            return true;
        }
        return original.call(set, chunk);
    }
}
