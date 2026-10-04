package bons.furious.patch.distanthorizons;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch distanthorizons_biome_blend_memo (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge, tested
 * build DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar, LGPL; client).
 *
 * For the nearest LOD rings (an LOD no wider than DH's lodBiomeBlending radius) AbstractDhTintGetter_neoforge.tryGetBlockTint
 * averages the tint over (2r+1)^2 neighbouring data points (49 at radius 3) and asks tryGetClientBiomeColor for each:
 * BlockBiomeWrapperPair.get (two nested ConcurrentHashMap lookups) and the colour cache (a third, keyed by the pair).
 * Neighbours mostly share one biome. Inside one tryGetBlockTint call the block state and the colour resolver are fixed,
 * so the same biome wrapper object gives the same pair and the same cached colour: the redirected call
 * (BiomeBlendMemoMixin) reuses the previous sample's colour while the biome wrapper object is the same, and asks DH
 * otherwise. A -1 (no colour, DH returns at once) is never reused. The only way the original could differ is DH's own
 * colour cache being cleared or overwritten by another thread in the middle of one point's loop, which already gives
 * DH's loop a mixed answer.
 *
 * -Dbons_and_furious.biomeBlendMemo=false asks DH for every sample; -Dbons_and_furious.biomeBlendMemo.shadow=true
 * (verification runs only) asks DH on every reuse too and counts colours that differ (SHADOW_CHECKS / SHADOW_MISMATCHES).
 *
 * Ported to 1.21.1: AbstractDhTintGetter_forge / BiomeWrapper_forge are AbstractDhTintGetter_neoforge /
 * BiomeWrapper_neoforge; nothing else. tryGetBlockTint(DhBlockPosMutable, ColorResolver) is byte-identical in DH 3.3.3
 * (the loop's call is still the second of its three tryGetClientBiomeColor calls), tryGetClientBiomeColor differs only by
 * the Mojang name ColorResolver.getColor (still called with x = z = 0.0), and BlockBiomeWrapperPair is byte-identical.
 */
public final class BiomeBlendMemo {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.biomeBlendMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.biomeBlendMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private BiomeBlendMemo() {
    }

    public static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: distanthorizons_biome_blend_memo applies (LOD tint blending reuses a neighbour's colour for the same biome){}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }

    public static void shadow(int remembered, int fresh) {
        SHADOW_CHECKS.incrementAndGet();
        if (remembered != fresh && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: distanthorizons_biome_blend_memo shadow mismatch: {} vs {}", remembered, fresh);
        }
    }
}
