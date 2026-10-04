package bons.furious.patch.biomeswevegone;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch biomeswevegone_foreign_chunk_skip (Oh The Biomes We've Gone 1.8.0, All Rights Reserved - none
 * of its code is carried; server side of world generation). SRG names for Minecraft members.
 *
 * At the end of every chunk's surface step, in every dimension, BWG runs two terrain passes: CragGardenExtension (two
 * NormalNoise and two weighted state providers built, then a biome lookup per column) and BasaltBarreraExtension (two
 * ImprovedNoise built, then a biome lookup per column). Each pass changes a column only when that column's own biome
 * lookup, region.getBiome(worldX, landHeight, worldZ), is its biome (Crag Gardens / Basalt Barrera); everything before
 * that test only builds local objects and reads heightmaps.
 *
 * region.getBiome is vanilla's fuzzed BiomeManager lookup: it picks among the noise biomes of the quarts around the
 * block, which reach one quart into the neighbouring chunks, read from those chunks' biome palettes when the chunks are
 * at BIOMES or later. So when the centre chunk and its eight neighbours are all present in the region at BIOMES or
 * later and no section palette of any of them holds the biome (PalettedContainerRO.maybeHas: every value the section can
 * return, and possibly more), no column of the chunk can pass the test, the pass would change nothing, and it is
 * skipped. In every other case (a neighbour missing or not at BIOMES, the biome in some palette, the region not centred
 * on this chunk) the pass runs as before.
 *
 * The Crag Gardens pass receives only a biome lookup function, so the surface step's WorldGenRegion is kept per thread for
 * the length of that step (SurfaceRegionCaptureMixin / SurfaceRegionReleaseMixin), and used only when its centre is this
 * very chunk.
 *
 * -Dbons_and_furious.foreignChunkTerrainSkip=false always runs both passes; -Dbons_and_furious.foreignChunkTerrainSkip.shadow=true
 * (verification runs only) does every skipped pass's per-column biome test instead and counts columns that would have
 * matched (SHADOW_CHECKS = skipped passes checked, SHADOW_MISMATCHES = passes where a column matched).
 */
public final class ForeignChunkTerrain {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.foreignChunkTerrainSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.foreignChunkTerrainSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Passes skipped / run (diagnostics). */
    public static final AtomicLong SKIPPED = new AtomicLong(), RUN = new AtomicLong();
    private static final ThreadLocal<WorldGenRegion> SURFACE_REGION = new ThreadLocal<>();
    private static volatile boolean announced;

    private ForeignChunkTerrain() {
    }

    public static void surfaceStarted(WorldGenRegion region) {
        SURFACE_REGION.set(region);
    }

    public static void surfaceFinished() {
        SURFACE_REGION.remove();
    }

    /** The surface step's region when it is centred on this chunk, else null. */
    public static WorldGenRegion regionFor(ChunkAccess chunk) {
        WorldGenRegion r = SURFACE_REGION.get();
        return r != null && r.m_143488_().equals(chunk.m_7697_()) ? r : null;
    }

    /** True when the pass for the biome may be skipped: see the class comment. */
    public static boolean skip(WorldGenRegion region, ChunkAccess chunk, ResourceKey<Biome> biome) {
        if (!enabled || region == null || chunk == null) return false;
        ChunkPos center = chunk.m_7697_();
        if (!region.m_143488_().equals(center)) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkAccess c = region.m_6522_(center.f_45578_ + dx, center.f_45579_ + dz, ChunkStatus.f_62317_, false);
                if (c == null) {
                    RUN.incrementAndGet();
                    return false;
                }
                if (dx == 0 && dz == 0 && c != chunk) {
                    RUN.incrementAndGet();
                    return false;
                }
                for (LevelChunkSection s : c.m_7103_()) {
                    if (s.m_187996_().m_63109_(h -> h.m_203565_(biome))) {
                        RUN.incrementAndGet();
                        return false;
                    }
                }
            }
        }
        SKIPPED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: biomeswevegone_foreign_chunk_skip applies (BWG's Crag Gardens and Basalt Barrera passes skip chunks whose 3x3 area holds neither biome){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) shadow(region, chunk, biome);
        return true;
    }

    /** Shadow check: the pass's own per-column test on every column; any match is a mismatch. */
    private static void shadow(WorldGenRegion region, ChunkAccess chunk, ResourceKey<Biome> biome) {
        ChunkPos pos = chunk.m_7697_();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean matched = false;
        for (int x = 0; x < 16 && !matched; x++) {
            for (int z = 0; z < 16 && !matched; z++) {
                int wx = pos.m_151382_(x), wz = pos.m_151391_(z);
                int land = chunk.m_5885_(Heightmap.Types.OCEAN_FLOOR_WG, wx, wz) / 10 * 10;
                if (chunk.m_141937_() >= land) continue;
                m.m_122178_(wx, land, wz);
                Holder<Biome> b = region.m_204166_(m);
                matched = b.m_203565_(biome);
            }
        }
        SHADOW_CHECKS.incrementAndGet();
        if (matched && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: biomeswevegone_foreign_chunk_skip shadow mismatch: chunk {} has a {} column", pos, biome.m_135782_());
        }
    }
}
