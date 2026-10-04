package bons.furious.patch.vanilla_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/**
 * Bons and Furious switches vanilla_suffocation_scan_loop and vanilla_fire_scan_loop (Minecraft 1.21.1 with NeoForge
 * 21.1.252, both sides). Mojang member names (NeoForge runs them in development and production).
 *
 * Two entity checks scan the blocks in a box through a Java stream every tick:
 * Entity.isInWall (every living entity, every tick: BlockPos.betweenClosedStream(box).anyMatch(suffocates)) and the
 * fire check at the end of Entity.move (every moving entity, every tick:
 * level.getBlockStatesIfLoaded(box).noneMatch(fire or lava)). Each builds an iterator spliterator, a pipeline head, a
 * map stage (move only) and the match operation's sink objects to look at one to a dozen blocks.
 *
 * The mixin wraps the call that builds the stream and returns a DeferredStream. Its match operations walk vanilla's own
 * BlockPos.betweenClosed iterable over the box corners vanilla computes (Mth.floor of the box's min and max), in the
 * iterable's order, and stop at the first decisive element: what the stream's match operation does element by element.
 * The move check also asks hasChunksAt over the same corners first and treats an unloaded box as an empty stream, as
 * LevelReader.getBlockStatesIfLoaded does, and reads every block through the level's own getBlockState. The predicate
 * is the call site's own lambda, so whatever another mod changed in it still runs. The scan stands down (the original
 * call runs) for a level class that overrides getBlockStatesIfLoaded or getBlockStates, and any other use of the
 * returned stream gets the vanilla stream.
 *
 * -Dbons_and_furious.entityBlockScans.shadow=true (verification runs only) also evaluates every scan the vanilla way and
 * counts disagreements (SHADOW_CHECKS / SHADOW_MISMATCHES); the loop's answer is the one returned.
 *
 * Ported to 1.21.1: no change of logic. The 1.21.1 bodies of Entity.isInWall (eye box of width dimensions.width() * 0.8,
 * height 1.0E-6, streamed into anyMatch), the fire check in Entity.move (getBlockStatesIfLoaded(box.deflate(1.0E-6))
 * into noneMatch(fire tag or lava)), BlockPos.betweenClosedStream(AABB) / (six ints) / betweenClosed (six ints),
 * LevelReader.getBlockStatesIfLoaded, LevelReader.hasChunksAt (six ints) and BlockGetter.getBlockStates are the same
 * code as on 1.20.1 (work/src-1.21.1 vs work/src-1.20.1; all guarded), so the loop visits the same positions in the same
 * order and asks the same questions.
 */
public final class BlockScans {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** vanilla_suffocation_scan_loop: -Dbons_and_furious.suffocationScanLoop=false runs isInWall's vanilla stream. */
    public static volatile boolean wallEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.suffocationScanLoop", "true"));
    /** vanilla_fire_scan_loop: -Dbons_and_furious.fireScanLoop=false runs move's vanilla stream. */
    public static volatile boolean fireEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fireScanLoop", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.entityBlockScans.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean wallAnnounced, fireAnnounced;

    /** True when a level class reads its block-state streams through the default LevelReader/BlockGetter code. */
    private static final ClassValue<Boolean> DEFAULT_STATE_STREAMS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                boolean plain = type.getMethod("getBlockStatesIfLoaded", AABB.class).getDeclaringClass() == LevelReader.class
                        && type.getMethod("getBlockStates", AABB.class).getDeclaringClass() == BlockGetter.class;
                if (!plain) {
                    LOGGER.info("Bons and Furious: vanilla_fire_scan_loop leaves the fire check of {} to its own block-state stream", type.getName());
                }
                return plain;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    private BlockScans() {
    }

    /** Entity.isInWall: BlockPos.betweenClosedStream(box). */
    public static Stream<BlockPos> positions(AABB box, Operation<Stream<BlockPos>> original) {
        if (!wallEnabled) {
            return original.call(box);
        }
        if (!wallAnnounced) {
            wallAnnounced = true;
            LOGGER.info("Bons and Furious: vanilla_suffocation_scan_loop applies (the suffocation check scans blocks without a stream){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return new PositionScan(box, original);
    }

    /** Entity.move: level.getBlockStatesIfLoaded(box). */
    public static Stream<BlockState> statesIfLoaded(Level level, AABB box, Operation<Stream<BlockState>> original) {
        if (!fireEnabled || !DEFAULT_STATE_STREAMS.get(level.getClass())) {
            return original.call(level, box);
        }
        if (!fireAnnounced) {
            fireAnnounced = true;
            LOGGER.info("Bons and Furious: vanilla_fire_scan_loop applies (the fire check of moving entities scans blocks without a stream){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return new StateScan(level, box, original);
    }

    private static boolean shadow(boolean ours, boolean vanilla) {
        SHADOW_CHECKS.incrementAndGet();
        if (ours != vanilla && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: entity block scan shadow mismatch (loop {}, stream {})", ours, vanilla);
        }
        return ours;
    }

    /** The positions of BlockPos.betweenClosedStream(box), from vanilla's own iterable. */
    static Iterable<BlockPos> boxPositions(AABB b) {
        return BlockPos.betweenClosed(Mth.floor(b.minX), Mth.floor(b.minY), Mth.floor(b.minZ),
                Mth.floor(b.maxX), Mth.floor(b.maxY), Mth.floor(b.maxZ));
    }

    /** The stream Entity.isInWall would have had, answered by a loop. Public for an offline proof. */
    public static final class PositionScan extends DeferredStream<BlockPos> {
        private final AABB box;
        private final Operation<Stream<BlockPos>> original;

        public PositionScan(AABB box, Operation<Stream<BlockPos>> original) {
            this.box = box;
            this.original = original;
        }

        @Override
        protected Stream<BlockPos> build() {
            return this.original.call(this.box);
        }

        @Override
        protected boolean anyLoop(Predicate<? super BlockPos> predicate) {
            boolean found = false;
            for (BlockPos pos : boxPositions(this.box)) {
                if (predicate.test(pos)) {
                    found = true;
                    break;
                }
            }
            return SHADOW ? shadow(found, this.build().anyMatch(predicate)) : found;
        }

        @Override
        protected boolean allLoop(Predicate<? super BlockPos> predicate) {
            boolean all = true;
            for (BlockPos pos : boxPositions(this.box)) {
                if (!predicate.test(pos)) {
                    all = false;
                    break;
                }
            }
            return SHADOW ? shadow(all, this.build().allMatch(predicate)) : all;
        }
    }

    /** The stream LevelReader.getBlockStatesIfLoaded(box) would have had, answered by a loop. Public for an offline proof. */
    public static final class StateScan extends DeferredStream<BlockState> {
        private final LevelReader level;
        private final AABB box;
        private final Operation<Stream<BlockState>> original;

        /** {@code original} is the wrapped call, invoked as original.call(level, box). */
        public StateScan(LevelReader level, AABB box, Operation<Stream<BlockState>> original) {
            this.level = level;
            this.box = box;
            this.original = original;
        }

        @Override
        protected Stream<BlockState> build() {
            return this.original.call(this.level, this.box);
        }

        /** The positions getBlockStatesIfLoaded would stream, or null for its empty stream (a chunk is not loaded). */
        private Iterable<BlockPos> loadedPositions() {
            AABB b = this.box;
            int x0 = Mth.floor(b.minX), x1 = Mth.floor(b.maxX);
            int y0 = Mth.floor(b.minY), y1 = Mth.floor(b.maxY);
            int z0 = Mth.floor(b.minZ), z1 = Mth.floor(b.maxZ);
            if (!this.level.hasChunksAt(x0, y0, z0, x1, y1, z1)) {
                return null;
            }
            return BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1);
        }

        @Override
        protected boolean anyLoop(Predicate<? super BlockState> predicate) {
            boolean found = false;
            Iterable<BlockPos> positions = this.loadedPositions();
            if (positions != null) {
                for (BlockPos pos : positions) {
                    if (predicate.test(this.level.getBlockState(pos))) {
                        found = true;
                        break;
                    }
                }
            }
            return SHADOW ? shadow(found, this.build().anyMatch(predicate)) : found;
        }

        @Override
        protected boolean allLoop(Predicate<? super BlockState> predicate) {
            boolean all = true;
            Iterable<BlockPos> positions = this.loadedPositions();
            if (positions != null) {
                for (BlockPos pos : positions) {
                    if (!predicate.test(this.level.getBlockState(pos))) {
                        all = false;
                        break;
                    }
                }
            }
            return SHADOW ? shadow(all, this.build().allMatch(predicate)) : all;
        }
    }
}
