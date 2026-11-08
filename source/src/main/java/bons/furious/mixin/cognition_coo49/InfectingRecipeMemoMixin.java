package bons.furious.mixin.cognition_coo49;

import bons.furious.patch.cognition_coo49.InfectingMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * cognition_infecting_recipe_memo (Cognition, MIT; 1.21.1 tested build: Cognition 2.4.13 for NeoForge 1.21.1; both sides;
 * exact; no Cognition code carried). InfectingRecipe.getInfectedBlockState answers from a per-RecipeManager Block -> answer
 * memo while the recipe generation token is current and every infecting recipe is a plain InfectingRecipe; otherwise the
 * method runs as shipped. See InfectingMemo.
 *
 * Ported to 1.21.1: same static method and descriptor; 2.4.13 looks the recipe up by the block (getRecipe(Level, Block))
 * over RecipeHolder values and tests a one-slot RecipeInput instead of a SimpleContainer.
 */
@Mixin(targets = "com.cyanogen.cognition.recipe.InfectingRecipe", remap = false)
public abstract class InfectingRecipeMemoMixin {
    @WrapMethod(method = "getInfectedBlockState(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;")
    private static BlockState bons$cognitionInfectedMemo(Level level, BlockState state, Operation<BlockState> original) {
        return InfectingMemo.infected(level, state, original);
    }
}
