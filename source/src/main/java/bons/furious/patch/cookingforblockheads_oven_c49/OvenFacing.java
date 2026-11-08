package bons.furious.patch.cookingforblockheads_oven_c49;

import net.blay09.mods.cookingforblockheads.block.BaseKitchenBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bons and Furious 1.0.49 switch cookingforblockheads_oven_facing_from_state (Cooking for Blockheads 21.1.24 for Minecraft
 * 1.21.1; kind change, gameplay fix, off by default; helper of OvenFacingMixin; no Cooking for Blockheads code carried).
 *
 * What Cooking for Blockheads does. OvenBlockEntity.getFacing() answers a field that serverTick fills on the oven's first
 * server tick (when the block state has BaseKitchenBlock.FACING); the field is neither saved nor sent, so every oven on the
 * client answers NORTH (and so does a server oven before its first tick). The only reader in the mod is OvenRenderer
 * (client), which draws the door handles with the light of the block next to the oven in that direction: an oven facing
 * south, east or west against a wall takes its handle light from inside the wall, and the handles render dark.
 *
 * What the switch does. getFacing() answers the facing of the oven's current block state (BaseKitchenBlock.FACING) whenever
 * the state has that property, else what Cooking for Blockheads answers. Gameplay fix (client visuals): oven door handles
 * are lit from the block in front of the oven; nothing is saved or sent differently. Runtime flag
 * bons_and_furious.cookingforblockheadsOvenFacingFromState.
 */
public final class OvenFacing {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cookingforblockheadsOvenFacingFromState", "true"));

    private OvenFacing() {
    }

    /** OvenBlockEntity.getFacing()'s answer {@code original} for an oven whose block state is {@code state}. */
    public static Direction facing(BlockState state, Direction original) {
        if (!enabled || state == null || !state.hasProperty(BaseKitchenBlock.FACING)) return original;
        return state.getValue(BaseKitchenBlock.FACING);
    }
}
