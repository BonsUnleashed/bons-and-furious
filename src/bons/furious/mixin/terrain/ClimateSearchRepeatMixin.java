package bons.furious.mixin.terrain;

import bons.furious.patch.terrain.ClimateRepeat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_climate_search_repeat (Minecraft 1.20.1 world generation).
 *
 * Climate.RTree.search finds the nearest climate point for a target, starting from the leaf the thread found last. The
 * biome lookup chain of this pack searches the same tree with the same target several times in a row. The method now
 * returns the value of this thread's previous search when it was the same tree, metric and an equal target; that search
 * left the thread-local seed at the very leaf a repeat would find and store again (see ClimateRepeat). Climate.RTree and
 * its DistanceMetric are not public, hence the string target and the coerced metric parameter.
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree", remap = false)
public abstract class ClimateSearchRepeatMixin {
    /** Start of search: the value of this thread's identical previous search replaces the tree walk. */
    @Inject(method = "m_186930_", at = @At("HEAD"), cancellable = true)
    private void bons$repeatedSearch(Climate.TargetPoint target, @Coerce Object metric, CallbackInfoReturnable<Object> cir) {
        Object last = ClimateRepeat.lastSearch(this, target, metric);
        if (last != ClimateRepeat.NONE) cir.setReturnValue(last);
    }

    /** Return of search: remember the query noted at its start together with the value, return the value unchanged. */
    @ModifyReturnValue(method = "m_186930_", at = @At("RETURN"))
    private Object bons$rememberSearch(Object value) {
        return ClimateRepeat.rememberSearch(this, value);
    }
}
