package bons.furious.patch.lithostitched_c49;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.Map;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lithostitched_block_swap_table (Lithostitched 1.4.11, MIT; server side incl. the integrated
 * server). Ours; no Lithostitched code is carried.
 *
 * What Lithostitched does. BlockSwapStructureProcessor.processBlock (m_7382_) runs for every block of every template piece
 * its processor list applies to: BuiltInRegistries.BLOCK.getKey(block) (a registry reverse lookup), blockSwapMap.containsKey
 * (a hash of a fresh ResourceLocation hash and an equals), and on a match blockSwapMap.get and BLOCK.get by id (two more
 * lookups) before building the swapped StructureBlockInfo.
 *
 * What the switch does. Each processor builds, on its first call, an identity table source Block -> target Block from its
 * id map: for every source id that names a registered block, BLOCK.get(source) -> BLOCK.get(target) (a missing target
 * resolves to the registry's default block, air, exactly as BLOCK.get does in Lithostitched's call). processBlock then
 * answers with one identity lookup and builds the same StructureBlockInfo (same position, target.withPropertiesOf(state),
 * same NBT object), or returns the very same info object when the block has no entry.
 *
 * Why the result is identical. For a registered block b, Lithostitched matches when getKey(b) is a source id; then
 * BLOCK.get(getKey(b)) == b, so b is in the table, and the target is the block BLOCK.get(target id) resolves to (the
 * registries are frozen before any structure is placed; the table is built on the first placement). Source ids that name
 * no block can never equal getKey of any block except through the registry's default key, so: a map whose sources include
 * the default key (minecraft:air), which Lithostitched also matches for unregistered blocks, and any map that is not an
 * immutable Guava map (the codec decodes into one; a hand-made mutable map could change) keep Lithostitched's own path.
 * A source id that is a Forge registry alias (Farmer's Delight's farmersdelight:basket for bamboo_basket, say) is left out:
 * containsKey and get follow the alias, but getKey(b) is always the block's own name, so Lithostitched never matches an
 * alias (and a map naming one block under both keys keeps the entry under its own name). Bug sweep 2026-11-11 (CC1).
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

    /** Built once per processor (the result is published through the processor's volatile field). */
    public static SwapTable build(Map<ResourceLocation, ResourceLocation> blockSwapMap) {
        if (!(blockSwapMap instanceof ImmutableMap)) return STOCK;
        Registry<Block> blocks = BuiltInRegistries.f_256975_;
        if (!(blocks instanceof DefaultedRegistry<Block> defaulted) || blockSwapMap.containsKey(defaulted.m_122315_())) return STOCK;
        Reference2ReferenceOpenHashMap<Block, Block> t = new Reference2ReferenceOpenHashMap<>(Math.max(4, blockSwapMap.size()));
        for (Map.Entry<ResourceLocation, ResourceLocation> e : blockSwapMap.entrySet()) {
            ResourceLocation id = e.getKey();
            if (!blocks.m_7804_(id)) continue;                    // names no block: getKey never yields it
            Block source = blocks.m_7745_(id);
            if (!id.equals(blocks.m_7981_(source))) continue;     // an alias: getKey yields the block's own name, never this id
            t.put(source, blocks.m_7745_(e.getValue()));
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
        return i.f_74675_() + " " + i.f_74676_() + " " + System.identityHashCode(i.f_74677_());
    }
}
