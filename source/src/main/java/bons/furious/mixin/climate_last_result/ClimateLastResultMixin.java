package bons.furious.mixin.climate_last_result;

import bons.furious.patch.climate_last_result.WeakLastResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_climate_last_result_weak (Minecraft 1.21.1, tested build NeoForge 21.1.252; both sides). [FIX]
 *
 * Climate.RTree's constructor creates the per-thread "last leaf" slot of the tree (new ThreadLocal, stored in the final
 * field lastResult). The slot the tree uses is now a WeakLastResult, a ThreadLocal that keeps the leaf weakly (see there):
 * the search code (search) is unchanged and calls get and set on it as before. Climate.RTree is not public, hence the
 * string target. The ThreadLocal vanilla creates is discarded unused (one small object per tree; trees are built when a
 * world's biome source is created).
 *
 * Ported to 1.21.1: Climate.RTree (constructor, lastResult, search) is unchanged. On 1.20.1 the hook took the value of
 * the constructor's NEW ThreadLocal; on 1.21.1 Biolith 3.0.10 (bundled with several world-generation mods) adds two
 * ThreadLocal fields of its own to Climate.RTree, and Mixin merges their initialisers into the same constructor right
 * after the super() call, so a NEW-ThreadLocal hook would also wrap Biolith's slots (and an ordinal would pick Biolith's
 * first). The slot is therefore replaced at the constructor's return, in exactly the field lastResult (@Mutable, as
 * vanilla_noise_wrap_presize does for NoiseChunk.wrapped). Nothing can search the tree before its constructor returns,
 * so every search of every tree uses the WeakLastResult from its first search on, exactly as with the 1.20.1 hook.
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree", remap = false)
public abstract class ClimateLastResultMixin {
    @Shadow
    @Final
    @Mutable
    private ThreadLocal<?> lastResult;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$weakLastResult(CallbackInfo ci) {
        this.lastResult = WeakLastResult.slot(this.lastResult);
    }
}
