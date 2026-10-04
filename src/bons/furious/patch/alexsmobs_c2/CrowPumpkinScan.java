package bons.furious.patch.alexsmobs_c2;

import com.github.alexthe666.alexsmobs.misc.AMTagRegistry;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch alexsmobs_crow_scan_palette_check (Alex's Mobs 1.22.9, LGPL; both sides, the goal runs on
 * the server).
 *
 * A wild crow's EntityCrow$AIAvoidPumpkins.searchForDestination (every 70-220 canUse calls) reads the block at every
 * position of a 39 x 11 x 39 box around it, 16,731 Level.getBlockState calls, in Alex's Mobs' own order (layers dy -9..1,
 * then square rings of radius 0..19, then x and z alternating outwards), and stops at the first block in the tag
 * alexsmobs:crow_fears. Over open ground nothing is found: 16,731 reads to return false.
 *
 * {@link #noneFeared} answers "the scan finds nothing" without reading blocks: every block Level.getBlockState returns
 * for an in-height position of a loaded chunk is an entry of that section's palette, or AIR for a section holding only
 * air (vanilla and Radium alike). So when every section of the box holds only air (and AIR is not in the tag) or has a
 * palette with no tagged state, no read can match. The tag is evaluated now, so datapacks and /reload are respected; a
 * stale palette entry, a global palette or an air-tagged datapack only makes the check fall back.
 * It never changes chunk bookkeeping: chunks are first checked through their ChunkHolder (no lookup cache, no ticket);
 * any column that is not a full chunk with ticket level <= 33, a layer outside the build height, a debug world, another
 * thread or a non-server level runs the original. When the answer is "nothing", the scan's own chunk lookups are then
 * replayed in its order (Level.getChunk for each change of chunk along the scan; a lookup of the chunk the previous
 * lookup returned is a pure cache hit in vanilla and in Radium), so the chunk caches and UNKNOWN tickets see exactly the
 * calls the 16,731 reads would have made. Then the goal gets false and its destination is left untouched, as before.
 * Alex's Mobs is LGPL; this class carries none of its code (the ring order is reproduced as data for the replay).
 */
public final class CrowPumpkinScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch. -Dbons_and_furious.alexsmobsCrowScanPaletteCheck=false runs every pumpkin search in full. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.alexsmobsCrowScanPaletteCheck", "true"));
    /** Alex's Mobs' AIAvoidPumpkins: searchLength 20 (rings 0..19), layers -8..2 read at dy - 1. */
    static final int RINGS = 20, LAYER_FROM = -8, LAYER_TO = 2;
    private static final int FULL_CHUNK_LEVEL = 33;
    private static volatile boolean announced;

    private CrowPumpkinScan() {
    }

    /** ServerChunkCache.getVisibleChunkIfPresent, through the mixin's invoker. */
    public interface HolderLookup {
        ChunkHolder bons$visibleHolder(long pos);
    }

    /**
     * True when AIAvoidPumpkins.searchForDestination of this crow would read no alexsmobs:crow_fears block and return
     * false. In that case the scan's chunk lookups have just been replayed.
     */
    public static boolean noneFeared(Entity crow) {
        if (!enabled) return false;
        Level level = crow.m_9236_();
        if (!(level instanceof ServerLevel server) || level.m_46659_() || !server.m_7654_().m_18695_()) return false;
        BlockPos origin = crow.m_20183_();
        int x = origin.m_123341_(), y = origin.m_123342_(), z = origin.m_123343_();
        // layers: the same int arithmetic as the scan (setWithOffset adds dy - 1)
        int lowSection = Integer.MAX_VALUE, highSection = Integer.MIN_VALUE;
        for (int layer = LAYER_FROM; layer <= LAYER_TO; layer++) {
            int yy = y + (layer - 1);
            if (level.m_151562_(yy)) return false;                     // outside the build height: let the original read it
            int s = level.m_151564_(yy);
            lowSection = Math.min(lowSection, s);
            highSection = Math.max(highSection, s);
        }
        // the scan's chunk lookups for one layer (every layer repeats them), consecutive repeats dropped
        LongArrayList sequence = new LongArrayList(64);
        LongOpenHashSet columns = new LongOpenHashSet(32);
        long last = Long.MIN_VALUE;
        for (int r = 0; r < RINGS; r++) {
            int dx = 0;
            while (dx <= r) {
                int dz = dx < r && dx > -r ? r : 0;
                while (dz <= r) {
                    long key = ChunkPos.m_45589_((x + dx) >> 4, (z + dz) >> 4);
                    if (key != last) {
                        sequence.add(key);
                        columns.add(key);
                        last = key;
                    }
                    dz = dz > 0 ? -dz : 1 - dz;
                }
                dx = dx > 0 ? -dx : 1 - dx;
            }
        }
        // every column a full chunk at ticket level <= 33 (so the replay loads nothing), every section free of the tag
        HolderLookup holders = (HolderLookup) server.m_7726_();
        TagKey<Block> fears = AMTagRegistry.CROW_FEARS;
        if (Blocks.f_50016_.m_49966_().m_204336_(fears)) return false;
        var it = columns.iterator();
        while (it.hasNext()) {
            ChunkHolder holder = holders.bons$visibleHolder(it.nextLong());
            if (holder == null || holder.m_140093_() > FULL_CHUNK_LEVEL) return false;
            LevelChunk chunk = holder.m_212234_();
            if (chunk == null) return false;
            LevelChunkSection[] sections = chunk.m_7103_();
            for (int s = lowSection; s <= highSection; s++) {
                if (s < 0 || s >= sections.length) return false;
                LevelChunkSection section = sections[s];
                if (section == null) return false;
                if (section.m_188008_()) continue;                        // only air: every read returns AIR
                if (section.m_63019_().m_63109_(state -> state.m_204336_(fears))) return false;
            }
        }
        // nothing to find: make the scan's own chunk lookups, in its order, then answer for it
        long previous = Long.MIN_VALUE;
        for (int layer = LAYER_FROM; layer <= LAYER_TO; layer++) {
            for (int i = 0; i < sequence.size(); i++) {
                long key = sequence.getLong(i);
                if (key == previous) continue;
                level.m_6325_(ChunkPos.m_45592_(key), ChunkPos.m_45602_(key));
                previous = key;
            }
        }
        if (!announced) announce();
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: alexsmobs_crow_scan_palette_check: crows' pumpkin searches are answered from chunk palettes where nothing can match");
    }
}
