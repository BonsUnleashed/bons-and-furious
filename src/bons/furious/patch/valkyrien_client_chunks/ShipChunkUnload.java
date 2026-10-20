package bons.furious.patch.valkyrien_client_chunks;

import bons.furious.mixin.valkyrien_client_chunks.LevelTickerListsAccessor;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

/**
 * Bons and Furious switch valkyrien_client_ship_chunk_unload (Valkyrien Skies 2.4.11 on Forge 47.4.16 / 47.4.28; client
 * only). [FIX] The block entities of a ship chunk the client drops are unloaded, as Minecraft unloads those of any chunk.
 *
 * Minecraft's client unloads a chunk in ClientChunkCache.drop (m_104455_): ClientLevel.unload (m_104665_) calls
 * LevelChunk.clearAllBlockEntities (m_187957_), which runs Forge's onChunkUnloaded and setRemoved for every block entity
 * of the chunk and rebinds its tickers to the no-op ticker, so the level drops them from its ticker list at its next
 * block-entity tick. Valkyrien Skies keeps ship (shipyard) chunks in its own map on the client
 * (MixinClientChunkCache.vs$shipChunks) and cancels drop at its head; for a shipyard chunk its handler preUnload removes
 * the chunk from that map, and when a ship is unloaded on the client (ShipUnloadEventClient) removeShipChunk does the
 * same. Neither unloads the chunk's block entities: they stay in ClientLevel.blockEntityTickers (on the client every
 * registered ticker ticks: Level.shouldTickBlocksAt is true and LevelChunk.isTicking only checks the world border), keep
 * ticking, and keep the dropped LevelChunk reachable. When the ship comes back into range the chunk is sent again; Valkyrien
 * Skies no longer has it, builds a new LevelChunk, and the new block entities register new tickers: after N unload/reload
 * cycles a ship block entity has N + 1 tickers on the client (Valkyrien Skies issue #1403, fixed upstream by PR #1407 on
 * 1.20.1/main, not in any release; idea text only).
 *
 * Here the chunk Valkyrien Skies has just removed from its map (the return value of its LongObjectMap.remove call, in
 * either handler) gets clearAllBlockEntities, the block-entity part of Minecraft's unload. Valkyrien Skies' own
 * replacement of that method (fix_tp_ships_cross_dimension.MixinLevelChunk, priority 1500: the same steps on snapshots)
 * is what runs. Light, entity sections and Forge's chunk events stay as Valkyrien Skies has them (it posts no
 * ChunkEvent.Load for ship chunks either). Nothing else changes: the chunk is returned to Valkyrien Skies unchanged, a
 * chunk that is not in the map (null) is left alone, and the call is idempotent (a second clear finds empty maps). Both
 * paths run on the client thread (the forget-chunk packet is handled there; ShipUnloadEventClient is emitted by the
 * client ship world's tick-driven dispatcher).
 *
 * Runtime switch -Dbons_and_furious.valkyrienClientShipChunkUnload=false: dropped ship chunks keep their block entities
 * (Valkyrien Skies 2.4.11's behaviour). -Dbons_and_furious.valkyrienClientShipChunkUnload.shadow=true (verification runs):
 * after each unload the level's ticker lists are searched for a live ticker inside the dropped chunk (SHADOW_CHECKS per
 * chunk, SHADOW_MISMATCHES per live ticker found, the first 20 logged; expect 0).
 */
public final class ShipChunkUnload {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.valkyrienClientShipChunkUnload", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.valkyrienClientShipChunkUnload.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Dropped ship chunks unloaded, block entities they held, unload calls that threw (for tests and the rig). */
    public static final AtomicLong CHUNKS = new AtomicLong(), BLOCK_ENTITIES = new AtomicLong(), FAILURES = new AtomicLong();
    private static volatile boolean announced;

    private ShipChunkUnload() {
    }

    /**
     * For the mixin: the value Valkyrien Skies' LongObjectMap.remove returned in preUnload ("drop") or removeShipChunk
     * ("ship unload"). Returned unchanged.
     */
    public static Object removed(Object removed, String path) {
        if (enabled && removed instanceof LevelChunk chunk) unload(chunk, path);
        return removed;
    }

    private static void unload(LevelChunk chunk, String path) {
        int blockEntities = chunk.m_62954_().size();     // m_62954_ = getBlockEntities
        try {
            chunk.m_187957_();                           // m_187957_ = clearAllBlockEntities
        } catch (RuntimeException e) {
            long n = FAILURES.incrementAndGet();
            if (n <= 5)
                LOGGER.warn("Bons and Furious: valkyrien_client_ship_chunk_unload could not unload the block entities of ship chunk {} (failure {}); "
                        + "they stay as Valkyrien Skies left them", chunk.m_7697_(), n, e);
            return;
        }
        CHUNKS.incrementAndGet();
        BLOCK_ENTITIES.addAndGet(blockEntities);
        if (!announced && blockEntities > 0) {
            announced = true;
            ChunkPos pos = chunk.m_7697_();
            LOGGER.info("Bons and Furious: valkyrien_client_ship_chunk_unload unloads the block entities of ship chunks the client drops "
                    + "(first: {} block entities of ship chunk [{}, {}], {}){}", blockEntities, pos.f_45578_, pos.f_45579_, path,
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) verify(chunk);
    }

    /** SHADOW: no live ticker may be left inside the chunk just unloaded. */
    private static void verify(LevelChunk chunk) {
        SHADOW_CHECKS.incrementAndGet();
        Level level = chunk.m_62953_();                  // m_62953_ = getLevel
        if (!(level instanceof LevelTickerListsAccessor lists)) return;
        ChunkPos pos = chunk.m_7697_();
        int live = count(lists.bons$vccTickers(), pos) + count(lists.bons$vccPendingTickers(), pos);
        if (live > 0) {
            long n = SHADOW_MISMATCHES.addAndGet(live);
            if (n - live < 20)
                LOGGER.warn("Bons and Furious: valkyrien_client_ship_chunk_unload shadow mismatch: {} live ticker(s) left in ship chunk [{}, {}] "
                        + "after its block entities were unloaded ({} so far)", live, pos.f_45578_, pos.f_45579_, n);
        }
    }

    private static int count(List<TickingBlockEntity> tickers, ChunkPos pos) {
        int n = 0;
        for (int i = 0, size = tickers.size(); i < size; i++) {
            TickingBlockEntity t = tickers.get(i);
            if (t.m_142220_()) continue;                 // m_142220_ = isRemoved
            BlockPos p = t.m_142689_();                  // m_142689_ = getPos
            if (p.m_123341_() >> 4 == pos.f_45578_ && p.m_123343_() >> 4 == pos.f_45579_) n++;
        }
        return n;
    }
}
