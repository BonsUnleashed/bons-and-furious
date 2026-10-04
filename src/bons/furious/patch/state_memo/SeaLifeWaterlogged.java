package bons.furious.patch.state_memo;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch spawn_sealife_waterlogged_memo (Spawn 4.0.7, All Rights Reserved: nothing of Spawn's code is
 * carried, only our own logic; both sides). No code of Spawn or Minecraft here.
 *
 * Spawn's sea-life decorations (sea bunny, sea star, sea urchin and the other SeaLifeDecoBlockEntity kinds) register
 * their ticker on both sides, so SeaLifeDecoBlockEntity.tick runs for every decoration in loaded chunks on every tick, on
 * the client and on the server (where it returns after its first checks). Its first step asks the block state for
 * WATERLOGGED (SeaLifeDecoBlock.WATERLOGGED is BlockStateProperties.WATERLOGGED). With FerriteCore that is a hash lookup
 * of the state's properties each time (client main thread 0.04-0.22% in our own recording, cli10_jfr1; the part of
 * the tick that spawn_sealife_tick leaves and that an exact change can reach). The answer now comes from StateValueMemo,
 * the very value getValue returned for the same state. Only for WATERLOGGED.
 *
 * -Dbons_and_furious.sealifeWaterloggedMemo=false asks every time. -Dbons_and_furious.sealifeWaterloggedMemo.shadow=true
 * also asks for every remembered answer and compares (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged).
 */
public final class SeaLifeWaterlogged {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.sealifeWaterloggedMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.sealifeWaterloggedMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private SeaLifeWaterlogged() {
    }

    /** The {@code be.getBlockState().getValue(WATERLOGGED)} that opens SeaLifeDecoBlockEntity.tick. */
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
            LOGGER.info("Bons and Furious: spawn_sealife_waterlogged_memo applies (sea-life decorations remember WATERLOGGED per block state){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return value;
    }

    private static void shadow(BlockState state, Property<?> property, Comparable<?> remembered) {
        Comparable<?> asked = state.m_61143_(property);
        SHADOW_CHECKS.incrementAndGet();
        if (asked != remembered) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: spawn_sealife_waterlogged_memo shadow mismatch #{}: {} remembered {} but getValue says {}",
                    m, state, remembered, asked);
        }
    }
}
