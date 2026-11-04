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
 * vanilla_entity_unload_snapshot (Minecraft 1.20.1 on Forge 47.4.28, server side): processUnloads (m_157577_) runs its
 * chunksToUnload.removeIf unchanged, inside a deferral scope (begin before, end in a finally after); updateChunkStatus
 * (m_157527_) queues its chunksToUnload.add / remove while that scope is open on this manager (see UnloadSnapshot).
 * MixinExtras @WrapOperation on all three calls, so Valkyrien Skies' forge ModifyArg (the exception-catching predicate on
 * the same removeIf) and Bons and Furious' spawn_gate epoch injections in updateChunkStatus stay as they are. With Valkyrien
 * Skies installed processUnloads is cancelled at HEAD by VS, and valkyrien_entity_unload_snapshot opens the same scope
 * around VS's loop through UnloadDeferral.
 */
@Mixin(value = PersistentEntitySectionManager.class, remap = false, priority = 1001)
public abstract class EntityUnloadDeferralMixin implements UnloadDeferral {
    @Shadow
    @Final
    private LongSet f_157499_;   // chunksToUnload
    @Unique
    private final UnloadSnapshot.Walk bons$unloadWalk = new UnloadSnapshot.Walk();

    @Override
    public void bons$beginUnloadWalk() {
        this.bons$unloadWalk.begin();
    }

    @Override
    public void bons$endUnloadWalk() {
        this.bons$unloadWalk.end(this.f_157499_);
    }

    @WrapOperation(method = "m_157577_", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;removeIf(Lit/unimi/dsi/fastutil/longs/LongPredicate;)Z"))
    private boolean bons$deferredWalk(LongSet set, LongPredicate predicate, Operation<Boolean> original) {
        if (!UnloadSnapshot.enabled || set != this.f_157499_ || set.isEmpty()) {
            return original.call(set, predicate);
        }
        this.bons$unloadWalk.begin();
        try {
            return original.call(set, predicate);
        } finally {
            this.bons$unloadWalk.end(this.f_157499_);
        }
    }

    @WrapOperation(method = "m_157527_", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;add(J)Z"))
    private boolean bons$deferAdd(LongSet set, long chunk, Operation<Boolean> original) {
        if (set == this.f_157499_ && this.bons$unloadWalk.defer(UnloadSnapshot.ADD, chunk)) {
            return true;
        }
        return original.call(set, chunk);
    }

    @WrapOperation(method = "m_157527_", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;remove(J)Z"))
    private boolean bons$deferRemove(LongSet set, long chunk, Operation<Boolean> original) {
        if (set == this.f_157499_ && this.bons$unloadWalk.defer(UnloadSnapshot.REMOVE, chunk)) {
            return true;
        }
        return original.call(set, chunk);
    }
}
