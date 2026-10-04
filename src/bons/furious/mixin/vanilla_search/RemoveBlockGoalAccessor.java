package bons.furious.mixin.vanilla_search;

import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_turtle_egg_search_sections (Minecraft 1.20.1 on Forge 47.4.16; server side): read access to
 * RemoveBlockGoal.blockToRemove (f_25836_), the block the goal's isValidTarget accepts. Nothing else.
 */
@Mixin(value = RemoveBlockGoal.class, remap = false)
public interface RemoveBlockGoalAccessor {
    @Accessor(value = "f_25836_", remap = false)
    Block bons$blockToRemove();
}
