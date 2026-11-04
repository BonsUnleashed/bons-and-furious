package bons.furious.mixin.celestialcore_precheck_c49;

import bons.furious.patch.celestialcore_precheck_c49.TransformPrecheck;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * celestialcore_transform_lookup_precheck (Celestial Core 1.6.1 and 1.5.8, both sides), part 2 of 2: an invoker for
 * RecipeManager's private byType (m_44054_), the map getRecipeFor streams over, so TransformPrecheck keeps the recipe blocks
 * per map object without building a list.
 */
@Mixin(value = RecipeManager.class, remap = false)
public interface RecipeManagerByTypeAccess extends TransformPrecheck.ByType {
    @Override
    @Invoker("m_44054_")
    Map<ResourceLocation, ?> bons$celestialcore_precheck_c49ByType(RecipeType<?> type);
}
