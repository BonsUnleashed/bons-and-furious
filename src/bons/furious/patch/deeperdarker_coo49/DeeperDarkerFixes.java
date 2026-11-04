package bons.furious.patch.deeperdarker_coo49;

import com.kyanite.deeperdarker.content.DDBlocks;
import com.kyanite.deeperdarker.content.blocks.SculkJawBlock;
import com.kyanite.deeperdarker.content.entities.DDMobType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group deeperdarker_coo49 (Deeper and Darker 1.3.3). Deeper and Darker is AGPL-3.0: no code of it is
 * carried; the injections hold only our own tests and use its public registry objects.
 *
 * deeperdarker_break_block_first (exact, on, server): DeeperDarkerEvents.breakEvent (every BlockEvent.BreakEvent: every
 *   block a player breaks, vein-miner and drill breaks included) first reads the main-hand item's Silk Touch level (an
 *   enchantment-list walk, a ResourceLocation per entry) and then uses it only for three of its blocks: crystallized amber,
 *   the ice lily and the ancient vase; for any other block it fails all three state tests and returns. The switch returns
 *   at once for every other block. The Silk Touch read is a query: Forge fires no event on it, no mixin of the pack sits
 *   on its path, and the pack's 13 item overrides of IForgeItem.getEnchantmentLevel only read, except that Tinkers'
 *   tool view gives a Tinkers tool without any NBT an empty tag, which Tinkers' own attribute, damage and harvest hooks
 *   already do as soon as such a tool is held. So nothing observable changes.
 * deeperdarker_sculk_jaw_inside_guards (gameplay fix, off): a sculk jaw's stepOn skips creative or crouching players and
 *   sculk monsters, but its entityInside (an open jaw an entity has fallen into) bites every living entity. With the switch
 *   on, entityInside skips the same three cases.
 * deeperdarker_sculk_jaw_single_bite (gameplay fix, off): entityInside bites on every tick while the jaw is open (the
 *   35-tick bite; with vanilla's invulnerability frames about 3 damage every 11 ticks, ~12 in all) on top of the stepOn bite.
 *   With the switch on, entityInside does nothing while the jaw is biting: one bite per close.
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
        if (state.m_60713_(DDBlocks.CRYSTALLIZED_AMBER.get()) || state.m_60713_(DDBlocks.ICE_LILY.get()) || state.m_60713_(DDBlocks.ANCIENT_VASE.get()))
            return false;
        announce(1, "deeperdarker_break_block_first", "Deeper and Darker's block-break handler returns at once for blocks it does not handle");
        return true;
    }

    /** True for stepOn's exemptions: creative or crouching players, sculk monsters. */
    public static boolean jawExempt(Entity entity) {
        if (!jawGuards) return false;
        boolean exempt = entity instanceof Player p && (p.m_7500_() || p.m_6047_())
                || entity instanceof Monster m && m.m_6336_() == DDMobType.SCULK;
        if (exempt) announce(2, "deeperdarker_sculk_jaw_inside_guards", "open sculk jaws spare whom their bite spares");
        return exempt;
    }

    /** True while the jaw is biting (its entityInside then does nothing). */
    public static boolean jawBiting(BlockState state) {
        if (!jawSingle || !state.m_61143_(SculkJawBlock.BITING)) return false;
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
