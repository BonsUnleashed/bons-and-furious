package bons.furious.patch.vanilla_unload_safety;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_entity_unload_snapshot (Minecraft 1.21.1 with NeoForge 21.1.252, server side). Kind: fix.
 * Idea: Collections Of Optimizations (vanilla.snapshotEntityUnloads), idea text only; the deferral design is ours. No
 * Mojang code here.
 *
 * The bug. PersistentEntitySectionManager.processUnloads walks chunksToUnload (a fastutil LongOpenHashSet) with removeIf,
 * i.e. its own iterator, and for every hidden chunk unloads the chunk's entities (processChunkUnload, storeChunkSections,
 * then setRemoved(UNLOADED_TO_CHUNK) on each entity and its passengers). Unloading an entity runs its level callbacks:
 * ServerLevel's EntityCallbacks.onTrackingEnd calls Entity.onRemovedFromLevel and posts NeoForge's EntityLeaveLevelEvent.
 * A listener that loads, keeps or releases a chunk there reaches updateChunkStatus(ChunkPos, Visibility) (a chunk demotion
 * calls ChunkMap.onFullChunkStatusChange directly, and the main thread runs scheduled promotions while it waits for a
 * chunk), which adds to or removes from that same set while it is being walked. fastutil's iterators are not fail-fast:
 * the walk then skips or repeats keys, removes the wrong key, or throws (e.g. a NullPointerException from the iterator's
 * wrapped list, or IllegalStateException), the crash seen after long-distance teleports.
 *
 * The fix. While a walk runs on a manager, the chunksToUnload.add / remove calls of that manager's updateChunkStatus are
 * queued instead of applied, and applied in their order right after the walk (a nested walk defers to the outermost
 * one). Everything else updateChunkStatus does (visibility, entity sections, entity load requests) still happens at once,
 * so the walk's own check (visibility != HIDDEN -> drop the key) sees the listener's latest decision. Without such a
 * listener nothing is queued and the walk is the stock one, call for call (same removeIf, same iterator, same order, same
 * set afterwards, table layout included).
 *
 * Concurrent Chunk Management Engine 0.4.0-alpha.0.122 (its chunk system rewrite, MixinServerEntityManager) makes
 * chunksToUnload a LongLinkedOpenHashSet and replaces processUnloads with a loop that takes one key at a time off its
 * front (removeFirstLong, then processChunkUnload). That loop holds no iterator, so a listener's changes cannot break it;
 * EntityUnloadDeferralMixin's wrap finds no removeIf there, no walk is opened and nothing is ever queued.
 *
 * -Dbons_and_furious.entityUnloadSnapshot=false switches the deferral off at run time.
 * -Dbons_and_furious.entityUnloadSnapshot.shadow=true (verification runs only): nothing is deferred; SHADOW_CHECKS counts
 * the walks that had keys and SHADOW_MISMATCHES the walks during which a listener changed the set (each one is the stock
 * hazard; WARN for the first 20).
 */
public final class UnloadSnapshot {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.entityUnloadSnapshot", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.entityUnloadSnapshot.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Set changes that arrived during a walk and were applied after it (all managers). */
    public static final AtomicLong DEFERRED = new AtomicLong();
    public static final byte ADD = 1, REMOVE = 2;
    private static volatile boolean announced;

    private UnloadSnapshot() {
    }

    /** The deferral state of one manager (held by EntityUnloadDeferralMixin). Server thread only. */
    public static final class Walk {
        int depth;
        boolean shadowTouched;
        final LongArrayList keys = new LongArrayList();
        final ByteArrayList ops = new ByteArrayList();

        public void begin() {
            this.depth++;
        }

        /** Applies the queued changes once the outermost walk ended. */
        public void end(LongSet chunksToUnload) {
            if (--this.depth > 0) {
                return;
            }
            this.depth = 0;
            if (SHADOW) {
                SHADOW_CHECKS.incrementAndGet();
                if (this.shadowTouched) {
                    this.shadowTouched = false;
                    long m = SHADOW_MISMATCHES.incrementAndGet();
                    if (m <= 20) {
                        LOGGER.warn("Bons and Furious: vanilla_entity_unload_snapshot shadow #{}: a listener changed the entity unload set while it was walked (stock walks a changing set)", m);
                    }
                }
                return;
            }
            int n = this.keys.size();
            if (n == 0) {
                return;
            }
            for (int i = 0; i < n; i++) {
                long key = this.keys.getLong(i);
                if (this.ops.getByte(i) == ADD) {
                    chunksToUnload.add(key);
                } else {
                    chunksToUnload.remove(key);
                }
            }
            this.keys.clear();
            this.ops.clear();
            DEFERRED.addAndGet(n);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_entity_unload_snapshot acts ({} chunk status changes made by entity unload listeners during the unload walk were applied after it)", n);
            }
        }

        /**
         * updateChunkStatus wants chunksToUnload.add (ADD) or .remove (REMOVE) of {@code key}: true when that change was
         * queued (a walk is running), false when the caller must apply it now.
         */
        public boolean defer(byte op, long key) {
            if (this.depth <= 0) {
                return false;
            }
            if (SHADOW) {
                this.shadowTouched = true;
                return false;
            }
            this.keys.add(key);
            this.ops.add(op);
            return true;
        }

        /** For the proof harness. */
        public int queued() {
            return this.keys.size();
        }
    }
}
