package bons.furious.mixin.vanilla_unload_safety;

import bons.furious.patch.vanilla_unload_safety.UnloadDeferral;
import bons.furious.patch.vanilla_unload_safety.UnloadSnapshot;
import com.bawnorton.mixinsquared.TargetHandler;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * valkyrien_entity_unload_snapshot (Valkyrien Skies 2.4.11 on Minecraft 1.20.1 / Forge 47.4.28, server side). VS's
 * shipyard_entities MixinPersistentEntitySectionManager cancels processUnloads at HEAD (replaceProcessUnloads) and walks
 * the LIVE chunksToUnload set itself: an iterator over it, every key whose visibility is not HIDDEN or whose
 * processChunkUnload (m_157568_) succeeds collected into a new LongOpenHashSet, removeAll at the end, any Exception printed
 * and the round abandoned (no removeAll). Entity unload listeners that change the set during that walk break the iterator
 * (see UnloadSnapshot). A HEAD injection into VS's handler (MixinSquared, as valkyrien_entity_unloads' mixin, which stays
 * as it is and cancels first when the set is empty) runs the same loop with the same semantics - the same iterator order,
 * the same collected set, the same removeAll, the same catch - inside the deferral scope of vanilla_entity_unload_snapshot
 * (UnloadDeferral; when that switch is off the loop runs without it, i.e. exactly VS's), then cancels as VS does.
 * Apache-2.0 target (VS mod mixin class); our own loop.
 */
@Mixin(value = PersistentEntitySectionManager.class, priority = 1500, remap = false)
public abstract class ValkyrienUnloadSnapshotMixin {
    @Shadow
    @Final
    private LongSet f_157499_;   // chunksToUnload
    @Shadow
    @Final
    private Long2ObjectMap<Visibility> f_157497_;   // chunkVisibility

    @Shadow
    private boolean m_157568_(long chunk) {   // processChunkUnload
        throw new IllegalStateException("shadow");
    }

    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.feature.shipyard_entities.MixinPersistentEntitySectionManager", name = "replaceProcessUnloads")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void bons$deferredValkyrienWalk(CallbackInfo processUnloads, CallbackInfo ci) {
        if (!UnloadSnapshot.enabled || this.f_157499_.isEmpty()) {
            return;
        }
        UnloadDeferral deferral = ((Object) this) instanceof UnloadDeferral d ? d : null;
        if (deferral != null) {
            deferral.bons$beginUnloadWalk();
        }
        try {
            try {
                LongOpenHashSet toRemove = new LongOpenHashSet();
                LongIterator it = this.f_157499_.iterator();
                while (it.hasNext()) {
                    long chunk = it.nextLong();
                    if (this.f_157497_.get(chunk) != Visibility.HIDDEN) {
                        toRemove.add(chunk);
                    } else if (this.m_157568_(chunk)) {
                        toRemove.add(chunk);
                    }
                }
                this.f_157499_.removeAll((LongCollection) toRemove);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } finally {
            if (deferral != null) {
                deferral.bons$endUnloadWalk();
            }
        }
        processUnloads.cancel();
        ci.cancel();
    }
}
