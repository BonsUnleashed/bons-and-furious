package bons.furious.patch.vanilla;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_animate_tick_uniform_biome (vanilla 1.20.1 client). SRG member names.
 *
 * ClientLevel.doAnimateTick (1,334 calls per tick) asks getBiome(pos) for the ambient particles of a random block. Vanilla's
 * BiomeManager computes the fiddled distance to eight candidate quart cells (x, y and z quart, each itself or the next)
 * and returns the biome of the nearest one through level.getNoiseBiome. When all eight cells lie in one chunk section,
 * inside the build height, and that section's biome container holds a single value (a SingleValuePalette), every
 * candidate has that biome, so it is the answer whichever cell wins: the same Holder getNoiseBiome would return for any of
 * them (same chunk lookup, no y clamping, same section, the palette's only entry). Then the distance computation is
 * skipped. In every other case (cells across a section border, a chunk not loaded, a mixed section) the original call
 * runs. No random numbers are involved, so the particle draws that follow are unchanged.
 */
public final class AnimateTickBiome {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.animateTickBiome=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.animateTickBiome", "true"));
    private static final MethodHandle DATA, PALETTE, VALUE;
    private static final boolean READY;

    static {
        MethodHandle d = null, p = null, v = null;
        boolean ready = false;
        try {
            Field data = PalettedContainer.class.getDeclaredField("data");                         // data
            Class<?> dataClass = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data", false, PalettedContainer.class.getClassLoader());
            Field palette = dataClass.getDeclaredField("palette");                                     // Data.palette
            Field value = SingleValuePalette.class.getDeclaredField("value");                         // SingleValuePalette.value
            for (Field f : new Field[]{data, palette, value}) f.setAccessible(true);
            d = MethodHandles.lookup().unreflectGetter(data).asType(MethodType.methodType(Object.class, Object.class));
            p = MethodHandles.lookup().unreflectGetter(palette).asType(MethodType.methodType(Object.class, Object.class));
            v = MethodHandles.lookup().unreflectGetter(value).asType(MethodType.methodType(Object.class, Object.class));
            ready = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_animate_tick_uniform_biome is inactive because the chunk palette classes are not the supported 1.20.1 layout ({})", t.toString());
        }
        DATA = d;
        PALETTE = p;
        VALUE = v;
        READY = ready;
    }

    private AnimateTickBiome() {
    }

    /** In place of level.getBiome(pos) inside doAnimateTick. */
    public static Holder<Biome> biome(ClientLevel level, BlockPos pos) {
        if (READY && enabled) {
            Holder<Biome> uniform = uniform(level, pos);
            if (uniform != null) return uniform;
        }
        return level.getBiome(pos);
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome> uniform(ClientLevel level, BlockPos pos) {
        int qx = (pos.getX() - 2) >> 2, qy = (pos.getY() - 2) >> 2, qz = (pos.getZ() - 2) >> 2;
        if ((qx & 3) == 3 || (qy & 3) == 3 || (qz & 3) == 3) return null;           // candidates cross a section border
        ChunkAccess chunk = level.getChunk(QuartPos.toSection(qx), QuartPos.toSection(qz), ChunkStatus.BIOMES, false);
        if (chunk == null) return null;
        int minQ = QuartPos.fromBlock(chunk.getMinBuildHeight()), maxQ = minQ + QuartPos.fromBlock(chunk.getHeight()) - 1;
        if (qy < minQ || qy + 1 > maxQ) return null;                                  // getNoiseBiome would clamp y
        LevelChunkSection[] sections = chunk.getSections();
        int index = chunk.getSectionIndex(QuartPos.toBlock(qy));
        if (index < 0 || index >= sections.length) return null;
        PalettedContainerRO<Holder<Biome>> biomes = sections[index].getBiomes();
        if (!(biomes instanceof PalettedContainer<?>)) return null;
        try {
            Object data = (Object) DATA.invokeExact((Object) biomes);
            Object palette = data == null ? null : (Object) PALETTE.invokeExact(data);
            if (palette == null || palette.getClass() != SingleValuePalette.class) return null;
            return (Holder<Biome>) (Object) VALUE.invokeExact(palette);                  // null while the palette is empty
        } catch (Throwable t) {
            return null;
        }
    }
}
