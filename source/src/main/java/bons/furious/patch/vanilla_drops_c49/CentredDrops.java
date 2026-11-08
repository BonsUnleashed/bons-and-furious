package bons.furious.patch.vanilla_drops_c49;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_block_drops_centred (Minecraft 1.21.1 on NeoForge 21.1.252, server), a trade-off that is
 * off by default. Mojang code is no-copy: injectors only. Ours, idea from Collections Of Optimizations
 * (vanilla.predictableItemDrops), idea text only.
 *
 * What vanilla does: Block.popResource(Level, BlockPos, ItemStack), the drop path of every broken block (and of everything
 * else that pops a stack out of a block position), places the new item entity at the block centre plus three random offsets
 * of -0.25..0.25 (Mth.nextDouble on the level's random), and the item entity's constructor ItemEntity(Level, x, y, z, stack)
 * gives it a random horizontal motion of -0.1..0.1 on each axis (two more draws on the level's random) with an upward motion
 * of 0.2. The inner popResource(Level, Supplier, ItemStack) builds the entity only on the server, for a non-empty stack,
 * with doTileDrops on and outside NeoForge's block-snapshot restore, sets the default pickup delay and adds the entity to
 * the world, or to NeoForge's captured drops while Block.dropResources collects them for its block drops event.
 *
 * What the switch does (only when bons_and_furious.properties turns it on): the three offsets are 0, so the drop appears at
 * the exact centre of the block (vanilla's height correction for the item's size stays), and the item entity gets the
 * straight upward motion (0, 0.2, 0). What the player notices: drops of broken blocks no longer scatter; they hop straight
 * up from the middle of the block. Everything else of the drop is vanilla's and NeoForge's (item, count, pickup delay, the
 * doTileDrops rule, the block-snapshot check, the drop capture and its event); the level's random is drawn five times fewer
 * per drop, so later random events of that level differ from a world without the switch. popResourceFromFace (drops pushed
 * out of one face) is unchanged.
 *
 * Flag: -Dbons_and_furious.vanillaBlockDropsCentred=false restores vanilla's scatter at runtime.
 */
public final class CentredDrops {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaBlockDropsCentred", "true"));
    private static volatile boolean announced;

    private CentredDrops() {
    }

    /** In place of Mth.nextDouble(random, -0.25, 0.25) for each of the three offsets in popResource. */
    public static double offset(RandomSource random, double min, double max) {
        if (!enabled) return Mth.nextDouble(random, min, max);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_block_drops_centred applies (block drops appear at the block centre and hop straight up)");
        }
        return 0.0D;
    }

    /** In place of new ItemEntity(level, x, y, z, stack) in popResource's item supplier. */
    public static ItemEntity item(Level level, double x, double y, double z, ItemStack stack) {
        return enabled ? new ItemEntity(level, x, y, z, stack, 0.0D, 0.2D, 0.0D) : new ItemEntity(level, x, y, z, stack);
    }
}
