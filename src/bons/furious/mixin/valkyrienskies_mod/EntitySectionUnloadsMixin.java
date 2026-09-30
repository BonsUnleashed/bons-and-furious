package bons.furious.mixin.valkyrienskies_mod;

import com.bawnorton.mixinsquared.TargetHandler;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * valkyrien_entity_unloads (Valkyrien Skies 2.4.11).
 *
 * VS's shipyard_entities MixinPersistentEntitySectionManager replaces processUnloads (every tick, per level) with its
 * own loop over the sections waiting to unload, which allocates a set and an iterator even when nothing waits. With an
 * empty queue it now cancels processUnloads straight away, which is what the loop ends with anyway. A HEAD injection
 * into VS's handler (MixinSquared); its CallbackInfo is one allocation per level and tick.
 */
@Mixin(value = PersistentEntitySectionManager.class, priority = 1500, remap = false)
public abstract class EntitySectionUnloadsMixin {
    @Shadow
    @Final
    private LongSet f_157499_;

    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.feature.shipyard_entities.MixinPersistentEntitySectionManager", name = "replaceProcessUnloads")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void bons$nothingToUnload(CallbackInfo processUnloads, CallbackInfo ci) {
        if (this.f_157499_.isEmpty()) {
            processUnloads.cancel();
            ci.cancel();
        }
    }
}
