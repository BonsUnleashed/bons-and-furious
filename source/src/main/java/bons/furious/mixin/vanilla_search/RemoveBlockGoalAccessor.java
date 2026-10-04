package bons.furious.mixin.vanilla_search;

import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_turtle_egg_search_sections (Minecraft 1.21.1 with NeoForge 21.1.252; server side): read access to
 * RemoveBlockGoal.blockToRemove, the block the goal's isValidTarget accepts. Nothing else.
 *
 * Ported to 1.21.1: Mojang names only; the field is unchanged.
 */
@Mixin(value = RemoveBlockGoal.class, remap = false)
public interface RemoveBlockGoalAccessor {
    @Accessor(value = "blockToRemove", remap = false)
    Block bons$blockToRemove();
}
