package bons.furious.patch.valkyrien_client_chunks;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Bons and Furious switch valkyrien_client_chunk_drop (Valkyrien Skies 2.4.11 on Forge 47.4.16 / 47.4.28; client only).
 * [FIX] The client unloads the world chunks the server tells it to forget again, as Minecraft does without Valkyrien Skies.
 *
 * ClientChunkCache.drop (m_104455_) is how Minecraft's client unloads a chunk when the server sends "forget chunk" (the
 * player moved away): ChunkEvent.Unload, then Storage.replace(i, chunk, null), which calls ClientLevel.unload: the chunk's
 * block entities are unloaded (their tickers stop), its light is switched off and its entity sections stop ticking.
 * Valkyrien Skies 2.4.11's MixinClientChunkCache.preUnload (cancellable HEAD of drop) handles shipyard chunks itself and
 * then calls ci.cancel() for EVERY chunk: the not-in-shipyard branch jumps straight to the cancel. So on a Valkyrien Skies
 * client no chunk is ever dropped. A chunk the server forgets as the player moves on is still inside the client's range
 * (render distance + 3), the case drop handles: it stays the client's chunk there, its block entities tick every client
 * tick and it stays in memory until the player returns or a chunk one array width further on takes its slot, and no
 * ChunkEvent.Unload fires on the client (mods that free per-chunk client data on it never do). After a long teleport the
 * forgotten chunks are already outside the client's range: Minecraft's drop does nothing for those either.
 *
 * Here VS's cancel call is made only for shipyard chunks (the same VSGameUtilsKt.isChunkInShipyard test VS makes first);
 * for any other chunk drop continues into Minecraft's own code, exactly as without Valkyrien Skies. Ship chunks keep
 * Valkyrien Skies' handling (and valkyrien_client_ship_chunk_unload's cleanup); shipyard chunks are not in Minecraft's
 * chunk array anyway, so Minecraft's drop would find nothing there.
 *
 * Runtime switch -Dbons_and_furious.valkyrienClientChunkDrop=false: every drop is cancelled again (VS 2.4.11's behaviour).
 * -Dbons_and_furious.valkyrienClientChunkDrop.shadow=true (verification runs): the chunk each passed-through drop removes is
 * remembered (weakly) and checked at the next drop: it must have left the client's chunk array and hold no block entity
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, the first 20 logged; expect 0).
 */
public final class WorldChunkDrop {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.valkyrienClientChunkDrop", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.valkyrienClientChunkDrop.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** World-chunk drops handed to Minecraft, shipyard drops left to Valkyrien Skies (for tests and the rig). */
    public static final AtomicLong PASSED = new AtomicLong(), SHIP_DROPS = new AtomicLong();
    private static volatile boolean announced;
    /** SHADOW only (client thread): the last world chunk handed to Minecraft's drop, and where it was. */
    private static WeakReference<LevelChunk> pending;
    private static int pendingX, pendingZ;

    private WorldChunkDrop() {
    }

    /**
     * For the mixin, at Valkyrien Skies' ci.cancel() in preUnload: true = do not cancel (a world chunk: Minecraft's drop
     * runs), false = cancel as Valkyrien Skies does (a shipyard chunk, or the switch is off).
     */
    public static boolean vanillaDrop(ClientLevel level, int chunkX, int chunkZ) {
        if (!enabled) return false;
        if (SHADOW) verifyPending(level);
        if (VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) {
            SHIP_DROPS.incrementAndGet();
            return false;
        }
        long n = PASSED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: valkyrien_client_chunk_drop lets Minecraft unload the world chunks the server tells the client to forget "
                    + "(Valkyrien Skies 2.4.11 cancelled every drop; first: chunk [{}, {}]){}", chunkX, chunkZ, SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            LevelChunk chunk = level.m_7726_().m_7587_(chunkX, chunkZ, ChunkStatus.f_62326_, false);   // getChunkSource().getChunk(x, z, FULL, false)
            pending = chunk == null ? null : new WeakReference<>(chunk);
            pendingX = chunkX;
            pendingZ = chunkZ;
        }
        return true;
    }

    /** SHADOW: the chunk the previous passed-through drop removed is out of the client's array and has no block entity. */
    private static void verifyPending(ClientLevel level) {
        WeakReference<LevelChunk> ref = pending;
        if (ref == null) return;
        pending = null;
        LevelChunk old = ref.get();
        if (old == null) return;                         // already collected: it was unloaded and released
        SHADOW_CHECKS.incrementAndGet();
        LevelChunk now = level.m_7726_().m_7587_(pendingX, pendingZ, ChunkStatus.f_62326_, false);
        int blockEntities = old.m_62954_().size();       // m_62954_ = getBlockEntities
        if (now == old || blockEntities > 0) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20)
                LOGGER.warn("Bons and Furious: valkyrien_client_chunk_drop shadow mismatch #{}: chunk [{}, {}] {} after its drop ({} block entities left)",
                        n, pendingX, pendingZ, now == old ? "is still in the client's chunk array" : "left the array", blockEntities);
        }
    }
}
