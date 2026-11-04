package bons.furious.patch.alexsmobs_crow_c49;

import com.github.alexthe666.alexsmobs.misc.AMTagRegistry;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch alexsmobs_crow_crop_scan_palette_check (Alex's Mobs 1.22.9, LGPL; both sides, the goal runs on
 * the server). SRG names. No Alex's Mobs code is carried; the visiting order is vanilla MoveToBlockGoal's, rebuilt as data
 * by our own enumerators (the same ones the vanilla_search group uses, kept here so this group stands alone). Ours, idea
 * from Collections Of Optimizations (alexsmobs.crowScanPrecheck), idea text only.
 *
 * What Alex's Mobs does: every wild crow's crop goal (CrowAICircleCrops, a MoveToBlockGoal built with range 32 and vertical
 * range 8) runs vanilla MoveToBlockGoal.findNearestBlock every 200-400 ticks: 17 layers of 63 x 63 positions (67,473)
 * around the crow, and at each one Mob.isWithinRestriction and the goal's isValidTarget, which is
 * level.getBlockState(pos).is(alexsmobs:crow_foodblocks). Level.getBlockState answers VOID_AIR above and below the build
 * height and otherwise asks Level.getChunk(x >> 4, z >> 4) (a loading lookup) and reads the chunk's section. Over open
 * ground nothing is found: 67,473 reads and tag tests to return false.
 *
 * What the switch does: {@link #search} visits the same positions in the same order, asks isWithinRestriction where the
 * original asks it, and makes the same Level.getChunk lookups in the same order, dropping only a lookup that repeats the
 * previous one with nothing in between (vanilla's ServerChunkCache answers it from its last-chunk cache: no ticket, no
 * load, nothing changes; the vanilla_search switches rely on the same fact). It does not read the block of a position
 * whose chunk section cannot hold a crow food block: Level.getBlockState returns an entry of the section's palette, or AIR
 * for a section holding only air, so when no palette entry is in the tag the goal's test is a pure false there. Positions
 * in other sections run the goal's own isValidTarget. A layer outside the build height makes no chunk lookup in the
 * original either; it is skipped when VOID_AIR is not in the tag. The found position is the search's own MutableBlockPos,
 * stored where the original stores it. The tag is evaluated during the search, so datapacks and /reload are respected.
 *
 * Applies only where the result is provably the same (decided once per class): the goal is exactly Alex's Mobs'
 * CrowAICircleCrops (which declares neither findNearestBlock nor anything the search calls besides isValidTarget, whose
 * bytecode the guard pins); the mob's class keeps Mob.isWithinRestriction / hasRestriction, Entity.level / blockPosition;
 * the level's class keeps Level.getBlockState, the getChunk methods and the build-height checks; the level is a server
 * level and not a debug world; the palette layout is the supported 1.20.1 one. If a lookup that had to wait ran other code
 * that moved the crow to another level, the rest of that search runs the original per-position code.
 *
 * Flag: -Dbons_and_furious.alexsmobsCrowCropScanPaletteCheck=false runs the original search. Shadow
 * (-Dbons_and_furious.alexsmobsCrowCropScanPaletteCheck.shadow=true): both searches run, result and position are compared
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, the first 20 logged) and the original's answer is used.
 */
public final class CrowCropSearch {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.alexsmobsCrowCropScanPaletteCheck", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.alexsmobsCrowCropScanPaletteCheck.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Searches answered by the switch (also counted in shadow mode). */
    public static final AtomicLong FAST = new AtomicLong();
    /** search(): run the original instead (nothing was called yet). */
    public static final BlockPos FALLBACK = new BlockPos(0, Integer.MIN_VALUE, 0);
    static final byte UNKNOWN = 0, SKIP = 1, INTERESTING = 2;
    private static final long NO_KEY = Long.MIN_VALUE;
    private static final MethodHandle DATA, PALETTE;
    /** True when the 1.20.1 palette layout was found (PalettedContainer.data, PalettedContainer$Data.palette). */
    static final boolean READY;
    /** Alex's Mobs' CrowAICircleCrops, or null when Alex's Mobs is absent. */
    private static final Class<?> CROW_GOAL;
    private static final ConcurrentHashMap<Integer, int[]> RINGS = new ConcurrentHashMap<>();
    private static volatile boolean announced;

    /** The goal side (CrowCropSearchMixin): the goal's own isValidTarget. */
    public interface Goal {
        boolean bons$alexsmobs_crow_c49Valid(LevelReader level, BlockPos pos);
    }

    static {
        MethodHandle d = null, p = null;
        boolean ready = false;
        try {
            Field data = PalettedContainer.class.getDeclaredField("f_188032_");                         // data
            Class<?> dataClass = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data", false, PalettedContainer.class.getClassLoader());
            Field palette = dataClass.getDeclaredField("f_188102_");                                     // Data.palette
            data.setAccessible(true);
            palette.setAccessible(true);
            d = MethodHandles.lookup().unreflectGetter(data).asType(MethodType.methodType(Object.class, Object.class));
            p = MethodHandles.lookup().unreflectGetter(palette).asType(MethodType.methodType(Object.class, Object.class));
            ready = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: alexsmobs_crow_crop_scan_palette_check is inactive because the chunk palette classes are not the supported 1.20.1 layout ({})", t.toString());
        }
        DATA = d;
        PALETTE = p;
        READY = ready;
        Class<?> goal = null;
        try {
            goal = Class.forName("com.github.alexthe666.alexsmobs.entity.ai.CrowAICircleCrops", false, CrowCropSearch.class.getClassLoader());
        } catch (Throwable absent) {
            // Alex's Mobs is not installed: no goal is ever eligible
        }
        CROW_GOAL = goal;
    }

    private CrowCropSearch() {
    }

    private static final ClassValue<Boolean> MOB_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Mob.class; c = c.getSuperclass()) {
                if (declares(c, "m_21444_", BlockPos.class) || declares(c, "m_21536_")) return false;
            }
            for (Class<?> c = type; c != null && c != Entity.class; c = c.getSuperclass()) {
                if (declaresReturning(c, "m_9236_", Level.class) || declaresReturning(c, "m_20183_", BlockPos.class)) return false;
            }
            return true;
        }
    };

    /**
     * The level's class keeps Level.getBlockState, its chunk lookups and both build-height checks (the search asks the same
     * isOutsideBuildHeight(int) that Level.getBlockState's isOutsideBuildHeight(BlockPos) asks; the height getters behind
     * it may be the level's own, both callers see them alike).
     */
    private static final ClassValue<Boolean> LEVEL_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!Level.class.isAssignableFrom(type)) return false;
            for (Class<?> c = type; c != null && c != Level.class; c = c.getSuperclass()) {
                if (declares(c, "m_8055_", BlockPos.class) || declares(c, "m_6325_", int.class, int.class)
                        || declares(c, "m_46819_", int.class, int.class, ChunkStatus.class)
                        || declares(c, "m_6522_", int.class, int.class, ChunkStatus.class, boolean.class)
                        || declares(c, "m_151570_", BlockPos.class) || declares(c, "m_151562_", int.class)) return false;
            }
            return true;
        }
    };

    /**
     * Whether c itself declares the method (an inspection failure counts as declared: the original runs). A synthetic bridge
     * alone does not count (Mixin merges javac's bridges into game classes; they only forward to the inherited method),
     * unless c also declares a non-bridge method of that name and parameter count (the rule of the vanilla_search group).
     */
    static boolean declares(Class<?> c, String name, Class<?>... params) {
        try {
            if (!c.getDeclaredMethod(name, params).isBridge()) return true;
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isBridge() && m.getName().equals(name) && m.getParameterCount() == params.length) return true;
            }
            return false;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    /** True when c declares a non-bridge, non-static method name() with exactly that return type. */
    static boolean declaresReturning(Class<?> c, String name, Class<?> returns) {
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0 && m.getReturnType() == returns && !m.isBridge()
                        && !Modifier.isStatic(m.getModifiers())) return true;
            }
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    /** True when the switch may answer this goal's search; false means: run the original. */
    public static boolean eligible(Object goal, PathfinderMob mob) {
        return enabled && READY && CROW_GOAL != null && goal.getClass() == CROW_GOAL && mob != null && MOB_OK.get(mob.getClass());
    }

    /**
     * MoveToBlockGoal's horizontal visiting order for one layer, as (dx << 16) | (dz & 0xFFFF): rings l = 0 .. range-1;
     * within a ring the x offset runs 0, 1, -1, 2, -2, ...; an x offset strictly inside the ring takes z = l then -l, the
     * ring's two edge columns take z = 0, 1, -1, ..., l, -l.
     */
    static int[] ring(int range) {
        return RINGS.computeIfAbsent(range, r -> {
            int n = 0;
            int[] out = new int[Math.max(1, (2 * r - 1) * (2 * r - 1))];
            for (int l = 0; l < r; l++) {
                for (int dx = 0; dx <= l; dx = dx > 0 ? -dx : 1 - dx) {
                    for (int dz = (dx < l && dx > -l) ? l : 0; dz <= l; dz = dz > 0 ? -dz : 1 - dz) {
                        out[n++] = (dx << 16) | (dz & 0xFFFF);
                    }
                }
            }
            return n == out.length ? out : Arrays.copyOf(out, n);
        });
    }

    /**
     * MoveToBlockGoal's layer order: k = start, then k = k > 0 ? -k : 1 - k, while k <= top; the layer's y offset is k - 1.
     * Null when there would be more than 4,096 layers (the caller then runs the original loop).
     */
    static int[] layers(int top, int start) {
        int n = 0;
        int[] out = new int[8];
        for (int k = start; k <= top; k = k > 0 ? -k : 1 - k) {
            if (n == 4096) return null;
            if (n == out.length) out = Arrays.copyOf(out, n * 2);
            out[n++] = k;
        }
        return Arrays.copyOf(out, n);
    }

    /**
     * The search. Returns the position the original would have stored in blockPos (the search's own MutableBlockPos), null
     * when it finds nothing, or {@link #FALLBACK} when the original must run instead (decided before anything is called).
     */
    public static BlockPos search(Goal goal, PathfinderMob mob, int range, int verticalRange, int verticalStart) {
        Level level = mob.m_9236_();
        if (level == null || level.m_5776_() || level.m_46659_() || range > 128 || !LEVEL_OK.get(level.getClass())) return FALLBACK;
        int[] layers = layers(verticalRange, verticalStart);
        if (layers == null) return FALLBACK;
        int[] ring = ring(range);
        BlockPos center = mob.m_20183_();
        int cx = center.m_123341_(), cy = center.m_123342_(), cz = center.m_123343_();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (range <= 0 || layers.length == 0) return null;                    // the original's loops run zero times
        TagKey<Block> food = AMTagRegistry.CROW_FOODBLOCKS;
        Predicate<BlockState> isFood = s -> s.m_204336_(food);
        boolean voidFood = Blocks.f_50626_.m_49966_().m_204336_(food);        // what an out-of-height read answers
        Verdicts verdicts = verdicts(level, cx, cy, cz, range - 1, layers, isFood);
        boolean restricted = mob.m_21536_();
        boolean slow = false;                       // set when other code moved the crow to another level
        long lastKey = NO_KEY;
        byte verdict = INTERESTING;
        for (int k : layers) {
            int y = cy + k - 1;
            if (level.m_151562_(y)) {
                // outside the build height: Level.getBlockState answers VOID_AIR and looks no chunk up
                if (!voidFood && !slow) continue;
                for (int packed : ring) {
                    cursor.m_122178_(cx + (packed >> 16), y, cz + (short) packed);
                    if (mob.m_21444_(cursor) && goal.bons$alexsmobs_crow_c49Valid(slow ? mob.m_9236_() : level, cursor)) return cursor;
                }
                continue;
            }
            int si = level.m_151564_(y);
            int sectionOffset = si - verdicts.secMin;
            lastKey = NO_KEY;                       // another section: the next position looks its chunk up again
            for (int packed : ring) {
                int x = cx + (packed >> 16), z = cz + (short) packed;
                if (slow) {
                    // exactly the original's per-position code
                    cursor.m_122178_(x, y, z);
                    if (mob.m_21444_(cursor) && goal.bons$alexsmobs_crow_c49Valid(mob.m_9236_(), cursor)) return cursor;
                    continue;
                }
                if (restricted && !mob.m_21444_(cursor.m_122178_(x, y, z))) continue;
                int chunkX = x >> 4, chunkZ = z >> 4;
                long key = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
                if (key != lastKey) {
                    LevelChunk chunk = level.m_6325_(chunkX, chunkZ);           // the lookup Level.getBlockState makes
                    lastKey = key;
                    verdicts.saw(chunkX, chunkZ, chunk);
                    // the section LevelChunk.getBlockState reads: the chunk's own section index of y
                    verdict = verdicts.verdict(chunk, verdicts.column(chunkX, chunkZ) + sectionOffset, chunk == null ? si : chunk.m_151564_(y));
                    // a lookup that had to wait may have run other code: re-read what the next positions depend on
                    restricted = mob.m_21536_();
                    if (mob.m_9236_() != level) slow = true;
                }
                if (verdict == SKIP) continue;                                    // isValidTarget: a pure false here
                if (goal.bons$alexsmobs_crow_c49Valid(level, cursor.m_122178_(x, y, z))) return cursor;
            }
        }
        return null;
    }

    static Verdicts verdicts(Level level, int cx, int cy, int cz, int reach, int[] layers, Predicate<BlockState> isFood) {
        int minSection = Integer.MAX_VALUE, maxSection = Integer.MIN_VALUE;
        for (int k : layers) {
            int y = cy + k - 1;
            if (level.m_151562_(y)) continue;
            int s = level.m_151564_(y);
            minSection = Math.min(minSection, s);
            maxSection = Math.max(maxSection, s);
        }
        if (minSection > maxSection) minSection = maxSection = 0;               // every layer outside the build height
        return new Verdicts(SectionPos.m_123171_(cx - reach), SectionPos.m_123171_(cx + reach), SectionPos.m_123171_(cz - reach),
                SectionPos.m_123171_(cz + reach), minSection, maxSection, isFood, Blocks.f_50016_.m_49966_());
    }

    /**
     * One search's section verdicts, indexed by (column, section) inside the search area. A verdict is SKIP only when no
     * state of the section's palette is a crow food block (a palette lists every state the section's data can hold, and may
     * list more: SKIP is conservative); a section holding only air reads as AIR. A cached verdict is reused as it is while
     * nothing can have run since it was checked: other code runs only inside a chunk lookup that has to wait for a chunk
     * (it then returns a chunk this search has not seen in that column), so the generation goes up whenever a lookup returns
     * a chunk object not seen before in its column, the first sighting included. After that, a verdict is reused only while
     * the section has the same palette object of the same size (palettes only grow by appending or are replaced by a new
     * object). The table lives for one search only.
     */
    static final class Verdicts {
        final int cxMin, czMin, nz, secMin, nsec;
        final Object[] palettes;
        final Object[] chunks;
        final int[] sizes, gens;
        final byte[] verdicts, answers;
        final Object[] seen;
        final Predicate<BlockState> interesting;
        final byte air;
        int generation = 1;

        Verdicts(int cxMin, int cxMax, int czMin, int czMax, int secMin, int secMax, Predicate<BlockState> interesting, BlockState airState) {
            this.cxMin = cxMin;
            this.czMin = czMin;
            this.nz = czMax - czMin + 1;
            this.secMin = secMin;
            this.nsec = secMax - secMin + 1;
            int columns = (cxMax - cxMin + 1) * this.nz;
            int n = columns * this.nsec;
            this.palettes = new Object[n];
            this.chunks = new Object[n];
            this.sizes = new int[n];
            this.gens = new int[n];
            this.verdicts = new byte[n];
            this.answers = new byte[n];
            this.seen = new Object[columns];
            this.interesting = interesting;
            this.air = interesting.test(airState) ? INTERESTING : SKIP;
        }

        /** Index base of a column (chunk coordinates); add the section offset (section index - secMin). */
        int column(int cx, int cz) {
            return ((cx - this.cxMin) * this.nz + (cz - this.czMin)) * this.nsec;
        }

        /** A chunk lookup of the search returned this chunk for column (cx, cz): a chunk not seen there before ends a generation. */
        void saw(int cx, int cz, Object chunk) {
            int c = (cx - this.cxMin) * this.nz + (cz - this.czMin);
            if (c < 0 || c >= this.seen.length) {
                this.generation++;
                return;
            }
            if (this.seen[c] != chunk) {
                this.seen[c] = chunk;
                this.generation++;
            }
        }

        /** The verdict for section index si of chunk (a LevelChunk exactly; anything else is INTERESTING). */
        byte verdict(ChunkAccess chunk, int e, int si) {
            boolean inTable = e >= 0 && e < this.verdicts.length;
            if (inTable && this.gens[e] == this.generation && this.chunks[e] == chunk) return this.answers[e];   // nothing ran since
            byte v = check(chunk, e, si, inTable);
            if (inTable) {
                this.gens[e] = this.generation;
                this.chunks[e] = chunk;
                this.answers[e] = v;
            }
            return v;
        }

        /** The verdict read from the section itself (its palette object and size remembered, so an unchanged one is not walked again). */
        private byte check(ChunkAccess chunk, int e, int si, boolean inTable) {
            if (chunk == null || chunk.getClass() != LevelChunk.class) return INTERESTING;
            LevelChunkSection[] sections = chunk.m_7103_();
            if (si < 0 || si >= sections.length) return this.air;
            LevelChunkSection section = sections[si];
            if (section == null) return INTERESTING;
            if (section.m_188008_()) return this.air;                       // hasOnlyAir: getBlockState answers AIR
            PalettedContainer<BlockState> states = section.m_63019_();
            if (states.getClass() != PalettedContainer.class) return INTERESTING;
            Object palette;
            try {
                Object data = (Object) DATA.invokeExact((Object) states);
                palette = data == null ? null : (Object) PALETTE.invokeExact(data);
            } catch (Throwable t) {
                return INTERESTING;
            }
            if (!(palette instanceof Palette<?> p)) return INTERESTING;
            @SuppressWarnings("unchecked")
            Palette<BlockState> pal = (Palette<BlockState>) p;
            int size = pal.m_62680_();
            if (inTable && this.palettes[e] == palette && this.sizes[e] == size && this.verdicts[e] != UNKNOWN) {
                return this.verdicts[e];
            }
            byte v;
            try {
                v = pal.m_6419_(this.interesting) ? INTERESTING : SKIP;     // maybeHas over every palette entry
            } catch (RuntimeException uninitialised) {
                return INTERESTING;
            }
            if (inTable) {
                this.palettes[e] = palette;
                this.sizes[e] = size;
                this.verdicts[e] = v;
            }
            return v;
        }
    }

    public static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: alexsmobs_crow_crop_scan_palette_check applies (crows' crop searches skip block reads in chunk sections that hold no crow food){}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }

    /** Shadow mode: the switch's answer against the original's. */
    public static void shadow(BlockPos ours, boolean originalFound, BlockPos originalPos) {
        SHADOW_CHECKS.incrementAndGet();
        boolean same = ours == null ? !originalFound : originalFound && originalPos != null && ours.equals(originalPos)
                && ours.getClass() == originalPos.getClass();
        if (same) return;
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) {
            LOGGER.warn("Bons and Furious: alexsmobs_crow_crop_scan_palette_check shadow mismatch #{}: the switch found {} where the original found {}",
                    m, ours, originalFound ? originalPos : "nothing");
        }
    }
}
