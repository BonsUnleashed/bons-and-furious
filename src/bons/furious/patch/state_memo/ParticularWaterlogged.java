package bons.furious.patch.state_memo;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch particular_chest_waterlogged_memo (Particular 1.2.7, MIT; client). No code of Particular or
 * Minecraft here.
 *
 * Particular's soul-sand bubble handlers run at the end of ChestBlockEntity.lidAnimateTick and
 * EnderChestBlockEntity.lidAnimateTick, on the client, for every chest in loaded chunks on every client tick. After
 * reading its config switch, each first asks the chest's block state for WATERLOGGED, and nearly every chest is dry, so
 * that one getValue is the whole cost: FerriteCore's FastMap behind it looks the property up in a hash map of the state's
 * properties (0.20-0.48% of the client main thread in our own recording, cli10_jfr1). The answer now comes from
 * StateValueMemo, the very value getValue returned for the same state. Only for WATERLOGGED.
 *
 * -Dbons_and_furious.particularWaterloggedMemo=false asks every time. -Dbons_and_furious.particularWaterloggedMemo.shadow=true
 * also asks for every remembered answer and compares (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged).
 */
public final class ParticularWaterlogged {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.particularWaterloggedMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.particularWaterloggedMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private ParticularWaterlogged() {
    }

    /** Particular's {@code state.getValue(property)} at the start of its bubble handlers. */
    public static Comparable<?> waterlogged(BlockState state, Property<?> property) {
        if (!enabled || property != BlockStateProperties.f_61362_) return state.m_61143_(property);
        Comparable<?> value = StateValueMemo.find(state);
        if (value != null) {
            if (SHADOW) shadow(state, property, value);
            return value;
        }
        value = StateValueMemo.ask(state, property);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: particular_chest_waterlogged_memo applies (chests remember WATERLOGGED per block state for Particular's bubble handlers){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return value;
    }

    private static void shadow(BlockState state, Property<?> property, Comparable<?> remembered) {
        Comparable<?> asked = state.m_61143_(property);
        SHADOW_CHECKS.incrementAndGet();
        if (asked != remembered) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: particular_chest_waterlogged_memo shadow mismatch #{}: {} remembered {} but getValue says {}",
                    m, state, remembered, asked);
        }
    }
}
