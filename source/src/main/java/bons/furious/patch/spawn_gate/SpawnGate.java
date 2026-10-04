package bons.furious.patch.spawn_gate;

import bons.furious.mixin.spawn_gate.ServerLevelEntityManagerAccessor;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_spawn_gate_visibility_memo (Minecraft 1.21.1 with NeoForge 21.1.252, the tested build;
 * side BOTH, acts on the server thread, including the integrated server). No Minecraft code here. Our own idea (found in
 * the 1.0.26 recording while measuring the natural-spawning census).
 *
 * Every tick, ServerChunkCache.tickChunks asks ServerLevel.isNaturalSpawningAllowed(ChunkPos) for every ticking chunk:
 * PersistentEntitySectionManager.canPositionTick, i.e. chunkVisibility.get(chunk key).isTicking() on a hash map holding
 * every loaded chunk. The probe reads the map's key and value arrays at a hashed slot, usually two cache misses per chunk:
 * 1.79-2.78% of the integrated server thread in our own recording (cli10_jfr1, all four windows; 1.20.1).
 *
 * Each LevelChunk now remembers the last answer for itself together with the epoch it was taken in: the chunk manager
 * keeps a long epoch for each of 256 groups of 8x8-chunk regions (VisibilityEpochs) and bumps the group's epoch at the
 * head of updateChunkStatus(ChunkPos, Visibility), the only method that changes chunkVisibility; every group is bumped
 * when PersistentEntitySectionManager.saveAll or ServerLevel.save returns (C2ME's shutdown module replaced saveAll there
 * on 1.20.1 and had its own access to the map). The gate answers from the chunk while the chunk's stamp equals its group's
 * epoch, and otherwise asks the map and stamps the answer. A change to any chunk of a group invalidates the group's
 * answers, so an answer can never be stale; epochs only grow (64 bits).
 *
 * Only for a level and a chunk manager of the vanilla classes (no override of isNaturalSpawningAllowed / canPositionTick),
 * and only when the chunk passed in is the one the position came from; everything else asks as before.
 *
 * -Dbons_and_furious.spawnGateVisibilityMemo=false asks the map every time. -Dbons_and_furious.spawnGateVisibilityMemo.shadow=true
 * (verification runs) also asks the map for every remembered answer and compares (SHADOW_CHECKS / SHADOW_MISMATCHES, first
 * 20 logged).
 *
 * Ported to 1.21.1: no change here; the 1.21.1 targets keep the same bodies (isNaturalSpawningAllowed(ChunkPos) =
 * entityManager.canPositionTick(pos) = chunkVisibility.get(pos.toLong()).isTicking(), the same single writer, same
 * main-thread status callbacks); only the mixins changed (loop chunk slot 16 -> 14, descriptor-qualified selectors).
 */
public final class SpawnGate {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.spawnGateVisibilityMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.spawnGateVisibilityMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    /** Number of epoch groups (power of two). */
    public static final int GROUPS = 256;

    private SpawnGate() {
    }

    /** A fresh epoch table: every group starts at 1, so a chunk's unset stamp (0) never matches. */
    public static long[] newEpochs() {
        long[] e = new long[GROUPS];
        Arrays.fill(e, 1L);
        return e;
    }

    /** The epoch group of a chunk: its 8x8-chunk region, hashed into GROUPS buckets. */
    public static int group(int chunkX, int chunkZ) {
        int h = (chunkX >> 3) * 0x9E3779B1 + (chunkZ >> 3) * 0x7FEB352D;
        return (h ^ (h >>> 16)) & (GROUPS - 1);
    }

    /** updateChunkStatus(pos, visibility) is about to change chunkVisibility at pos. */
    public static void bump(long[] epochs, int chunkX, int chunkZ) {
        epochs[group(chunkX, chunkZ)]++;
    }

    /** Something may have touched chunkVisibility anywhere. */
    public static void bumpAll(long[] epochs) {
        for (int i = 0; i < epochs.length; i++) epochs[i]++;
    }

    private static final ClassValue<Boolean> PLAIN_LEVEL = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("isNaturalSpawningAllowed", ChunkPos.class).getDeclaringClass() == ServerLevel.class;
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
        }
    };

    /** tickChunks' call of level.isNaturalSpawningAllowed(pos), pos being chunk.getPos(). */
    public static boolean allowed(ServerLevel level, ChunkPos pos, LevelChunk chunk) {
        if (!enabled || chunk == null || chunk.getPos() != pos || !PLAIN_LEVEL.get(level.getClass())) return level.isNaturalSpawningAllowed(pos);
        PersistentEntitySectionManager<?> manager = ((ServerLevelEntityManagerAccessor) level).bons$entityManager();
        if (manager == null || manager.getClass() != PersistentEntitySectionManager.class) return level.isNaturalSpawningAllowed(pos);
        long[] epochs = ((VisibilityEpochs) manager).bons$visEpochs();
        ChunkVisibilityMemo memo = (ChunkVisibilityMemo) chunk;
        // the group is remembered with the answer (a chunk's position never changes), so a remembered answer is checked
        // without reading the ChunkPos object; an unset memo has stamp 0, which no epoch ever equals
        if (memo.bons$visStamp() == epochs[memo.bons$visGroup()]) {
            boolean remembered = memo.bons$visTicking();
            if (SHADOW) shadow(level, pos, remembered);
            return remembered;
        }
        int group = group(pos.x, pos.z);
        long epoch = epochs[group];
        boolean answer = level.isNaturalSpawningAllowed(pos);
        memo.bons$visRemember(epoch, group, answer);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_spawn_gate_visibility_memo applies (ticking chunks remember their spawning gate until their region changes){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return answer;
    }

    private static void shadow(ServerLevel level, ChunkPos pos, boolean remembered) {
        boolean asked = level.isNaturalSpawningAllowed(pos);
        SHADOW_CHECKS.incrementAndGet();
        if (asked != remembered) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_spawn_gate_visibility_memo shadow mismatch #{}: chunk {} remembered {} but the entity manager says {}",
                    m, pos, remembered, asked);
        }
    }
}
