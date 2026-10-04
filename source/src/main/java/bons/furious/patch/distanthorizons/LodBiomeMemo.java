package bons.furious.patch.distanthorizons;

import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper_neoforge;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch distanthorizons_lod_biome_memo (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge, tested
 * build DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar, LGPL; both sides: DH turns loaded and DH-generated chunks into
 * LOD data on its own threads on the client and on a server that runs DH).
 *
 * LodDataBuilder.createFromChunk walks every column of a chunk from its top solid block down to the lowest non-empty
 * height and asks chunkWrapper.getBiome(x, y, z) for every block: about 100,000-150,000 calls per chunk. For DH's own
 * NeoForge wrapper (ChunkWrapper_neoforge) that is chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2) plus a lookup of DH's
 * wrapper for the Holder in a ConcurrentHashMap, so the answer only changes at a quart (four blocks): three calls in four
 * repeat the one before.
 *
 * The redirected call keeps, for this one createFromChunk call only (a MixinExtras shared local, no thread-local), the
 * column, quart and wrapper of the previous call, and returns that wrapper when the next call is for the same chunk
 * wrapper, the same column and the same quart. getNoiseBiome is then called with the same three arguments as the call
 * that produced the remembered wrapper, and DH's map hands back the wrapper it stored for that Holder. Any other wrapper
 * class (an API-provided chunk) runs the original call every time. DH compares biome wrappers with equals and maps them
 * by equals, so even a wrapper DH's own map replaced in a concurrent put changes nothing in the LOD data.
 *
 * -Dbons_and_furious.lodBiomeMemo=false calls getBiome every time; -Dbons_and_furious.lodBiomeMemo.shadow=true
 * (verification runs only) also calls it on every memo hit and counts answers that are not equal (SHADOW_CHECKS /
 * SHADOW_MISMATCHES).
 *
 * Ported to 1.21.1: ChunkWrapper_forge is ChunkWrapper_neoforge; nothing else. LodDataBuilder (createFromChunk with its
 * two IChunkWrapper.getBiome calls) and BiomeWrapper_neoforge.getBiomeWrapper / equals / hashCode are byte-identical in
 * DH 3.3.3; ChunkWrapper_neoforge.getBiome differs only by Mojang names (QuartPos.fromBlock, ChunkAccess.getNoiseBiome),
 * both unchanged between Minecraft 1.20.1 and 1.21.1 (fromBlock = b >> 2; getNoiseBiome clamps y and reads the section's
 * biome container), and the wrapper's chunk and wrappedLevel fields are still final.
 */
public final class LodBiomeMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lodBiomeMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.lodBiomeMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    /** The previous answer of one createFromChunk call. */
    public static final class Memo {
        IChunkWrapper chunk;
        int x, z, quartY;
        IBiomeWrapper biome;
    }

    private LodBiomeMemo() {
    }

    public static IBiomeWrapper biome(IChunkWrapper chunk, int x, int y, int z, LocalRef<Memo> ref) {
        if (!enabled || chunk == null || chunk.getClass() != ChunkWrapper_neoforge.class) {
            return chunk.getBiome(x, y, z);
        }
        Memo m = ref.get();
        int quartY = y >> 2;
        if (m != null && m.chunk == chunk && m.x == x && m.z == z && m.quartY == quartY) {
            if (SHADOW) {
                IBiomeWrapper fresh = chunk.getBiome(x, y, z);
                SHADOW_CHECKS.incrementAndGet();
                if (!Objects.equals(fresh, m.biome) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                    LOGGER.warn("Bons and Furious: distanthorizons_lod_biome_memo shadow mismatch at {},{},{}: {} vs {}", x, y, z, m.biome, fresh);
                }
            }
            return m.biome;
        }
        IBiomeWrapper biome = chunk.getBiome(x, y, z);
        if (m == null) {
            m = new Memo();
            ref.set(m);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: distanthorizons_lod_biome_memo applies (LOD building reads each quart's biome once per column){}",
                        SHADOW ? " - shadow verification on" : "");
            }
        }
        m.chunk = chunk;
        m.x = x;
        m.z = z;
        m.quartY = quartY;
        m.biome = biome;
        return biome;
    }
}
