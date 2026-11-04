package bons.furious.patch.vanilla_template_memory;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_template_position_share (Minecraft 1.20.1, both sides: the server loads structure
 * templates for world generation, the client for its integrated server). Kind: exact variant. No Mojang code here.
 *
 * What vanilla does. StructureTemplate.loadPalette (m_247272_) reads the block list of a template and makes a new BlockPos
 * for every block entry (and load, m_246595_, one for every entity entry); a template with several palettes repeats the
 * whole list per palette. Loaded templates stay cached for the session, so a pack with large structure mods holds millions
 * of position objects, almost all of them repeats of the same few hundred thousand small coordinates.
 *
 * What the switch does. A position whose three coordinates are 0..63 (every block of nearly every template; a template is
 * at most 48 blocks across in practice) is taken from one shared table instead of being made again; any other position
 * is made as before. BlockPos is immutable, so the entries hold the same coordinates and every reader sees the same
 * values. Exact variant: equal positions in loaded templates are the same object (positions were only ever compared by
 * value: a census of every client and server mod jar finds no identity comparison of a template block's position).
 *
 * The table holds all 262,144 positions 0..63 (about 7 MB), made in one go the first time a template position is asked
 * for (class initialization: every thread then sees them complete), in the order saved templates list their blocks (by
 * y, then x, then z). A lazily filled table measured 4 % slower template loads: load sorts each block list by position,
 * and positions made at random times lie scattered in memory; made in that order, a template's positions lie next to
 * each other as freshly made ones do.
 *
 * -Dbons_and_furious.templatePositionShare=false switches it off at run time (templates loaded afterwards).
 */
public final class TemplatePositions {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.templatePositionShare", "true"));
    private static final int BITS = 6, SIZE = 1 << BITS, MASK = SIZE - 1;

    private TemplatePositions() {
    }

    /** The position (x, y, z) for a template entry: shared when all three are 0..63, else a new one as before. */
    public static BlockPos at(int x, int y, int z) {
        if (!enabled || ((x | y | z) & ~MASK) != 0) return new BlockPos(x, y, z);
        return Table.POSITIONS[(y << (2 * BITS)) | (x << BITS) | z];
    }

    /** Loaded on the first shared position only (not while the switch is off). */
    private static final class Table {
        static final BlockPos[] POSITIONS = fill();

        private static BlockPos[] fill() {
            BlockPos[] t = new BlockPos[SIZE * SIZE * SIZE];
            for (int y = 0; y < SIZE; y++)
                for (int x = 0; x < SIZE; x++)
                    for (int z = 0; z < SIZE; z++) t[(y << (2 * BITS)) | (x << BITS) | z] = new BlockPos(x, y, z);
            LOGGER.info("Bons and Furious: vanilla_template_position_share applies (structure templates share their block positions)");
            return t;
        }
    }

    /** For the proof harness: positions the table holds (it is made whole on the first shared position). */
    public static int filled() {
        return Table.POSITIONS.length;
    }
}
