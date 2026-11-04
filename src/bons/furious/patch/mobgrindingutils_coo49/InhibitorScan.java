package bons.furious.patch.mobgrindingutils_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import mob_grinding_utils.blocks.BlockEnderInhibitorOff;
import mob_grinding_utils.blocks.BlockEnderInhibitorOn;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mobgrindingutils_inhibitor_scan_sections (since 1.0.49; Mob Grinding Utils 1.20.1-1.1.0; server
 * side). Helper of InhibitorScanMixin; no Mob Grinding Utils or Minecraft code is carried. SRG names.
 *
 * What the mod does. MGUEndermanInhibitEvent listens to every EntityTeleportEvent on the server (endermen dodging,
 * chorus fruit, shulkers; a failed teleport attempt fires it again, up to 64 times in one tick) and answers
 * getIsInhibited(entity): the entity's box grown by 8 in every direction, x from floor(minX) to floor(maxX) - 1 (outer),
 * then y, then z (inner), entity.getCommandSenderWorld().getBlockState(pos) at every position (about 5,200 for an
 * enderman), true at the first block that is a BlockEnderInhibitorOn and not a BlockEnderInhibitorOff.
 *
 * What the switch does. The same positions in the same order, with the same chunk lookups: Level.getBlockState starts with
 * Level.getChunk(x >> 4, z >> 4) for every position inside the build height; a lookup that repeats the one just made
 * with nothing in between can only return the same chunk from the chunk cache and is dropped, every other lookup is made
 * at the same point of the walk (so chunk loads, tickets and the cache behave as before). A z-run that stays in one chunk
 * section is read only when the section's block palette could hold an active inhibitor (Palette.maybeHas over every entry:
 * a palette lists every state the section can hold, and may list more, so "no" is certain); otherwise none of its
 * positions can answer true and the run is skipped. Positions that are read are read from the looked-up chunk exactly as
 * Level.getBlockState reads them (LevelChunk.getBlockState). The answer, the first true position and every chunk access
 * are therefore those of the original. Section verdicts live for one call only and are recomputed whenever the section's
 * palette object or size changed (palettes only grow, or are replaced by a new object).
 *
 * Runs the original (unchanged) when: the switch or its runtime flag is off; the 1.20.1 palette layout is not found; the
 * box reaches outside the build height (the original reads void air there without a lookup); the level is a debug world;
 * the level's class (other than Level) overrides getBlockState, getChunk(int, int) or isOutsideBuildHeight(BlockPos); the
 * entity's class overrides getCommandSenderWorld() or level() (the original asks it at every position; Entity's own is a
 * plain field read, asked once here). Chunks of another class than LevelChunk, sections whose container is a subclass of
 * PalettedContainer and unreadable palettes are read position by position.
 *
 * -Dbons_and_furious.mobgrindingutilsInhibitorScanSections=false runs the original. SHADOW MODE (verification only):
 * -Dbons_and_furious.mobgrindingutilsInhibitorScanSections.shadow=true runs both and answers the original's result;
 * SHADOW_CHECKS / SHADOW_MISMATCHES, the first 20 mismatches are logged as WARN.
 */
public final class InhibitorScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mobgrindingutilsInhibitorScanSections", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mobgrindingutilsInhibitorScanSections.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields): calls answered by the section walk, runs skipped. */
    public static long answered, skippedRuns;
    static final byte UNKNOWN = 0, SKIP = 1, READ = 2;
    private static final long NO_KEY = Long.MIN_VALUE;
    private static final MethodHandle DATA, PALETTE;
    static final boolean READY;
    private static volatile boolean announced;
    private static final AtomicInteger STAND_DOWN_LINES = new AtomicInteger();

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
            LOGGER.warn("Bons and Furious: mobgrindingutils_inhibitor_scan_sections is inactive because the chunk palette classes are not the supported 1.20.1 layout ({})", t.toString());
        }
        DATA = d;
        PALETTE = p;
        READY = ready;
    }

    private InhibitorScan() {
    }

    /** The original's test for one block state: an active Ender Inhibitor. */
    static boolean active(BlockState state) {
        Block b = state.m_60734_();
        return b instanceof BlockEnderInhibitorOn && !(b instanceof BlockEnderInhibitorOff);
    }

    /** Whether c itself declares the method (a javac bridge alone does not count); an inspection failure counts as declared. */
    static boolean declares(Class<?> c, String name, Class<?>... params) {
        try {
            if (!c.getDeclaredMethod(name, params).isBridge()) return true;
            for (java.lang.reflect.Method m : c.getDeclaredMethods())
                if (!m.isBridge() && m.getName().equals(name) && m.getParameterCount() == params.length) return true;
            return false;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    private static void standDown(String what, String why) {
        if (STAND_DOWN_LINES.incrementAndGet() <= 8)
            LOGGER.info("Bons and Furious: mobgrindingutils_inhibitor_scan_sections steps aside for {}: {}", what, why);
    }

    /** Level classes whose block reads are Level's own (no override of the methods the walk replays). */
    static final ClassValue<Boolean> LEVEL_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Level.class; c = c.getSuperclass()) {
                String which = declares(c, "m_8055_", BlockPos.class) ? "getBlockState(BlockPos) [m_8055_]"
                        : declares(c, "m_6325_", int.class, int.class) ? "getChunk(int, int) [m_6325_]"
                        : declares(c, "m_151570_", BlockPos.class) ? "isOutsideBuildHeight(BlockPos) [m_151570_]" : null;
                if (which != null) {
                    standDown("level class " + type.getName(), c.getName() + " overrides " + which);
                    return false;
                }
            }
            return true;
        }
    };

    /** Entity classes whose getCommandSenderWorld() is Entity's plain read of its level. */
    static final ClassValue<Boolean> ENTITY_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Entity.class; c = c.getSuperclass()) {
                String which = declares(c, "m_20193_") ? "getCommandSenderWorld() [m_20193_]" : declares(c, "m_9236_") ? "level() [m_9236_]" : null;
                if (which != null) {
                    standDown("entity class " + type.getName(), c.getName() + " overrides " + which);
                    return false;
                }
            }
            return true;
        }
    };

    /** MGUEndermanInhibitEvent.getIsInhibited(entity), wrapped. */
    public static boolean isInhibited(LivingEntity entity, Operation<Boolean> original) {
        if (!enabled || !READY || entity == null || !ENTITY_OK.get(entity.getClass())) return original.call(entity);
        AABB box = entity.m_20191_().m_82377_(8.0, 8.0, 8.0);
        int x0 = Mth.m_14107_(box.f_82288_), x1 = Mth.m_14107_(box.f_82291_);
        int y0 = Mth.m_14107_(box.f_82289_), y1 = Mth.m_14107_(box.f_82292_);
        int z0 = Mth.m_14107_(box.f_82290_), z1 = Mth.m_14107_(box.f_82293_);
        if (x0 >= x1 || y0 >= y1 || z0 >= z1) return false;                // the original's loops read nothing: false
        Level level = entity.m_20193_();
        if (level == null || level.m_46659_() || !LEVEL_OK.get(level.getClass())) return original.call(entity);
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        if (level.m_151570_(probe.m_122178_(x0, y0, z0)) || level.m_151570_(probe.m_122178_(x0, y1 - 1, z0))) return original.call(entity);
        if (SHADOW) {
            boolean theirs = original.call(entity);
            boolean ours = scan(level, x0, x1, y0, y1, z0, z1);
            SHADOW_CHECKS.incrementAndGet();
            if (ours != theirs) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: mobgrindingutils_inhibitor_scan_sections shadow mismatch #{} at {} ({}): the section walk says {}, the original {}",
                        m, entity.m_20183_(), entity.getClass().getName(), ours, theirs);
            }
            return theirs;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: mobgrindingutils_inhibitor_scan_sections applies (the Ender Inhibitor check of teleports skips chunk sections that hold no inhibitor)");
        }
        answered++;
        return scan(level, x0, x1, y0, y1, z0, z1);
    }

    /** The walk; x in [x0, x1), y in [y0, y1), z in [z0, z1), as the original's loops. Public for the offline proof. */
    public static boolean scan(Level level, int x0, int x1, int y0, int y1, int z0, int z1) {
        int minSection = level.m_151560_();                              // getSectionIndex(y) = (y >> 4) - minSection
        Verdicts v = new Verdicts(x0 >> 4, (x1 - 1) >> 4, z0 >> 4, (z1 - 1) >> 4, (y0 >> 4) - minSection, ((y1 - 1) >> 4) - minSection);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long lastKey = NO_KEY;
        LevelChunk chunk = null;
        for (int x = x0; x < x1; x++) {
            int cx = x >> 4;
            for (int y = y0; y < y1; y++) {
                int si = (y >> 4) - minSection;
                int z = z0;
                while (z < z1) {
                    int cz = z >> 4;
                    long key = (long) cx & 0xFFFFFFFFL | ((long) cz & 0xFFFFFFFFL) << 32;
                    if (key != lastKey) {
                        chunk = level.m_6325_(cx, cz);                     // the lookup the original's getBlockState makes here
                        lastKey = key;
                    }
                    int zEnd = Math.min(z1, (cz + 1) << 4);               // the rest of this z-run stays in this chunk and section
                    if (v.verdict(chunk, cx, cz, si) == SKIP) {
                        skippedRuns++;
                        z = zEnd;
                        continue;
                    }
                    for (; z < zEnd; z++) {
                        if (active(chunk.m_8055_(cursor.m_122178_(x, y, z)))) return true;
                    }
                }
            }
        }
        return false;
    }

    /** One call's section verdicts, indexed by (chunk column, section) inside the box. */
    static final class Verdicts {
        final int cxMin, czMin, nz, secMin, nsec;
        final Object[] palettes;
        final int[] sizes;
        final byte[] verdicts;

        Verdicts(int cxMin, int cxMax, int czMin, int czMax, int secMin, int secMax) {
            this.cxMin = cxMin;
            this.czMin = czMin;
            this.nz = czMax - czMin + 1;
            this.secMin = secMin;
            this.nsec = secMax - secMin + 1;
            int n = (cxMax - cxMin + 1) * this.nz * this.nsec;
            this.palettes = new Object[n];
            this.sizes = new int[n];
            this.verdicts = new byte[n];
        }

        /** SKIP only when no position of section si of the chunk can be an active inhibitor; READ otherwise. */
        byte verdict(LevelChunk chunk, int cx, int cz, int si) {
            if (chunk == null || chunk.getClass() != LevelChunk.class) return READ;
            LevelChunkSection[] sections = chunk.m_7103_();
            if (si < 0 || si >= sections.length) return SKIP;               // LevelChunk.getBlockState answers air there
            LevelChunkSection section = sections[si];
            if (section == null) return READ;
            if (section.m_188008_()) return SKIP;                           // hasOnlyAir: getBlockState answers air
            PalettedContainer<BlockState> states = section.m_63019_();
            if (states.getClass() != PalettedContainer.class) return READ;
            Object palette;
            try {
                Object data = (Object) DATA.invokeExact((Object) states);
                palette = data == null ? null : (Object) PALETTE.invokeExact(data);
            } catch (Throwable t) {
                return READ;
            }
            if (!(palette instanceof Palette<?> p)) return READ;
            @SuppressWarnings("unchecked")
            Palette<BlockState> pal = (Palette<BlockState>) p;
            int size = pal.m_62680_();
            int e = ((cx - this.cxMin) * this.nz + (cz - this.czMin)) * this.nsec + (si - this.secMin);
            if (e >= 0 && e < this.verdicts.length && this.palettes[e] == palette && this.sizes[e] == size && this.verdicts[e] != UNKNOWN)
                return this.verdicts[e];
            byte verdict;
            try {
                verdict = pal.m_6419_(InhibitorScan::active) ? READ : SKIP;  // maybeHas over every palette entry
            } catch (RuntimeException uninitialised) {
                return READ;
            }
            if (e >= 0 && e < this.verdicts.length) {
                this.palettes[e] = palette;
                this.sizes[e] = size;
                this.verdicts[e] = verdict;
            }
            return verdict;
        }
    }
}
