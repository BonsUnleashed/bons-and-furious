package bons.furious.patch.simplyswords_loot_hooks;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.resources.ResourceLocation;
import net.sweenus.simplyswords.config.SimplySwordsConfig;
import org.slf4j.Logger;

/**
 * Bons and Furious switch simplyswords_loot_table_name_first: the name tests of Simply Swords' loot table hooks, asked
 * before the hook's first config read (see LootHookNameFirstMixin). SRG: m_135815_ ResourceLocation.getPath.
 *
 * chestTable(id) is the hooks' own test, id.getPath().contains("chests") && !id.getPath().contains("spectrum").
 * listed(id) is SimplySwordsConfig.getLootList(id.toString()), the unique-weapon hook's first branch: a read of a static
 * map filled once in SimplySwordsConfig.init(), with no side effect, so asking it before the config read instead of
 * after changes nothing.
 *
 * -Dbons_and_furious.simplyswordsLootNames=false keeps the hooks as shipped (the config switch acts at class
 * transformation); .metrics=true counts and prints a summary when the JVM exits; .verify=true (rigs) lets every hook
 * run as shipped and watches the hooks' own addPool calls: a pool added to a table the name test would have left out
 * is counted and logged as an error. A data load without such a line shows that the name test leaves out only tables
 * the hooks add nothing to.
 */
public final class LootHookNames {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.simplyswordsLootNames.verify");
    public static final boolean ACTIVE = !VERIFY && !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.simplyswordsLootNames", "true"));
    static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.simplyswordsLootNames.metrics");
    /** False when the hooks run exactly as shipped and nothing is counted: the wrappers then go straight to the original call. */
    public static final boolean ON = ACTIVE || METRICS;
    /** Hook calls answered without the config read / handed to the original; pools the hooks added; pools added where the name test says no. */
    public static final LongAdder SKIPPED = new LongAdder(), KEPT = new LongAdder(), POOLS = new LongAdder(), WRONG = new LongAdder();

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious simplyswords_loot_table_name_first: hook calls the name test "
                    + (VERIFY ? "would leave out " : "left out ") + SKIPPED.sum() + ", hook calls run as shipped " + KEPT.sum() + ", pools the hooks added " + POOLS.sum()
                    + (VERIFY ? ", pools added to a table the name test would have left out " + WRONG.sum() : "")),
                    "Bons and Furious Simply Swords loot hooks summary"));
        }
    }

    private LootHookNames() {
    }

    private static boolean chest(ResourceLocation id) {
        String path = id.m_135815_();
        return path.contains("chests") && !path.contains("spectrum");
    }

    /** The three chest hooks: false when the hook is to be answered without its config read. */
    public static boolean chestTable(ResourceLocation id) {
        boolean acts = chest(id);
        if (METRICS) (acts ? KEPT : SKIPPED).increment();
        return acts;
    }

    /** The unique-weapon hook: false when the hook is to be answered without its config read. */
    public static boolean uniqueTable(ResourceLocation id) {
        boolean acts = chest(id) || SimplySwordsConfig.getLootList(id.toString());
        if (METRICS) (acts ? KEPT : SKIPPED).increment();
        return acts;
    }

    /** A hook's own addPool call (counted with the metrics flag; checked with the verify flag). */
    public static void pool(ResourceLocation id, boolean uniqueHook) {
        if (!METRICS) return;
        POOLS.increment();
        if (VERIFY && !(chest(id) || uniqueHook && SimplySwordsConfig.getLootList(id.toString()))) {
            WRONG.increment();
            LOGGER.error("Bons and Furious: simplyswords_loot_table_name_first would have left out {}, but Simply Swords' {} hook adds a pool to it",
                    id, uniqueHook ? "unique-weapon" : "chest");
        }
    }
}
