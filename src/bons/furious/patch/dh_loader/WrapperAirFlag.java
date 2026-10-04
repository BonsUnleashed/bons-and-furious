package bons.furious.patch.dh_loader;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch distanthorizons_wrapper_air_flag (Distant Horizons 3.3.2 + Forge 47.4.16; both sides). No Distant
 * Horizons or Minecraft code here.
 *
 * DH wraps every block state in a BlockStateWrapper_forge (one per state, created once, its blockState field final) and
 * asks the wrapper isAir() for every data point it turns into render data (FullDataToRenderDataTransformer
 * .setRenderColumnView), next to wrapper fields it precomputed (material, opacity, solid, liquid). isAir() is the only
 * call in that loop that leaves the wrapper: BlockState.isAir() is Forge-patched to getBlock().isAir(state), a virtual
 * call through the Block object to BlockBehaviour.isAir(BlockState), which reads the state's final isAir field. For cold
 * states that is two cache misses per data point (state, block) plus an unpredictable virtual call.
 *
 * The wrapper now remembers its first answer when that answer cannot change: blockState null (DH's AIR wrapper: always
 * true), or a state whose class is exactly BlockState and whose block's class does not declare its own isAir(BlockState)
 * anywhere below BlockBehaviour (then the answer is the state's final field). Everything else (a block class that
 * overrides isAir, such as Ars Nouveau's IntangibleAirBlock or Immersive Engineering's FakeLightBlock, a BlockState
 * subclass, or a class whose methods cannot be listed) is asked every time, as before. The flag byte lives in the wrapper
 * (WrapperAirFlagMixin); a racing first call writes the same value twice.
 */
public final class WrapperAirFlag {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.dhWrapperAirFlag=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dhWrapperAirFlag", "true"));
    /** Shadow mode for rigs: every remembered answer is also asked of the state and compared (WARN on a difference). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.dhWrapperAirFlag.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Flag byte values (the wrapper's field starts at UNKNOWN = 0). */
    public static final byte UNKNOWN = 0, NOT_AIR = 1, AIR = 2, ASK = -1;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    /** Per block class: true when no class from it up to (excluding) BlockBehaviour declares isAir(BlockState). */
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                    if (c == BlockBehaviour.class) return Boolean.TRUE;
                    for (var m : c.getDeclaredMethods()) {
                        if (m.getName().equals("isAir") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == BlockState.class)
                            return Boolean.FALSE;
                    }
                }
                return Boolean.FALSE;      // not a BlockBehaviour at all
            } catch (Throwable e) {        // a method signature names a missing class: cannot tell, keep asking
                return Boolean.FALSE;
            }
        }
    };

    private WrapperAirFlag() {
    }

    /** The flag to store after the first isAir() answer of a wrapper whose blockState is {@code state}. */
    public static byte flagFor(BlockState state, boolean air) {
        byte flag;
        if (state == null) flag = air ? AIR : NOT_AIR;
        else if (state.getClass() == BlockState.class && PLAIN.get(state.m_60734_().getClass())) flag = air ? AIR : NOT_AIR;
        else flag = ASK;
        if (!announced && flag != ASK) {
            announced = true;
            LOGGER.info("Bons and Furious: distanthorizons_wrapper_air_flag remembers isAir in Distant Horizons' block-state wrappers");
        }
        return flag;
    }

    /** Whether the state's block class keeps Forge's default isAir(BlockState) (probes). */
    public static boolean plainBlockClass(Class<?> blockClass) {
        return PLAIN.get(blockClass);
    }

    public static void shadow(BlockState state, boolean remembered, boolean original) {
        SHADOW_CHECKS.incrementAndGet();
        if (remembered != original && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: distanthorizons_wrapper_air_flag shadow check: {} remembered isAir={} but the state answered {}",
                    state, remembered, original);
    }
}
