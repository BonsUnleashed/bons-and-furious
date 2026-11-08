package bons.furious.patch.titanium_coo49;

import com.hrznstudio.titanium.block.RotatableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch titanium_facing_single_state_read (since 1.0.49; Titanium 4.0.52 for NeoForge 1.21.1, both sides;
 * helper of ActiveTileFacingMixin; no Titanium code is carried, Titanium is LGPL-3.0 and nothing of it is copied either).
 *
 * What Titanium does. ActiveTile.getFacingDirection() asks the level for the machine's block state up to three times in
 * one call: once for FACING_ALL's presence, again for its value, or again for FACING_HORIZONTAL's presence and once more for
 * its value; NORTH when the state has neither. It runs on every item and fluid capability query against a Titanium machine
 * (ActiveTile.getItemHandler / getFluidHandler turn the asked side into the machine's own side with it: every adjacent pipe,
 * hopper or machine asks) and on every facing handler work tick.
 *
 * What the switch does (exact). It reads level.getBlockState(worldPosition) once and asks the same questions of that one
 * state in the same order: FACING_ALL present, its value; else FACING_HORIZONTAL present, its value; else NORTH. Within one
 * call nothing runs between the reads Titanium makes (no other code, the same thread), so every read answers the same state
 * object and the first read keeps any chunk access the original's first read makes. Same answer, same exceptions (a null
 * level fails on the first read either way). Runtime flag bons_and_furious.titaniumFacingSingleStateRead.
 */
public final class TitaniumFacing {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.titaniumFacingSingleStateRead", "true"));
    private static volatile boolean announced;

    private TitaniumFacing() {
    }

    /** ActiveTile.getFacingDirection()'s answer for the machine at {@code pos} in {@code level}, from one state read. */
    public static Direction facing(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!announced) announce();
        if (state.hasProperty(RotatableBlock.FACING_ALL)) return state.getValue(RotatableBlock.FACING_ALL);
        if (state.hasProperty(RotatableBlock.FACING_HORIZONTAL)) return state.getValue(RotatableBlock.FACING_HORIZONTAL);
        return Direction.NORTH;
    }

    private static void announce() {
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: titanium_facing_single_state_read applies (a Titanium machine reads its block state once per facing question)");
    }
}
