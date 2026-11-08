package bons.furious.patch.lithostitched_c49;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lithostitched_block_swap_table (Lithostitched 1.8.1 for NeoForge 1.21.1, MIT; server side incl.
 * the integrated server). Ours; no Lithostitched code is carried.
 *
 * What Lithostitched does. BlockSwapStructureProcessor.processBlock runs for every block of every template piece its
 * processor list applies to: level.registryAccess().lookupOrThrow(Registries.BLOCK) (a registry lookup with its Optionals),
 * the block's builtInRegistryHolder().key(), blockSwapMap.containsKey(key), and on a match blockSwapMap.get and the
 * registry's get(target key) (two more lookups) before building the swapped StructureBlockInfo; a target key that names no
 * block leaves the block as it is.
 *
 * What the switch does. Each processor builds, on its first call, an identity table source Block -> target Block from its
 * key map: for every entry whose source key is a registered block's own key (BLOCK.containsKey, which follows no alias) and
 * whose target key BLOCK.getHolder resolves (NeoForge aliases included, as the registry's get does), source block ->
 * target block. processBlock then answers with one identity lookup and builds the same StructureBlockInfo (same position,
 * target.withPropertiesOf(state), same NBT object), or returns the very same info object when the block has no entry.
 *
 * Why the result is identical. A registered block's holder key is the interned key of its registry entry (ResourceKey
 * compares by identity), so Lithostitched matches block b exactly when b's own key is a source key, i.e. when b is in the
 * table; a registered source key whose getHolder resolves to another holder makes the whole map keep Lithostitched's path.
 * The target it builds is the value of the target key's holder in the level's block registry, which for the server's
 * registries (a ServerLevel or a WorldGenRegion: Level.registryAccess is the server's composite access, whose static layer
 * holds BuiltInRegistries.BLOCK) is BuiltInRegistries.BLOCK, frozen before any structure is placed. Entries whose source or
 * target names no block can never change a block, so the table leaves them out. Kept on Lithostitched's own path: a block
 * whose registry holder is unbound (its key() read throws there), any other kind of level, and any map that is not an
 * immutable Guava map (the codec decodes into one; a hand-made mutable map could change).
 *
 * -Dbons_and_furious.lithostitchedBlockSwapTable=false switches it off at run time.
 * -Dbons_and_furious.lithostitchedBlockSwapTable.shadow=true: Lithostitched's body runs and its answer is compared with the
 * table's (block of the result's state, same position and NBT object; SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for 20).
 */
public final class SwapTable {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lithostitchedBlockSwapTable", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.lithostitchedBlockSwapTable.shadow");
    public static final java.util.concurrent.atomic.AtomicLong SHADOW_CHECKS = new java.util.concurrent.atomic.AtomicLong(),
            SHADOW_MISMATCHES = new java.util.concurrent.atomic.AtomicLong();
    /** The marker for a processor whose map keeps Lithostitched's own path. */
    public static final SwapTable STOCK = new SwapTable(null);
    private static volatile boolean announced;

    private final Reference2ReferenceOpenHashMap<Block, Block> table;

    private SwapTable(Reference2ReferenceOpenHashMap<Block, Block> table) {
        this.table = table;
    }

    /** The target block for a source block, or null when the processor leaves it alone. */
    public Block target(Block source) {
        return this.table.get(source);
    }

    /** True for the levels whose registry access is the server's (its block registry is BuiltInRegistries.BLOCK). */
    public static boolean serverRegistries(LevelReader level) {
        return level instanceof WorldGenRegion || level instanceof ServerLevel;
    }

    /** Built once per processor (the result is published through the processor's volatile field). */
    public static SwapTable build(Map<ResourceKey<Block>, ResourceKey<Block>> blockSwapMap) {
        if (!(blockSwapMap instanceof ImmutableMap)) return STOCK;
        Registry<Block> blocks = BuiltInRegistries.BLOCK;
        Reference2ReferenceOpenHashMap<Block, Block> t = new Reference2ReferenceOpenHashMap<>(Math.max(4, blockSwapMap.size()));
        for (Map.Entry<ResourceKey<Block>, ResourceKey<Block>> e : blockSwapMap.entrySet()) {
            ResourceKey<Block> sourceKey = e.getKey();
            if (!blocks.containsKey(sourceKey)) continue;            // no block's own key: Lithostitched never matches it
            Optional<Holder.Reference<Block>> source = blocks.getHolder(sourceKey);
            if (source.isEmpty() || source.get().key() != sourceKey) return STOCK;   // a registered key that resolves elsewhere
            Optional<Holder.Reference<Block>> target = blocks.getHolder(e.getValue());
            if (target.isEmpty()) continue;                           // the registry's get finds no block: nothing swaps
            t.put(source.get().value(), target.get().value());
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: lithostitched_block_swap_table applies (Lithostitched's block swap processors use a block table){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return new SwapTable(t);
    }

    /** Shadow mode: compare Lithostitched's answer with the table's. */
    public static void shadow(Object stock, Object ours, String where) {
        SHADOW_CHECKS.incrementAndGet();
        if (!java.util.Objects.equals(describe(stock), describe(ours))) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: lithostitched_block_swap_table shadow mismatch #{} at {}: Lithostitched {} vs table {}", m, where, describe(stock), describe(ours));
        }
    }

    private static String describe(Object info) {
        if (!(info instanceof net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo i)) return String.valueOf(info);
        return i.pos() + " " + i.state() + " " + System.identityHashCode(i.nbt());
    }
}
