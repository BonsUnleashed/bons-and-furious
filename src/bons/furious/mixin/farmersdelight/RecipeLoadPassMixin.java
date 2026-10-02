package bons.furious.mixin.farmersdelight;

import bons.furious.patch.farmersdelight.ToolActionItems;
import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * farmersdelight_tool_action_items (Minecraft 1.20.1 RecipeManager, both sides).
 *
 * One RecipeManager.apply call - the parse of every JSON recipe of a data load (world load, server start, /reload) - is
 * one ToolActionItems pass on its thread. The method runs unchanged inside it (every other mod's injection included);
 * the pass is closed in a finally block, so a failing load leaves nothing behind.
 */
@Mixin(value = RecipeManager.class, remap = false)
public abstract class RecipeLoadPassMixin {
    @WrapMethod(method = "m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V")
    private void bons$recipePass(Map<ResourceLocation, JsonElement> recipes, ResourceManager resources, ProfilerFiller profiler, Operation<Void> original) {
        ToolActionItems.Pass previous = ToolActionItems.enterPass();
        try {
            original.call(recipes, resources, profiler);
        } finally {
            ToolActionItems.leave(previous);
        }
    }
}
