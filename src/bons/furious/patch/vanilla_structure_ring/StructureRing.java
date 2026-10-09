package bons.furious.patch.vanilla_structure_ring;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_structure_ring_search (Minecraft 1.20.1; server side: /locate, explorer and treasure
 * maps, dolphins, eyes of ender for non-stronghold sets, Async Locator). SRG member names.
 *
 * <p>ChunkGenerator.getNearestGeneratedStructure for a random-spread structure set (m_223188_) is called once per ring
 * r = 0, 1, 2, ... by findNearestMapStructure. For ring r it walks the whole (2r+1) x (2r+1) square of spacing cells
 * row by row and skips every cell that is not on the ring's border; only border cells reach getPotentialStructureChunk and
 * getStructureGeneratingAt. A search out to ring R walks about 4/3 R^3 interior cells for nothing (1.3 million at R =
 * 100, per structure set).
 *
 * <p>The switch walks the same border cells in the same order directly: the first and last row in full, every other row
 * only at its first and last column (row -r: all columns; rows -r+1 .. r-1: columns -r and r; row r: all columns; ring 0:
 * the one cell). Each visited cell runs exactly the original calls - getPotentialStructureChunk(seed, x + spacing * dx,
 * z + spacing * dz) and getStructureGeneratingAt - and the first hit is returned, so the result, the chunks loaded, the
 * structure checks and references added are those of the original, in the same order.
 *
 * <p>Runtime switch -Dbons_and_furious.structureRing=false. Shadow mode -Dbons_and_furious.structureRing.shadow=true:
 * before each search the visiting order is also derived the original way (the full square, border test per cell) and
 * compared position by position with the direct walk (pure arithmetic, no structure checks); a difference logs a warning
 * (at most 20) and that search runs the original method.
 */
public final class StructureRing {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.structureRing", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.structureRing.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private StructureRing() {
    }

    /** Called once per served search: one INFO line the first time. */
    public static void served() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_structure_ring_search walks only the border cells of each ring in nearest-structure searches");
        }
    }

    /**
     * Shadow mode: the border cells of ring {@code radius} in the original order (the full square with the border test)
     * against the direct walk, as getPotentialStructureChunk results. True when identical (the search may use the direct
     * walk), false after logging the difference.
     */
    public static boolean shadowAgrees(RandomSpreadStructurePlacement placement, long seed, int sectionX, int sectionZ, int radius) {
        SHADOW_CHECKS.incrementAndGet();
        int spacing = placement.m_205003_();
        java.util.List<ChunkPos> original = new java.util.ArrayList<>(), direct = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; ++dx) {
            boolean edgeX = dx == -radius || dx == radius;
            for (int dz = -radius; dz <= radius; ++dz) {
                boolean edgeZ = dz == -radius || dz == radius;
                if (!edgeX && !edgeZ) continue;
                original.add(placement.m_227008_(seed, sectionX + spacing * dx, sectionZ + spacing * dz));
            }
        }
        for (int dx = -radius; dx <= radius; ++dx) {
            int step = (dx == -radius || dx == radius) ? 1 : 2 * radius;
            for (int dz = -radius; dz <= radius; dz += step) direct.add(placement.m_227008_(seed, sectionX + spacing * dx, sectionZ + spacing * dz));
        }
        if (original.equals(direct)) return true;
        long n = SHADOW_MISMATCHES.incrementAndGet();
        if (n <= 20) LOGGER.warn("Bons and Furious: vanilla_structure_ring_search shadow mismatch {} (ring {} at {},{}: {} cells vs {})", n, radius, sectionX, sectionZ,
                original.size(), direct.size());
        return false;
    }
}
