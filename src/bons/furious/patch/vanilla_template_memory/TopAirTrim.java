package bons.furious.patch.vanilla_template_memory;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_template_top_air_trim (Minecraft 1.20.1, both sides; kind change, off by default: with
 * the config key off the mixins are not applied). No Mojang code here.
 *
 * What vanilla does. A structure block saves every block of the box it was given, air included, and many mods draw that
 * box far taller than the build (a two-block path piece kept in a 48-block-high box). Every saved air entry is placed when
 * the piece generates: it carves the terrain above the build, and every one of them is held in memory while the template
 * is cached.
 *
 * What the switch does. A template read from a mod or data pack (StructureTemplateManager.loadFromResource, m_230427_;
 * never the world's own "generated" folder, the test structures or a structure block's save) drops, in each palette, the
 * air entries above its highest non-air block, keeping air up to two blocks above the highest entity so a saved mob still
 * has room. The template's declared size, its bounding boxes, jigsaws, entities and every other block stay as they were.
 * Trade-off (off by default): in newly generated chunks such a piece no longer carves the terrain above itself (trees,
 * hills or water above a buried or half-buried piece stay); pieces with a tall empty box save memory and place faster.
 *
 * -Dbons_and_furious.templateTopAirTrim=false switches it off at run time while the key is on.
 */
public final class TopAirTrim {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.templateTopAirTrim", "true"));
    /** Air entries kept above the highest entity's block (room for a saved mob). */
    public static final int ENTITY_HEADROOM = 2;
    private static final ThreadLocal<int[]> FROM_RESOURCE = ThreadLocal.withInitial(() -> new int[1]);
    public static final AtomicLong TEMPLATES = new AtomicLong(), DROPPED = new AtomicLong();
    private static volatile boolean announced;

    private TopAirTrim() {
    }

    /** loadFromResource HEAD / RETURN: templates loaded in between (on this thread) come from a mod or data pack. */
    public static void enterResource() {
        FROM_RESOURCE.get()[0]++;
    }

    public static void exitResource() {
        int[] d = FROM_RESOURCE.get();
        if (d[0] > 0) d[0]--;
    }

    public static boolean fromResource() {
        return enabled && FROM_RESOURCE.get()[0] > 0;
    }

    /**
     * The air entries of one palette above {@code keepUpTo} are removed (the list is the palette's own block list, built
     * by load); returns how many.
     */
    public static int trim(List<StructureTemplate.StructureBlockInfo> blocks, int entityTop) {
        int top = entityTop == Integer.MIN_VALUE ? Integer.MIN_VALUE : entityTop + ENTITY_HEADROOM;
        for (StructureTemplate.StructureBlockInfo b : blocks) if (!b.f_74676_().m_60795_()) top = Math.max(top, b.f_74675_().m_123342_());
        if (top == Integer.MIN_VALUE) return 0;   // all air and no entity: a piece made to clear space, kept as it is
        final int keep = top;
        int before = blocks.size();
        blocks.removeIf(b -> b.f_74676_().m_60795_() && b.f_74675_().m_123342_() > keep);
        int dropped = before - blocks.size();
        if (dropped > 0) {
            DROPPED.addAndGet(dropped);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_template_top_air_trim applies (structure templates from mods and data packs drop the empty air above their builds)");
            }
        }
        return dropped;
    }
}
