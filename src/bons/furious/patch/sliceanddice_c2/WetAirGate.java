package bons.furious.patch.sliceanddice_c2;

import com.possible_triangle.sliceanddice.Content;
import com.possible_triangle.sliceanddice.block.sprinkler.WetAir;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch sliceanddice_wet_air_gate (Slice & Dice 3.6.0, both sides; it answers on the server thread).
 *
 * Slice & Dice's sprinkler fills air with its "wet air" block. Two of its hooks look for it on every tick:
 *  - LevelMixin.isRainingAt (HEAD of Level.isRainingAt, also on dry days) calls WetAir.check, which reads the blocks at
 *    y+1, y+2 and y+3 above the position and keeps going up while it reads a crop; Entity.isInRain asks twice per
 *    entity (feet and head), fire and farmland ticks ask too;
 *  - EntityMixin.baseTick reads the block at every entity's position each tick and puts the fire out on wet air.
 * Every read is Level.getBlockState: a chunk-source getChunk(load = true) plus a section read. On a cache miss that
 * lookup adds or refreshes an UNKNOWN chunk ticket (Radium: on the first such miss of the chunk in a tick), which the next
 * tick purges again.
 *
 * The gate answers those reads from the chunk's sections: the column's chunk is fetched once with
 * getChunk(x, z, FULL, load = false), the same lookup without the ticket (only a loaded, full chunk, only on the server
 * thread; otherwise the original runs and loads as before), and a section is known to read no wet air when it holds only
 * air (Level.getBlockState returns AIR for such a section,
 * in vanilla and with Radium, and wet air is itself air: Slice & Dice registers it from CAVE_AIR's properties) or when
 * its palette has no wet-air entry: a section can only return palette entries. Positions outside the build height read
 * VOID_AIR. WetAir.check's crop walk is followed section by section: it can only continue past y+3 through a section
 * whose palette holds a crop. Only when every block the original would read is known not to be wet air does the gate
 * answer "no" (WetAir.check -> false; the baseTick read -> AIR, which the handler only compares with wet air); anything
 * else (a palette that has or had wet air, a global or unknown palette, a debug world, an unloaded chunk, another
 * thread, the client) runs Slice & Dice's code unchanged.
 *
 * Palette scans are kept per HashMapPalette (WetAirPaletteScan): such a palette only appends entries, except when
 * read(FriendlyByteBuf) refills it in place, which resets the kept scan. Linear (at most 16 entries) and single-value
 * palettes are scanned on each call.
 *
 * The one internal difference, by design: on a cache miss the original's getChunk(load = true) adds or refreshes an
 * UNKNOWN chunk ticket on the already loaded chunk (with Radium: on the first such miss of that chunk in a tick); the
 * gate's load = false lookup adds none, and the reads that follow then find the chunk in the cache, so that tick may see
 * no UNKNOWN ticket for the chunk at all. The chunk lands in the same lookup cache either way. Blocks, entities, fire,
 * saved data, RNG and visuals are unchanged. The only effect is on ticket bookkeeping: a level-33 border chunk that lost
 * every other ticket in the same tick may start unloading one tick earlier.
 * Slice & Dice's licence is not bundled (mods.toml points to its repository): no Slice & Dice code is carried here.
 */
public final class WetAirGate {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch. -Dbons_and_furious.sliceanddiceWetAirGate=false runs Slice & Dice's reads every time. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.sliceanddiceWetAirGate", "true"));
    /** -Dbons_and_furious.sliceanddiceWetAirGate.shadow=true: whenever the gate answers, also run the original and compare. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.sliceanddiceWetAirGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** What the baseTick read returns when the gate answers: a block that is not wet air (the handler only compares). */
    public static final BlockState NOT_WET = Blocks.f_50016_.m_49966_();

    static final int WET = 1;
    static final int CROP = 2;
    static final int UNKNOWN = -1;
    private static final int COUNT_MASK = (1 << 30) - 1;

    private static final MethodHandle CONTAINER_DATA;   // PalettedContainer.data (private volatile, a package-private record)
    private static final MethodHandle DATA_PALETTE;     // PalettedContainer.Data.palette
    private static volatile Block wetAir;
    private static volatile boolean announced;

    static {
        MethodHandle data = null, palette = null;
        try {
            Class<?> dataClass = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data");
            Field dataField = null, paletteField = null;
            for (Field f : PalettedContainer.class.getDeclaredFields())
                if (f.getType() == dataClass && !Modifier.isStatic(f.getModifiers())) dataField = f;
            for (Field f : dataClass.getDeclaredFields())
                if (f.getType() == Palette.class && !Modifier.isStatic(f.getModifiers())) paletteField = f;
            dataField.setAccessible(true);
            paletteField.setAccessible(true);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            data = lookup.unreflectGetter(dataField).asType(MethodType.methodType(Object.class, PalettedContainer.class));
            palette = lookup.unreflectGetter(paletteField).asType(MethodType.methodType(Palette.class, Object.class));
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: sliceanddice_wet_air_gate cannot read chunk palettes ({}); Slice & Dice's wet-air reads run unchanged", t.toString());
            data = null;
            palette = null;
        }
        CONTAINER_DATA = data;
        DATA_PALETTE = palette;
    }

    private WetAirGate() {
    }

    /**
     * The column's chunk, or null when the original must run: not a server level, a debug world, not the server thread,
     * or no full chunk loaded there. getChunk(x, z, FULL, load = false) is the original reads' own lookup (vanilla's or
     * Radium's 4-entry cache, then the chunk map) without the ticket a load = true lookup adds; it leaves the chunk in
     * that cache as the original's first read would, so the reads that follow in the same tick still find it there.
     */
    private static LevelChunk loadedChunk(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || level.m_46659_() || !server.m_7654_().m_18695_()) return null;
        ChunkAccess chunk = server.m_7726_().m_7587_(pos.m_123341_() >> 4, pos.m_123343_() >> 4, ChunkStatus.f_62326_, false);
        return chunk instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    /** True when WetAir.check(level, pos) would read no wet air and return false (decided without a ticketing lookup). */
    public static boolean rainCheckHidden(Level level, BlockPos pos) {
        if (!enabled || pos == null || CONTAINER_DATA == null) return false;
        Block wet = wetAir();
        if (wet == null) return false;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return false;
        LevelChunkSection[] sections = chunk.m_7103_();
        int y = pos.m_123342_();
        int last = Integer.MIN_VALUE;
        for (int above = 1; above <= 3; above++) {                 // always read by WetAir.check (same int arithmetic)
            int index = level.m_151564_(y + above);
            if (index == last) continue;
            last = index;
            if (index < 0 || index >= sections.length) continue;   // outside the build height: VOID_AIR
            int v = verdict(sections[index], wet);
            if (v < 0 || (v & WET) != 0) return false;
        }
        // the walk above y+3 continues only while it reads crops: follow it while a section may hold one
        int index = level.m_151564_(y + 3);
        while (index >= 0 && index < sections.length) {
            int v = verdict(sections[index], wet);
            if (v < 0 || (v & WET) != 0) return false;
            if ((v & CROP) == 0) break;
            index++;
        }
        if (!announced) announce();
        return true;
    }

    /** True when the block at pos is known not to be wet air (decided without a ticketing lookup). */
    public static boolean cellHidden(Level level, BlockPos pos) {
        if (!enabled || pos == null || CONTAINER_DATA == null) return false;
        Block wet = wetAir();
        if (wet == null) return false;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return false;
        LevelChunkSection[] sections = chunk.m_7103_();
        int index = level.m_151564_(pos.m_123342_());
        if (index >= 0 && index < sections.length) {
            int v = verdict(sections[index], wet);
            if (v < 0 || (v & WET) != 0) return false;
        }
        if (!announced) announce();
        return true;
    }

    /** WET / CROP bits for everything a read in this section can return, or UNKNOWN. */
    static int verdict(LevelChunkSection section, Block wet) {
        if (section == null) return UNKNOWN;
        if (section.m_188008_()) return 0;          // only air: every read returns AIR
        Palette<?> palette;
        try {
            Object data = (Object) CONTAINER_DATA.invokeExact((PalettedContainer) section.m_63019_());
            palette = (Palette<?>) DATA_PALETTE.invokeExact(data);
        } catch (Throwable t) {
            return UNKNOWN;
        }
        try {
            Class<?> type = palette.getClass();
            if (type == HashMapPalette.class) {
                WetAirPaletteScan scan = (WetAirPaletteScan) palette;
                int kept = scan.bons$wetAirScan();
                int done = kept & COUNT_MASK;
                int size = palette.m_62680_();
                if (done > size) {          // refilled with fewer entries (read() resets the scan; this is a safety net)
                    done = 0;
                    kept = 0;
                }
                int flags = kept >>> 30;
                for (int i = done; i < size; i++) flags |= flags((BlockState) palette.m_5795_(i), wet);
                if (done != size) scan.bons$setWetAirScan(size | flags << 30);
                return flags;
            }
            if (type == LinearPalette.class || type == SingleValuePalette.class) {
                int flags = 0;
                int size = palette.m_62680_();
                for (int i = 0; i < size; i++) flags |= flags((BlockState) palette.m_5795_(i), wet);
                return flags;
            }
        } catch (RuntimeException e) {
            return UNKNOWN;                         // an uninitialised or inconsistent palette: the original decides
        }
        return UNKNOWN;                             // the global palette or another mod's palette
    }

    private static int flags(BlockState state, Block wet) {
        Block block = state.m_60734_();
        return (block == wet ? WET : 0) | (block instanceof CropBlock ? CROP : 0);
    }

    /** Slice & Dice's wet-air block; null while it cannot be resolved (then the original code runs and decides). */
    static Block wetAir() {
        Block b = wetAir;
        if (b == null) {
            try {
                b = Content.INSTANCE.getWET_AIR().get();
            } catch (RuntimeException e) {
                return null;
            }
            wetAir = b;
        }
        return b;
    }

    /** Shadow mode: the original check after the gate said "no wet air". */
    public static void shadowRain(Level level, BlockPos pos) {
        SHADOW_CHECKS.incrementAndGet();
        if (WetAir.check(level, pos)) mismatch("WetAir.check", level, pos);
    }

    /** Shadow mode: the original read after the gate said "not wet air". */
    public static void shadowCell(Level level, BlockPos pos) {
        SHADOW_CHECKS.incrementAndGet();
        Block wet = wetAir();
        if (wet != null && level.m_8055_(pos).m_60734_() == wet) mismatch("baseTick read", level, pos);
    }

    private static void mismatch(String what, Level level, BlockPos pos) {
        long n = SHADOW_MISMATCHES.incrementAndGet();
        if (n <= 20) LOGGER.warn("Bons and Furious: sliceanddice_wet_air_gate shadow mismatch #{}: {} at {} in {} found wet air", n, what, pos, level);
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: sliceanddice_wet_air_gate: Slice & Dice's wet-air checks are answered from chunk palettes{}",
                SHADOW ? " (shadow check on)" : "");
    }
}
