package bons.furious.patch.deeperdarker_coo49;

import com.kyanite.deeperdarker.content.DDBlocks;
import com.kyanite.deeperdarker.content.blocks.SculkJawBlock;
import com.kyanite.deeperdarker.util.DDTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group deeperdarker_coo49 (Deeper and Darker; 1.21.1 tested build: Deeper and Darker 1.4.1 for NeoForge
 * 1.21.1). Deeper and Darker is AGPL-3.0: no code of it is carried; the injections hold only our own tests and use its public
 * registry objects and tags.
 *
 * deeperdarker_break_block_first (exact, on, server): DeeperDarkerEvents.breakEvent (every BlockEvent.BreakEvent: every
 *   block a player breaks, vein-miner and drill breaks included) first looks the enchantment registry up and reads the
 *   main-hand item's Silk Touch level (ItemStack.getEnchantmentLevel(Holder): the item's own getEnchantmentLevel, then
 *   NeoForge's GetEnchantmentLevelEvent, a query event whose listeners adjust the level they return) and then uses it only
 *   for three of its blocks: crystallized amber, the ice lily and the ancient vase; for any other block it fails all three
 *   state tests and returns. The switch returns at once for every other block. The skipped reads are queries, so nothing
 *   observable changes.
 * deeperdarker_sculk_jaw_inside_guards (gameplay fix, off): a sculk jaw's stepOn skips creative or crouching players and
 *   monsters of the deeperdarker:sculk entity type tag, but its entityInside (an open jaw an entity has fallen into) bites
 *   every living entity and takes experience from every player. With the switch on, entityInside skips the same three
 *   cases.
 * deeperdarker_sculk_jaw_single_bite (gameplay fix, off): entityInside bites on every tick while the jaw is open (the
 *   35-tick bite; with vanilla's invulnerability frames about 3 damage every 11 ticks, ~12 in all) and takes 4 experience
 *   from a player into the jaw's store on each of those calls (no cooldown limits that), on top of the stepOn bite. With the
 *   switch on, entityInside does nothing while the jaw is biting: one bite (3 damage, 4 experience) per close.
 */
public final class DeeperDarkerFixes {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean breakFirst = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.deeperdarkerBreakBlockFirst", "true"));
    public static volatile boolean jawGuards = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.deeperdarkerSculkJawInsideGuards", "true"));
    public static volatile boolean jawSingle = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.deeperdarkerSculkJawSingleBite", "true"));
    private static volatile int announced;

    private DeeperDarkerFixes() {
    }

    /** True when breakEvent cannot do anything for this state (none of its three blocks). */
    public static boolean irrelevantBreak(BlockState state) {
        if (!breakFirst || state == null) return false;
        if (state.is(DDBlocks.CRYSTALLIZED_AMBER) || state.is(DDBlocks.ICE_LILY) || state.is(DDBlocks.ANCIENT_VASE)) return false;
        announce(1, "deeperdarker_break_block_first", "Deeper and Darker's block-break handler returns at once for blocks it does not handle");
        return true;
    }

    /** True for stepOn's exemptions: creative or crouching players, monsters of the sculk entity type tag. */
    public static boolean jawExempt(Entity entity) {
        if (!jawGuards) return false;
        boolean exempt = entity instanceof Player p && (p.isCreative() || p.isCrouching())
                || entity instanceof Monster m && m.getType().is(DDTags.Misc.SCULK);
        if (exempt) announce(2, "deeperdarker_sculk_jaw_inside_guards", "open sculk jaws spare whom their bite spares");
        return exempt;
    }

    /** True while the jaw is biting (its entityInside then does nothing). */
    public static boolean jawBiting(BlockState state) {
        if (!jawSingle || !state.getValue(SculkJawBlock.BITING)) return false;
        announce(4, "deeperdarker_sculk_jaw_single_bite", "a sculk jaw bites once per close");
        return true;
    }

    private static void announce(int bit, String key, String what) {
        if ((announced & bit) != 0) return;
        synchronized (DeeperDarkerFixes.class) {
            if ((announced & bit) != 0) return;
            announced |= bit;
        }
        LOGGER.info("Bons and Furious: {}: {}", key, what);
    }
}
