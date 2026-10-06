package bons.furious.patch.vanilla_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
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
 * Bons and Furious switches vanilla_suffocation_scan_loop and vanilla_fire_scan_loop (Minecraft 1.20.1, both sides).
 * SRG member names.
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
 * returned stream gets the vanilla stream. 1.0.34: the fire check also stands down while Radium applies its experimental
 * fire and lava cache to the same call (see RadiumFireLava).
 *
 * -Dbons_and_furious.entityBlockScans.shadow=true (verification runs only) also evaluates every scan the vanilla way and
 * counts disagreements (SHADOW_CHECKS / SHADOW_MISMATCHES); the loop's answer is the one returned.
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
                boolean plain = type.getMethod("m_46847_", AABB.class).getDeclaringClass() == LevelReader.class
                        && type.getMethod("m_45556_", AABB.class).getDeclaringClass() == BlockGetter.class;
                if (!plain) {
                    LOGGER.info("Bons and Furious: vanilla_fire_scan_loop leaves the fire check of {} to its own block-state stream", type.getName());
                }
                return plain;
            } catch (Throwable t) {
                // 1.0.34: not only NoSuchMethodException. getMethod resolves the types of every public method it walks (the
                // level class, its superclasses and interfaces, with what other mods' mixins add to them); one naming a class
                // that is missing on this side, e.g. a client-only class on a dedicated server, throws NoClassDefFoundError:
                // that level keeps its original stream
                return false;
            }
        }
    };

    /**
     * 1.0.34: whether Radium applies its experimental fire and lava cache to Entity.move
     * (experimental.entity.block_caching.fire_lava_touching.EntityMixin). That mixin redirects the same
     * getBlockStatesIfLoaded call (its null means "nothing to check") and the noneMatch after it ("stream == null"). The
     * switch's wrapper would run instead of Radium's redirect and hand noneMatch a stream that is never null, so the "not
     * touching fire or lava" branch would never run. While Radium applies that mixin the fire check steps aside: the wrapped
     * call, Radium's redirect, runs as without the switch. Decided once, on the first fire check, from Radium's live options
     * as Radium's own mixin plugin decided them (final once Mixin has prepared Radium's config, long before an entity moves).
     */
    private static final class RadiumFireLava {
        static final boolean APPLIED = radiumFireLavaApplied();
    }

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
        if (!fireEnabled || RadiumFireLava.APPLIED || !DEFAULT_STATE_STREAMS.get(level.getClass())) {   // 1.0.34: RadiumFireLava
            return original.call(level, box);
        }
        if (!fireAnnounced) {
            fireAnnounced = true;
            LOGGER.info("Bons and Furious: vanilla_fire_scan_loop applies (the fire check of moving entities scans blocks without a stream){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return new StateScan(level, box, original);
    }

    /**
     * 1.0.34: RadiumFireLava.APPLIED, read the way Bons' Radium compat reads Radium's options (LithiumMod.CONFIG, its
     * CaffeineConfig, getEffectiveOptionForMixin(...).isEnabled(), the test Radium's mixin plugin applies). Radium not
     * installed: false (the switch works as before). Radium installed but its option unreadable: true (steps aside).
     */
    private static boolean radiumFireLavaApplied() {
        String mixin = "experimental.entity.block_caching.fire_lava_touching.EntityMixin";
        try {
            ClassLoader loader = BlockScans.class.getClassLoader();
            Class<?> lithium;
            try {
                lithium = Class.forName("me.jellysquid.mods.lithium.common.LithiumMod", true, loader);
            } catch (ClassNotFoundException e) {
                return false;
            }
            Object plugin = lithium.getField("CONFIG").get(null);
            Field f = Class.forName("net.caffeinemc.caffeineconfig.AbstractCaffeineConfigMixinPlugin", false, loader).getDeclaredField("config");
            f.setAccessible(true);
            Object config = f.get(plugin);
            Object option = config.getClass().getMethod("getEffectiveOptionForMixin", String.class).invoke(config, mixin);
            if (option == null || !(Boolean) option.getClass().getMethod("isEnabled").invoke(option)) return false;
            LOGGER.info("Bons and Furious: vanilla_fire_scan_loop steps aside: Radium applies its experimental fire and lava cache ({}) "
                    + "to the same call; the fire check of moving entities is left to Radium", mixin);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_fire_scan_loop steps aside: Radium is installed but its option for {} could not be read ({}); "
                    + "the fire check of moving entities is left as Radium makes it", mixin, t.toString());
            return true;
        }
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
        return BlockPos.m_121976_(Mth.m_14107_(b.f_82288_), Mth.m_14107_(b.f_82289_), Mth.m_14107_(b.f_82290_),
                Mth.m_14107_(b.f_82291_), Mth.m_14107_(b.f_82292_), Mth.m_14107_(b.f_82293_));
    }

    /** The stream Entity.isInWall would have had, answered by a loop. Public for the offline proof. */
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

    /** The stream LevelReader.getBlockStatesIfLoaded(box) would have had, answered by a loop. Public for the offline proof. */
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
            int x0 = Mth.m_14107_(b.f_82288_), x1 = Mth.m_14107_(b.f_82291_);
            int y0 = Mth.m_14107_(b.f_82289_), y1 = Mth.m_14107_(b.f_82292_);
            int z0 = Mth.m_14107_(b.f_82290_), z1 = Mth.m_14107_(b.f_82293_);
            if (!this.level.m_46812_(x0, y0, z0, x1, y1, z1)) {
                return null;
            }
            return BlockPos.m_121976_(x0, y0, z0, x1, y1, z1);
        }

        @Override
        protected boolean anyLoop(Predicate<? super BlockState> predicate) {
            boolean found = false;
            Iterable<BlockPos> positions = this.loadedPositions();
            if (positions != null) {
                for (BlockPos pos : positions) {
                    if (predicate.test(this.level.m_8055_(pos))) {
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
                    if (!predicate.test(this.level.m_8055_(pos))) {
                        all = false;
                        break;
                    }
                }
            }
            return SHADOW ? shadow(all, this.build().allMatch(predicate)) : all;
        }
    }
}
