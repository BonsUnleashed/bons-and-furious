package bons.furious.patch.vanilla_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch vanilla_fire_scan_idle_skip (since 1.0.45; Minecraft 1.20.1 on Forge 47.4.16; applied on both sides,
 * acts on the logical server: a dedicated server or the integrated server of single player). SRG names.
 *
 * What Minecraft does. At the end of Entity.move (m_6478_), for every moving entity on every move, wasOnFire (f_146810_) was
 * set to isOnFire() at the start of the move, and then
 *   if (level.getBlockStatesIfLoaded(box.deflate(1e-6)).noneMatch(fire tag or lava)) {
 *       if (remainingFireTicks <= 0) setRemainingFireTicks(-getFireImmuneTicks());
 *       if (wasOnFire && (powder snow || water, rain, bubbles || an extinguishing fluid)) playEntityOnFireExtinguishedSound();
 *   }
 * The block scan's answer only decides whether that first block runs. For an entity that was not burning (so the sound
 * branch is dead) and whose remainingFireTicks already equals -getFireImmuneTicks() (the reset value), the setter call
 * would write the value the field already holds.
 *
 * What the switch does. In exactly that case (wasOnFire false; remainingFireTicks == -gfit, where the entity's class takes
 * setRemainingFireTicks and getFireImmuneTicks from Entity (gfit 1) or Player (gfit 20) - no other class of the 556 client
 * jars declares them - so gfit needs no call), the wrapped call returns a stream whose noneMatch answers true without
 * reading blocks or calling the predicate. It still asks hasChunksAt over the same corners and, when loaded, makes the chunk
 * lookups the block reads would have made: Level.getChunk(x >> 4, z >> 4) for every position in BlockPos.betweenClosedStream
 * order (x fastest, then y, then z), dropping a lookup that repeats the one just made (it can only hit the chunk cache).
 * That is what Level.getBlockState does first for every position, vanilla's inside the build height and Radium's inlined copy
 * always; so the decision applies only when the whole box lies inside the build height. The vanilla code after the stream
 * then runs unchanged (so the setter and getter are called exactly as without the switch in the common no-fire case, and Ars
 * Nouveau's hook on setRemainingFireTicks sees the same calls). Any other use of the stream builds the original one.
 *
 * Exact variant, one documented difference: when fire or lava does touch the box of such an idle entity (a fire-immune mob in
 * lava or fire - its timer never changes -, an entity that moved into lava this tick, a modded fire-tag block that sets no
 * fire), the switch acts as if nothing touched it: one same-value setRemainingFireTicks(-gfit) / getFireImmuneTicks() pair
 * that the original skips, and the box's chunk lookups continue past the first fire or lava block (lookups of loaded chunks;
 * hasChunksAt was true). No value changes: the field already holds -gfit, and every other effect of the move is unchanged.
 *
 * Stands down (the original call runs) when: the switch is off; Radium applies its experimental fire and lava cache to the
 * same call (BlockScans); the level's class overrides getBlockState, getChunk(int, int), getChunkAt, the block-state stream
 * methods or hasChunksAt; the entity's class or one of its superclasses other than Entity and Player declares
 * setRemainingFireTicks or getFireImmuneTicks; the box reaches outside the build height. Each class or level reason logs ONE
 * INFO line naming the class and what it declares (with the mixin that merged it), so an idle switch shows in the log.
 *
 * Synthetic bridge methods do not count as declarations (1.0.45 rehearsal): six mods of this pack (Corgilib, Friends and
 * Foes, Moonlight, Oh The Trees You'll Grow, Supplementaries, The Bumblezone) mix into ServerLevel from a class that extends
 * Level, and javac gave each mixin class a synthetic bridge "ChunkAccess getChunk(int, int)" whose whole body is
 * "return super.getChunk(x, z)" (invokespecial Level.getChunk(II)LevelChunk; javap-checked); Mixin merges it into ServerLevel.
 * getDeclaredMethod("m_6325_", int, int) then found it and the switch stood down on every server level. A bridge only
 * forwards to the method it bridges, and the lookups this switch replays and the block reads it skips call the LevelChunk
 * method itself, so getBlockState and getChunk behave as without the bridge. getDeclaredMethod returns the declaration with
 * the most specific return type, so a real override declared next to a bridge is still seen and still counts. A bridge next
 * to a non-bridge method of the same name and parameter count (a generic override it forwards to) counts too.
 *
 * On a client level the switch practically never acts: Entity.baseTick clears the fire timer every tick there
 * (isClientSide: clearFire()), so an entity reaches this check with timer 0, not -gfit; only an entity moved a second time in
 * the same tick (pushed by a piston) can be decided there. The cheap "timer >= 0" test comes first for that reason (it also
 * covers entities in water, which clears the timer too).
 *
 * -Dbons_and_furious.fireScanIdleSkip=false runs the original check. -Dbons_and_furious.fireScanIdleSkip.shadow=true
 * (verification runs only) also evaluates the original stream for every decided call and counts the calls where fire or lava
 * did touch the box (SHADOW_TOUCHING, the documented case), and checks that getFireImmuneTicks() returns the assumed gfit and
 * the timer still holds -gfit (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20 mismatches).
 */
public final class FireIdleScan {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** vanilla_fire_scan_idle_skip: -Dbons_and_furious.fireScanIdleSkip=false runs the original fire check. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fireScanIdleSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.fireScanIdleSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_TOUCHING = new AtomicLong();
    private static final long NO_KEY = Long.MIN_VALUE;
    private static volatile boolean announced;

    private FireIdleScan() {
    }

    /** gfit of the class: 1 (Entity's methods) or 20 (Player's); 0 when another class declares one of the two methods. */
    static final ClassValue<Integer> GFIT = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            if (!Entity.class.isAssignableFrom(type)) return 0;
            int gfit = 1;
            for (Class<?> c = type; c != Entity.class; c = c.getSuperclass()) {
                if (c == Player.class) {
                    gfit = 20;
                    continue;
                }
                String which = declares(c, "m_7311_", int.class) ? describe(c, "m_7311_", "setRemainingFireTicks(int)", int.class)
                        : declares(c, "m_6101_") ? describe(c, "m_6101_", "getFireImmuneTicks()") : null;
                if (which != null) {
                    standDown("entities of class " + type.getName(), "the fire timer methods are not only Entity's or Player's: " + which);
                    return 0;
                }
            }
            return gfit;
        }
    };

    /** True when the level reads blocks, chunks and block-state streams through Level / LevelReader / BlockGetter's own code. */
    static final ClassValue<Boolean> LEVEL_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            String why = levelReason(type);
            if (why != null) standDown("level class " + type.getName(), why);
            return why == null;
        }
    };

    /** Why the switch cannot replay the lookups of this level class's block reads, or null when it can. */
    static String levelReason(Class<?> type) {
        try {
            for (Class<?> c = type; c != null && c != Level.class; c = c.getSuperclass()) {
                if (declares(c, "m_8055_", BlockPos.class)) return "it overrides " + describe(c, "m_8055_", "getBlockState(BlockPos)", BlockPos.class);
                if (declares(c, "m_6325_", int.class, int.class)) return "it overrides " + describe(c, "m_6325_", "getChunk(int, int)", int.class, int.class);
                if (declares(c, "m_46745_", BlockPos.class)) return "it overrides " + describe(c, "m_46745_", "getChunkAt(BlockPos)", BlockPos.class);
            }
            Class<?> from = type.getMethod("m_46847_", AABB.class).getDeclaringClass();
            if (from != LevelReader.class) return "getBlockStatesIfLoaded(AABB) comes from " + from.getName();
            from = type.getMethod("m_45556_", AABB.class).getDeclaringClass();
            if (from != BlockGetter.class) return "getBlockStates(AABB) comes from " + from.getName();
            from = type.getMethod("m_46812_", int.class, int.class, int.class, int.class, int.class, int.class).getDeclaringClass();
            if (from != LevelReader.class) return "hasChunksAt(int x6) comes from " + from.getName();
            return null;
        } catch (Throwable t) {
            return "its methods could not be inspected (" + t + ")";     // as BlockScans: the original runs
        }
    }

    /**
     * Whether c itself declares the method; an inspection failure counts as declared (the original then runs). A synthetic
     * bridge does not count unless c also declares a non-bridge method of that name and parameter count (see the class comment).
     * A bridge alone only forwards to the inherited method. Next to such a method it forwards to that one, a covariant or
     * generic override. getDeclaredMethod picks a real override with exactly these parameters over a bridge.
     */
    static boolean declares(Class<?> c, String name, Class<?>... params) {
        try {
            if (!c.getDeclaredMethod(name, params).isBridge()) return true;
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (!m.isBridge() && m.getName().equals(name) && m.getParameterCount() == params.length) return true;
            }
            return false;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    /** For a stand-down line: "Class.label", plus the mixin that merged the declaration when it carries @MixinMerged. */
    static String describe(Class<?> c, String name, String label, Class<?>... params) {
        String s = c.getName() + "." + label + " [" + name + "]";
        try {
            MixinMerged merged = c.getDeclaredMethod(name, params).getAnnotation(MixinMerged.class);
            if (merged != null) s += ", merged by the mixin " + merged.mixin();
        } catch (Throwable ignored) {
        }
        return s;
    }

    private static final AtomicInteger STAND_DOWN_LINES = new AtomicInteger();

    /** One INFO line per class that keeps the switch off (at most 16; ClassValue computes each class once). */
    static void standDown(String what, String why) {
        int n = STAND_DOWN_LINES.incrementAndGet();
        if (n <= 16) LOGGER.info("Bons and Furious: vanilla_fire_scan_idle_skip steps aside for {}: {}{}", what, why,
                n == 16 ? " (further classes are not logged)" : "");
    }

    private static volatile boolean radiumAnnounced;

    /** BlockScans' Radium decision (Radium's experimental fire and lava cache on the same call), with one INFO line. */
    private static boolean radiumStepsAside() {
        if (!BlockScans.radiumCacheApplied()) return false;
        if (!radiumAnnounced) {
            radiumAnnounced = true;
            LOGGER.info("Bons and Furious: vanilla_fire_scan_idle_skip steps aside: Radium applies its experimental fire and lava cache to the same call");
        }
        return true;
    }

    /** Entity.move: level.getBlockStatesIfLoaded(box), wrapped; wasOnFire and rft are the entity's fields at the call. */
    public static Stream<BlockState> scan(Entity entity, boolean wasOnFire, int rft, Level level, AABB box, Operation<Stream<BlockState>> original) {
        // rft >= 0 is never the decided value -gfit (gfit 1 or 20); a client-side entity always arrives with 0 (class comment)
        if (!enabled || wasOnFire || rft >= 0) return original.call(level, box);
        int gfit = GFIT.get(entity.getClass());
        if (gfit == 0 || rft != -gfit || !LEVEL_OK.get(level.getClass()) || radiumStepsAside()) return original.call(level, box);
        int x0 = Mth.m_14107_(box.f_82288_), y0 = Mth.m_14107_(box.f_82289_), z0 = Mth.m_14107_(box.f_82290_);
        int x1 = Mth.m_14107_(box.f_82291_), y1 = Mth.m_14107_(box.f_82292_), z1 = Mth.m_14107_(box.f_82293_);
        if (y0 < level.m_141937_() || y1 >= level.m_151558_()) return original.call(level, box);
        if (!announced) announce();
        return new IdleStream(entity, gfit, level, box, original, x0, y0, z0, x1, y1, z1);
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: vanilla_fire_scan_idle_skip applies (the fire check of moving entities that are not burning skips its block reads){}",
                SHADOW ? " - shadow verification on" : "");
    }

    /**
     * The stream getBlockStatesIfLoaded(box) would have had, for an idle entity: noneMatch answers true after the same
     * hasChunksAt and chunk lookups; anyMatch and allMatch answer from the original stream; any other use builds it.
     * Public for the offline proof.
     */
    public static final class IdleStream extends DeferredStream<BlockState> {
        private final Entity entity;
        private final int gfit;
        private final Level level;
        private final AABB box;
        private final Operation<Stream<BlockState>> original;
        private final int x0, y0, z0, x1, y1, z1;
        private boolean none;

        public IdleStream(Entity entity, int gfit, Level level, AABB box, Operation<Stream<BlockState>> original,
                          int x0, int y0, int z0, int x1, int y1, int z1) {
            this.entity = entity;
            this.gfit = gfit;
            this.level = level;
            this.box = box;
            this.original = original;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }

        @Override
        protected Stream<BlockState> build() {
            return this.original.call(this.level, this.box);
        }

        @Override
        public boolean noneMatch(Predicate<? super BlockState> predicate) {
            this.none = true;
            try {
                return super.noneMatch(predicate);
            } finally {
                this.none = false;
            }
        }

        @Override
        protected boolean anyLoop(Predicate<? super BlockState> predicate) {
            if (!this.none) return this.original.call(this.level, this.box).anyMatch(predicate);
            Level l = this.level;
            if (l.m_46812_(this.x0, this.y0, this.z0, this.x1, this.y1, this.z1)) {        // hasChunksAt, as getBlockStatesIfLoaded
                long last = NO_KEY;
                for (int z = this.z0; z <= this.z1; z++) {
                    int cz = z >> 4;
                    for (int y = this.y0; y <= this.y1; y++) {
                        for (int x = this.x0; x <= this.x1; x++) {
                            int cx = x >> 4;
                            long key = (long) cx & 0xFFFFFFFFL | ((long) cz & 0xFFFFFFFFL) << 32;
                            if (key != last) {
                                l.m_6325_(cx, cz);                                      // the lookup getBlockState makes first
                                last = key;
                            }
                        }
                    }
                }
            }
            if (SHADOW) shadow(predicate);
            return false;                                                              // noneMatch: true
        }

        @Override
        protected boolean allLoop(Predicate<? super BlockState> predicate) {
            return this.original.call(this.level, this.box).allMatch(predicate);
        }

        private void shadow(Predicate<? super BlockState> predicate) {
            SHADOW_CHECKS.incrementAndGet();
            if (!this.original.call(this.level, this.box).noneMatch(predicate)) SHADOW_TOUCHING.incrementAndGet();
            String problem = null;
            try {
                int real = (int) GET_FIRE_IMMUNE_TICKS.invokeExact(this.entity);
                if (real != this.gfit) problem = "getFireImmuneTicks() returned " + real + ", assumed " + this.gfit;
                else if (this.entity.m_20094_() != -this.gfit) problem = "remainingFireTicks changed to " + this.entity.m_20094_();
            } catch (Throwable t) {
                problem = "getFireImmuneTicks() could not be called: " + t;
            }
            if (problem != null) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_fire_scan_idle_skip shadow mismatch #{} for {}: {}", m, this.entity.getClass().getName(), problem);
            }
        }
    }

    /** Entity.getFireImmuneTicks() (protected), for the shadow check only. */
    private static final MethodHandle GET_FIRE_IMMUNE_TICKS;

    static {
        MethodHandle h = null;
        try {
            h = MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup())
                    .findVirtual(Entity.class, "m_6101_", MethodType.methodType(int.class)).asType(MethodType.methodType(int.class, Entity.class));
        } catch (Throwable t) {
            LOGGER.debug("Bons and Furious: vanilla_fire_scan_idle_skip shadow check unavailable ({})", t.toString());
        }
        GET_FIRE_IMMUNE_TICKS = h;
    }
}
