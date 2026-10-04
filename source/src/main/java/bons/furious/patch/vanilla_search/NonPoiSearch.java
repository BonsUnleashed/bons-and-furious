package bons.furious.patch.vanilla_search;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.slf4j.Logger;

/**
 * Shared parts of the vanilla_search group (Bons and Furious 1.0.30; Minecraft 1.21.1 with NeoForge 21.1.252; server
 * side, including the integrated server): the visiting orders of the two searches, rebuilt by our own enumerators, and a
 * per-search table of section verdicts. Mojang member names.
 *
 * A section verdict answers "could a position of this chunk section make the search predicate do anything but a pure
 * false?". It is SKIP only when no state of the section's palette satisfies the switch's "interesting" test (a superset
 * of the states the predicate could accept or that could run foreign code). A palette lists every state the section's
 * data can hold, and may list more (entries are never removed), so SKIP is conservative. Empty sections (hasOnlyAir) and
 * positions outside the chunk's section array read as AIR in LevelChunk.getBlockState (vanilla and Radium's inlined
 * copy alike), so they get the AIR verdict.
 *
 * A cached verdict is reused only while the section still has the same palette object with the same size and the same
 * hasOnlyAir flag: palettes only grow by appending (LinearPalette, HashMapPalette, Radium's LithiumHashPalette) or are
 * replaced by a new object (resize), so an unchanged (palette, size) pair has unchanged entries. The table lives for one
 * search call only. Whatever runs between two positions (a blocking getChunk, a predicate call into another mod) can
 * therefore never make the search answer from a stale verdict.
 *
 * Ported to 1.21.1: PalettedContainer.data / PalettedContainer$Data.palette are read by their Mojang names (NeoForge
 * 1.21.1 runs Mojang names in production too); the palettes' maybeHas/idFor/getSize and LevelChunk/LevelChunkSection
 * getBlockState are unchanged. NeoForge 1.21.1 counts LevelChunkSection.nonEmptyBlockCount with BlockState.isEmpty()
 * instead of isAir() (its MC-232360 fix), which only changes WHEN hasOnlyAir is true; LevelChunk.getBlockState (vanilla
 * and Radium 0.13.1's inlined copy) still answers AIR for every position of such a section, which is what the AIR
 * verdict models.
 */
public final class NonPoiSearch {
    static final Logger LOGGER = LogUtils.getLogger();
    static final byte UNKNOWN = 0, SKIP = 1, INTERESTING = 2;
    private static final MethodHandle DATA, PALETTE;
    /** True when the 1.21.1 palette layout was found (PalettedContainer.data, PalettedContainer$Data.palette). */
    static final boolean READY;
    private static final ConcurrentHashMap<Integer, int[]> RINGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, int[]> DIAMONDS = new ConcurrentHashMap<>();

    static {
        MethodHandle d = null, p = null;
        boolean ready = false;
        try {
            Field data = PalettedContainer.class.getDeclaredField("data");
            Class<?> dataClass = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data", false, PalettedContainer.class.getClassLoader());
            Field palette = dataClass.getDeclaredField("palette");
            data.setAccessible(true);
            palette.setAccessible(true);
            d = MethodHandles.lookup().unreflectGetter(data).asType(MethodType.methodType(Object.class, Object.class));
            p = MethodHandles.lookup().unreflectGetter(palette).asType(MethodType.methodType(Object.class, Object.class));
            ready = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: the vanilla_search switches are inactive because the chunk palette classes are not the supported 1.21.1 layout ({})", t.toString());
        }
        DATA = d;
        PALETTE = p;
        READY = ready;
    }

    private NonPoiSearch() {
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
            return n == out.length ? out : java.util.Arrays.copyOf(out, n);
        });
    }

    /**
     * MoveToBlockGoal's layer order: k = start, then k = k > 0 ? -k : 1 - k, while k <= top; the layer's y offset is
     * k - 1. Null when there would be more than 4,096 layers (the caller then runs the original loop).
     */
    static int[] layers(int top, int start) {
        int n = 0;
        int[] out = new int[8];
        for (int k = start; k <= top; k = k > 0 ? -k : 1 - k) {
            if (n == 4096) return null;
            if (n == out.length) out = java.util.Arrays.copyOf(out, n * 2);
            out[n++] = k;
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /**
     * BlockPos.withinManhattan(pos, w, h, d)'s visiting order, as packed (dx, dy, dz) offsets (10 bits each, biased by
     * 512): depth 0 .. w + h + d; x from -min(w, depth) to min(w, depth); y from -m to m with m = min(h, depth - |x|);
     * then z = depth - |x| - |y| when that is at most d, first +z, then -z unless z is 0.
     */
    static int[] diamond(int w, int h, int d) {
        long key = ((long) w << 42) | ((long) h << 21) | d;
        return DIAMONDS.computeIfAbsent(key, k -> {
            int n = 0;
            int[] out = new int[(2 * w + 1) * (2 * h + 1) * (2 * d + 1)];
            for (int depth = 0; depth <= w + h + d; depth++) {
                int maxX = Math.min(w, depth);
                for (int x = -maxX; x <= maxX; x++) {
                    int maxY = Math.min(h, depth - Math.abs(x));
                    for (int y = -maxY; y <= maxY; y++) {
                        int z = depth - Math.abs(x) - Math.abs(y);
                        if (z > d) continue;
                        out[n++] = pack(x, y, z);
                        if (z != 0) out[n++] = pack(x, y, -z);
                    }
                }
            }
            return n == out.length ? out : java.util.Arrays.copyOf(out, n);
        });
    }

    static int pack(int x, int y, int z) {
        return ((x + 512) << 20) | ((y + 512) << 10) | (z + 512);
    }

    static int dx(int p) {
        return (p >>> 20) - 512;
    }

    static int dy(int p) {
        return ((p >>> 10) & 0x3FF) - 512;
    }

    static int dz(int p) {
        return (p & 0x3FF) - 512;
    }

    /**
     * One search's section verdicts, indexed by (column, section) inside the search area. A verdict is recomputed whenever
     * the section's palette object, its size or its hasOnlyAir flag differs from the one it was computed for.
     */
    static final class Verdicts {
        final int cxMin, czMin, nz, secMin, nsec;
        final Object[] palettes;
        final int[] sizes;
        final byte[] verdicts;
        final Predicate<BlockState> interesting;
        final byte air;

        Verdicts(int cxMin, int cxMax, int czMin, int czMax, int secMin, int secMax, Predicate<BlockState> interesting, BlockState airState) {
            this.cxMin = cxMin;
            this.czMin = czMin;
            this.nz = czMax - czMin + 1;
            this.secMin = secMin;
            this.nsec = secMax - secMin + 1;
            int n = (cxMax - cxMin + 1) * this.nz * this.nsec;
            this.palettes = new Object[n];
            this.sizes = new int[n];
            this.verdicts = new byte[n];
            this.interesting = interesting;
            this.air = interesting.test(airState) ? INTERESTING : SKIP;
        }

        /** Index base of a column (chunk coordinates); add the section offset (section index - secMin). */
        int column(int cx, int cz) {
            return ((cx - this.cxMin) * this.nz + (cz - this.czMin)) * this.nsec;
        }

        /**
         * The verdict for section index si of chunk (a LevelChunk exactly; anything else is INTERESTING, i.e. the original
         * per-position code runs). e = column(...) + (si - secMin).
         */
        byte verdict(ChunkAccess chunk, int e, int si) {
            if (chunk.getClass() != LevelChunk.class) return INTERESTING;
            LevelChunkSection[] sections = chunk.getSections();
            if (si < 0 || si >= sections.length) return this.air;
            LevelChunkSection section = sections[si];
            if (section.hasOnlyAir()) return this.air;                       // hasOnlyAir: getBlockState answers AIR
            PalettedContainer<BlockState> states = section.getStates();
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
            int size = pal.getSize();
            if (e >= 0 && e < this.verdicts.length && this.palettes[e] == palette && this.sizes[e] == size && this.verdicts[e] != UNKNOWN) {
                return this.verdicts[e];
            }
            byte v;
            try {
                v = pal.maybeHas(this.interesting) ? INTERESTING : SKIP;     // maybeHas over every palette entry
            } catch (RuntimeException uninitialised) {
                return INTERESTING;
            }
            if (e >= 0 && e < this.verdicts.length) {
                this.palettes[e] = palette;
                this.sizes[e] = size;
                this.verdicts[e] = v;
            }
            return v;
        }
    }
}
