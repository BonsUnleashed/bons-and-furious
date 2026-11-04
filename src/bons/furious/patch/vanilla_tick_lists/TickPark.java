package bons.furious.patch.vanilla_tick_lists;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_level_ticks_blocked_park (Minecraft 1.20.1 on Forge 47.4.28, server side incl. the
 * integrated server). Kind: exact optimization. Idea: Collections Of Optimizations (vanilla.deferBlockedTickContainers),
 * idea text only; the design (park in place, unpark on the inputs' own changes) is ours. No Mojang code here.
 *
 * What the game does. Every tick LevelTicks.sortContainersToTick (m_193216_) walks nextTickForContainer (chunk -> due
 * time of its earliest scheduled block or fluid tick). For every chunk that is due it looks the container up, peeks its
 * head and asks the level's tick check, ServerLevel.isPositionTickingWithEntitiesLoaded (m_184110_): entities loaded
 * (PersistentEntitySectionManager.areEntitiesLoaded), a visible chunk holder (ChunkMap.getVisibleChunkIfPresent), inside
 * block-ticking range (DistanceManager.inBlockTickingRange = ChunkLevel.isBlockTicking(TickingTracker.getLevel)) and the
 * holder's ticking future completed with a chunk. A chunk with an overdue tick that may not tick (view distance larger
 * than simulation distance, a chunk still loading) answers false and is asked again on every tick, by both LevelTicks
 * (blocks and fluids) of the level, for as long as it stays like that.
 *
 * What the switch does. When the check answers false, the entry is PARKED IN PLACE: its value (the due time V) is kept
 * here and the entry's value is set to MARKER (Long.MAX_VALUE - 1) by a put on its existing key, so the next walks
 * stop at the first comparison (MARKER > game time), exactly where a not-yet-due entry stops. The map never changes
 * structure for this (put on an existing key only, Long2LongOpenHashMap only), so its iteration order, and with it the order in which
 * due containers enter the PriorityQueue (whose ties - saved ticks share sub-tick orders across chunks - depend on that
 * order) stay stock. An entry is unparked (V written back where MARKER still stands) at the head of the next walk after
 * one of the check's inputs for that chunk may have turned true:
 *   - TickingTracker.setLevel(chunk, level) with a block-ticking level           (inBlockTickingRange)
 *   - ServerLevel.startTickingChunk(chunk), which runs inside the ticking future just before it completes   (future)
 *   - PersistentEntitySectionManager.processPendingLoads writing LOADED for the chunk                     (entities loaded)
 *   - ChunkMap.promoteChunkMap() answering true: every parked chunk whose visible holder is not the one it had (holder)
 *   - any of these arriving from a thread other than the level's (never in vanilla): every entry
 *   - every 20th walk: every entry (a safety net for checks other mods might add; with the inputs above the check gives
 *     the same false again and the entry is parked again)
 * A put by the schedule callback (a new earliest tick), addContainer, removeContainer or clearArea overwrite or remove the
 * marker exactly as they overwrite or remove V in stock; a kept V whose marker is gone is dropped. Between those events
 * stock's walk would only ask the check and get false (the check has no side effects), so the walk that runs a container's
 * ticks is the same walk as in stock, and so are the ticks, their order and their game time.
 * Parking is used only where the inputs are the vanilla ones: the level, its chunk source, chunk map, distance manager,
 * ticking tracker, entity manager and both LevelTicks are exactly the vanilla classes (C2ME's no-tick-view-distance option
 * replaces the ticking tracker, and the level then runs stock); otherwise that level keeps the stock walk.
 *
 * -Dbons_and_furious.levelTicksBlockedPark=false switches it off at run time (the next walk unparks everything).
 * -Dbons_and_furious.levelTicksBlockedPark.shadow=true (verification runs only): nothing is parked; the would-be parked
 * entries are tracked with the same unpark rules, and every time the stock walk asks the check for one of them
 * SHADOW_CHECKS counts it and SHADOW_MISMATCHES counts a true answer (a tick the switch would have run late; WARN for the
 * first 20).
 */
public final class TickPark {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.levelTicksBlockedPark", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.levelTicksBlockedPark.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Entries parked / unparked so far (all levels). */
    public static final AtomicLong PARKS = new AtomicLong(), UNPARKS = new AtomicLong();
    public static final long MARKER = Long.MAX_VALUE - 1;
    public static final int FULL_UNPARK_EVERY = 20;
    private static volatile boolean announced, declined;

    private TickPark() {
    }

    /** The park state of one LevelTicks. Used on its level's thread only. */
    public static final class Parked {
        final Router router;
        final Long2LongOpenHashMap saved = new Long2LongOpenHashMap();          // chunk -> V (real) / value seen at park (shadow)
        final Long2ObjectOpenHashMap<Object> holders = new Long2ObjectOpenHashMap<>();
        final LongOpenHashSet pending = new LongOpenHashSet();
        volatile boolean unparkAll;
        int walks;

        Parked(Router router) {
            this.router = router;
        }

        /** Head of sortContainersToTick: unpark what may have become runnable since the last walk. */
        public void beforeWalk(Long2LongMap map) {
            if (!enabled || this.unparkAll || ++this.walks >= FULL_UNPARK_EVERY) {
                this.unparkAll = false;
                this.walks = 0;
                if (!this.saved.isEmpty()) {
                    this.restoreAll(map);
                }
                this.pending.clear();
                return;
            }
            if (this.pending.isEmpty()) {
                return;
            }
            LongIterator it = this.pending.iterator();
            while (it.hasNext()) {
                this.restore(map, it.nextLong());
            }
            this.pending.clear();
        }

        private void restore(Long2LongMap map, long pos) {
            if (!this.saved.containsKey(pos)) {
                return;
            }
            long v = this.saved.remove(pos);
            this.holders.remove(pos);
            if (!SHADOW && map.get(pos) == MARKER) {
                map.put(pos, v);    // an existing key: the value is replaced, the table is not touched
                UNPARKS.incrementAndGet();
            }
        }

        private void restoreAll(Long2LongMap map) {
            if (!SHADOW) {
                ObjectIterator<Long2LongMap.Entry> it = this.saved.long2LongEntrySet().fastIterator();
                while (it.hasNext()) {
                    Long2LongMap.Entry e = it.next();
                    long pos = e.getLongKey();
                    if (map.get(pos) == MARKER) {
                        map.put(pos, e.getLongValue());
                        UNPARKS.incrementAndGet();
                    }
                }
            }
            this.saved.clear();
            this.holders.clear();
        }

        /**
         * The tick check answered false for the due entry of {@code pos}, the walk's current entry (real mode). Its value
         * is replaced by a put on its existing key: Long2LongOpenHashMap writes the value slot in place (no rehash, no
         * structural change), so the walk's iterator and the map's order are untouched. Any other map class: not parked.
         */
        public void park(long pos, Long2LongMap map) {
            if (!enabled || map.getClass() != Long2LongOpenHashMap.class) {
                return;
            }
            long v = map.get(pos);
            if (v == MARKER || !map.containsKey(pos)) {
                return;
            }
            this.saved.put(pos, v);
            this.holders.put(pos, this.router.visibleHolder(pos));
            map.put(pos, MARKER);
            PARKS.incrementAndGet();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_level_ticks_blocked_park applies (overdue block and fluid ticks of chunks that may not tick yet wait parked until something they depend on changes)");
            }
        }

        /** Shadow mode: the stock walk asked the check for the due entry of {@code pos} and got {@code answer}. */
        public void shadowVisit(long pos, boolean answer, Long2LongMap map) {
            long value = map.get(pos);
            if (this.saved.containsKey(pos)) {
                if (this.saved.get(pos) == value) {
                    long c = SHADOW_CHECKS.incrementAndGet();
                    if (answer) {
                        long m = SHADOW_MISMATCHES.incrementAndGet();
                        if (m <= 20) {
                            LOGGER.warn("Bons and Furious: vanilla_level_ticks_blocked_park shadow mismatch #{} (of {} checks): chunk {} {} became runnable without a watched change",
                                    m, c, (int) pos, (int) (pos >>> 32));
                        }
                    }
                }
                this.saved.remove(pos);
                this.holders.remove(pos);
            }
            if (!answer && enabled) {
                this.saved.put(pos, value);
                this.holders.put(pos, this.router.visibleHolder(pos));
            }
        }

        /** The map entry of {@code pos} is about to be written or removed by LevelTicks itself (put / remove). */
        public void forget(long pos) {
            if (!this.saved.isEmpty() && this.saved.containsKey(pos)) {
                this.saved.remove(pos);
                this.holders.remove(pos);
            }
        }

        /**
         * addContainer is about to run for {@code pos}: V goes back first, because addContainer writes the entry only when
         * the new container has a tick, and otherwise stock's next walk must find V there (and remove the entry).
         */
        public void unparkNow(Long2LongMap map, long pos) {
            if (!this.saved.isEmpty()) {
                this.restore(map, pos);
            }
        }

        void trigger(long pos) {
            if (this.saved.containsKey(pos)) {
                this.pending.add(pos);
            }
        }

        void revalidate() {
            if (this.holders.isEmpty()) {
                return;
            }
            ObjectIterator<Long2ObjectMap.Entry<Object>> it = this.holders.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                Long2ObjectMap.Entry<Object> e = it.next();
                if (this.router.visibleHolder(e.getLongKey()) != e.getValue()) {
                    this.pending.add(e.getLongKey());
                }
            }
        }

        /** For the proof harness. */
        public int parkedCount() {
            return this.saved.size();
        }
    }

    /** Routes the inputs' changes of one level to the park state of its two LevelTicks. */
    public static final class Router {
        public final Parked block, fluid;
        private final Thread owner;
        private final LongFunction<Object> visible;

        public Router(Thread owner, LongFunction<Object> visible) {
            this.owner = owner;
            this.visible = visible;
            this.block = new Parked(this);
            this.fluid = new Parked(this);
        }

        Object visibleHolder(long pos) {
            return this.visible.apply(pos);
        }

        private boolean foreignThread() {
            if (Thread.currentThread() == this.owner) {
                return false;
            }
            this.block.unparkAll = true;
            this.fluid.unparkAll = true;
            return true;
        }

        /** One of the check's inputs for chunk {@code pos} may have turned true. */
        public void trigger(long pos) {
            if (this.foreignThread()) {
                return;
            }
            this.block.trigger(pos);
            this.fluid.trigger(pos);
        }

        /** ChunkMap.promoteChunkMap() replaced the visible holder map. */
        public void holdersChanged() {
            if (this.foreignThread()) {
                return;
            }
            this.block.revalidate();
            this.fluid.revalidate();
        }
    }

    /** One INFO line when a level keeps the stock walk because one of its parts is not the vanilla class. */
    public static void declined(String level, String why) {
        if (!declined) {
            declined = true;
            LOGGER.info("Bons and Furious: vanilla_level_ticks_blocked_park leaves {} unchanged ({}); further levels like it are not logged", level, why);
        }
    }
}
