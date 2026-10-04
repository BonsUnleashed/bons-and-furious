package bons.furious.patch.mesh_air;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_block_state_air_flag (Minecraft 1.21.1 with NeoForge 21.1.252; both sides). No
 * Minecraft code here.
 *
 * NeoForge (as Forge did) patches BlockState.isAir() into getBlock().isAir(state): the state's owner Block, a virtual
 * call on it, and BlockBehaviour.isAir(state) reading the state's final isAir field back. Any block class that overrides
 * isAir(BlockState) keeps the JIT from binding the call statically: every isAir() loads the Block object and dispatches
 * through its class. Chunk-mesh builders call it for every neighbour of every connected-texture block, Distant Horizons'
 * LOD builder for every block of a chunk.
 *
 * Each state now keeps its first answer in a byte (BlockStateAirFlagMixin) when that answer cannot change: the state's
 * class is exactly BlockState and its block's class declares no isAir(BlockState) of its own anywhere below BlockBehaviour,
 * so the answer is BlockBehaviour.isAir's read of the state's final field. Every other state (an overriding block class, a
 * BlockState subclass, a class whose methods cannot be listed) is asked every time, as before. A racing first call writes
 * the same byte twice.
 *
 * Ported to 1.21.1: no change of logic. NeoForge 21.1.252's BlockStateBase.isAir() is still getBlock().isAir(state),
 * BlockBehaviour.isAir(BlockState) still returns the state's final isAir field, and the field is still set once in the
 * BlockStateBase constructor from the block's properties; only the constructor's property map type changed
 * (Reference2ObjectArrayMap instead of ImmutableMap), which the guard now names.
 */
public final class BlockStateAirFlag {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.blockStateAirFlag=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.blockStateAirFlag", "true"));
    /** Shadow mode for rigs: every remembered answer is also asked of the block and compared (WARN on a difference). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.blockStateAirFlag.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Flag byte values (the state's field starts at UNKNOWN = 0). */
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
                return Boolean.FALSE;
            } catch (Throwable e) {        // a method signature names a missing class: cannot tell, keep asking
                return Boolean.FALSE;
            }
        }
    };

    private BlockStateAirFlag() {
    }

    /** The flag to store after the first answer {@code air} of block.isAir(state) inside state.isAir(). */
    public static byte flagFor(Block block, BlockState state, boolean air) {
        byte flag = block != null && state != null && state.getClass() == BlockState.class && PLAIN.get(block.getClass()) ? (air ? AIR : NOT_AIR) : ASK;
        if (!announced && flag != ASK) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_block_state_air_flag remembers isAir in each block state");
        }
        return flag;
    }

    /** Whether a block class keeps the loader's BlockBehaviour.isAir(BlockState) (probes). */
    public static boolean plainBlockClass(Class<?> blockClass) {
        return PLAIN.get(blockClass);
    }

    public static void shadow(BlockState state, boolean remembered, boolean original) {
        SHADOW_CHECKS.incrementAndGet();
        if (remembered != original && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: vanilla_block_state_air_flag shadow check: {} remembered isAir={} but the block answered {}", state, remembered, original);
    }
}
