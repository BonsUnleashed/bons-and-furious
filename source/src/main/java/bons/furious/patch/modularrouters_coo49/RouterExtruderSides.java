package bons.furious.patch.modularrouters_coo49;

import me.desht.modularrouters.block.tile.ModularRouterBlockEntity;
import me.desht.modularrouters.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious 1.0.49 switch modularrouters_extruder_side_fast_path (Modular Routers 13.2.7+mc1.21.1, server; kind
 * change, off by default (exact; opt-in under the speed rule, see the properties text); helper of RouterRedstoneMixin; no
 * Modular Routers code carried).
 *
 * What Modular Routers does. ModularRouterBlockEntity.calculateIncomingRedstonePower(pos) walks the six sides in
 * MiscUtil.DIRECTIONS order; for each side it asks the router's extension data for the integer "ExtruderDist" + side (a
 * side an extruder module has pushed blocks out of is skipped), then reads level.getSignal(pos.relative(side), side), answers
 * at once on 15 or more and otherwise keeps the maximum. It runs on every neighbour change of a router and whenever the
 * router's remembered power is invalid; the six string concatenations and lookups run even when the extension data is
 * empty, which it is for every router that never ran an extruder.
 *
 * What the switch does. The same walk in the same order with the same calls (getExtensionData() each side, as the router
 * does, so the compound is created as it would be; nonNullLevel() and getSignal() each side), except that the
 * "ExtruderDist" key is neither built nor looked up while the extension data is empty: an empty compound answers 0 for
 * every key, so no side is skipped and the answer is the same. Non-empty extension data takes the router's own test. Runtime
 * flag bons_and_furious.modularroutersExtruderSideFastPath.
 */
public final class RouterExtruderSides {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.modularroutersExtruderSideFastPath", "true"));
    private static volatile boolean announced;

    private RouterExtruderSides() {
    }

    /** calculateIncomingRedstonePower(pos) for {@code router}, as described above. */
    public static int power(ModularRouterBlockEntity router, BlockPos pos) {
        if (!announced) announce();
        int power = 0;
        for (Direction facing : MiscUtil.DIRECTIONS) {
            CompoundTag ext = router.getExtensionData();
            if (!ext.isEmpty() && ext.getInt("ExtruderDist" + facing) > 0) continue;
            int p = router.nonNullLevel().getSignal(pos.relative(facing), facing);
            if (p >= 15) return p;
            if (p > power) power = p;
        }
        return power;
    }

    private static void announce() {
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: modularrouters_extruder_side_fast_path applies (a router without extruder data reads its six redstone sides without the per-side key lookups)");
    }
}
