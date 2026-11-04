package bons.furious.patch.immersiveweathering_c49;

import com.ordana.immersive_weathering.blocks.LeafPileBlock;
import java.util.concurrent.atomic.AtomicLong;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;
import net.mehvahdjukaar.moonlight.api.set.leaves.LeavesType;
import net.mehvahdjukaar.moonlight.api.util.math.colors.RGBColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch immersiveweathering_leaf_pile_tint_memo (Immersive Weathering 1.20.1-2.0.5, LGPLv3; client side,
 * the chunk-mesh builder threads). Immersive Weathering's getLeafTypeColor is carried here (LGPLv3 permits it).
 *
 * What Immersive Weathering does. Every tinted quad of a leaf pile asks its block colour: the leaves' own colour at that
 * position (event.getColor, biome dependent), then new RGBColor(original).asLAB().mixWith(new RGBColor(0x7D5212).asLAB(),
 * age / 10f).asRGB().toInt(): two RGB -> LAB conversions (the brown one is the same every time), a mix and a LAB -> RGB
 * conversion, about six colour objects per quad.
 *
 * What the switch does. The leaves' colour lookup still runs every call (it depends on the biome at the position); the
 * mixed colour is then taken from a lock-free direct-mapped table keyed on (that colour, AGE) and computed with Immersive
 * Weathering's own expression on a miss. Moonlight's RGBColor / LABColor conversions are pure functions of their inputs
 * (no mutable state; every value comes from the arguments), so the table returns what the expression returns. Entries are
 * immutable objects published through a plain array slot: a racing reader sees a whole entry or none.
 *
 * -Dbons_and_furious.immersiveweatheringLeafPileTintMemo=false switches it off at run time.
 * -Dbons_and_furious.immersiveweatheringLeafPileTintMemo.shadow=true: every hit is recomputed and compared
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class LeafTint {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.immersiveweatheringLeafPileTintMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.immersiveweatheringLeafPileTintMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final int BROWN = 8213010;
    private static final int BITS = 12;
    private static final Entry[] TABLE = new Entry[1 << BITS];
    private static volatile boolean announced;

    private record Entry(int original, int age, int mixed) {}

    private LeafTint() {}

    /** Immersive Weathering's mixed colour for a leaves colour and a pile age, as its getLeafTypeColor computes it. */
    public static int mix(int original, int age) {
        float percentage = (float) age / 10.0f;
        return new RGBColor(original).asLAB().mixWith(new RGBColor(BROWN).asLAB(), percentage).asRGB().toInt();
    }

    /** getLeafTypeColor's body: the leaves colour lookup, then the mixed colour from the table. */
    public static int color(ClientHelper.BlockColorEvent event, LeavesType type, BlockState state, BlockAndTintGetter level, BlockPos pos, int i) {
        int original = event.getColor(type.leaves.m_49966_(), level, pos, i);
        int age = state.m_61143_(LeafPileBlock.AGE);
        int h = (original * 0x9E3779B9) ^ (age * 0x85EBCA6B);
        int slot = (h ^ (h >>> 15)) & ((1 << BITS) - 1);
        Entry e = TABLE[slot];
        if (e != null && e.original == original && e.age == age) {
            if (SHADOW) shadow(original, age, e.mixed);
            return e.mixed;
        }
        int mixed = mix(original, age);
        TABLE[slot] = new Entry(original, age, mixed);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: immersiveweathering_leaf_pile_tint_memo applies (Immersive Weathering's leaf pile tints come from a colour table){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return mixed;
    }

    private static void shadow(int original, int age, int memo) {
        SHADOW_CHECKS.incrementAndGet();
        int fresh = mix(original, age);
        if (fresh != memo) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: immersiveweathering_leaf_pile_tint_memo shadow mismatch #{}: colour {} age {}: table {} vs {}",
                    m, Integer.toHexString(original), age, Integer.toHexString(memo), Integer.toHexString(fresh));
        }
    }
}
