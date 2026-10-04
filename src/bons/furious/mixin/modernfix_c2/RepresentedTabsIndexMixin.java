package bons.furious.mixin.modernfix_c2;

import bons.furious.patch.modernfix_c2.RepresentedTabsIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Collection;
import java.util.List;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.searchtree.JEIRuntimeCapturer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * modernfix_represented_tabs_index (ModernFix 5.27.77, LGPL-3.0, with JEI 15.59.0.212; client).
 *
 * JEIRuntimeCapturer.getRepresentedTabs counts, for every JEI item stack and every non-search creative tab, whether the
 * tab's search-tab display items contain the stack (N x T hashed set probes, once per JEI session at the first search in a
 * tab with a search bar). The JEI stack collection it is about to iterate goes through RepresentedTabsIndex, which fills
 * the method's own count map exactly as that loop would (same contains calls wherever one can be true, same order) and
 * hands the loop an empty list; everything after the loop is ModernFix's.
 */
@Mixin(value = JEIRuntimeCapturer.class, remap = false)
public abstract class RepresentedTabsIndexMixin {
    @WrapOperation(method = "getRepresentedTabs", at = @At(value = "INVOKE",
            target = "Lmezz/jei/api/runtime/IIngredientManager;getAllItemStacks()Ljava/util/Collection;"))
    private static Collection<ItemStack> bons$countedWithIndex(IIngredientManager manager, Operation<Collection<ItemStack>> original,
                                                              @Local(ordinal = 0) Reference2IntOpenHashMap<CreativeModeTab> counts,
                                                              @Local(ordinal = 0) List<CreativeModeTab> allTabs) {
        return RepresentedTabsIndex.count(original.call(manager), allTabs, counts);
    }
}
