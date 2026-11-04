package bons.furious.patch.vanilla_lookup_loops;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_multiface_face_loop (Minecraft 1.20.1 on Forge 47.4.28, both sides). No Mojang code here.
 *
 * What vanilla does. MultifaceBlock.hasAnyFace (m_153960_) and hasAnyVacantFace (m_153962_) are static helpers asked by
 * glow lichen, sculk veins and every mod multiface block (spreading, placement, world generation, block updates). Each call
 * builds a stream over the six directions of MultifaceBlock's own direction array (f_153806_) and asks anyMatch with a
 * lambda that calls the static hasFace (m_153900_): a spliterator, a pipeline head, a capturing lambda and the match
 * operation per call.
 *
 * What the switch does. The same question with a plain loop over the same array in the same order, calling the same
 * static hasFace for each direction and returning at the first match, exactly where anyMatch stops (anyMatch is
 * short-circuiting and evaluates its predicate in encounter order). The answer, the hasFace calls made (and so every hook
 * another mod has in hasFace or BlockState.hasProperty/getValue) and any exception are the same; only the stream objects
 * are no longer built. Both methods are static, so no subclass can override them.
 *
 * -Dbons_and_furious.multifaceFaceLoop=false switches it off at run time.
 */
public final class FaceLoops {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.multifaceFaceLoop", "true"));
    private static volatile boolean announced;

    private FaceLoops() {
    }

    /** hasAnyFace (wanted = true): some direction has its face set; hasAnyVacantFace (wanted = false): some has not. */
    public static boolean any(BlockState state, Direction[] directions, boolean wanted) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_multiface_face_loop applies (multiface face checks without a stream per call)");
        }
        for (Direction direction : directions) {
            if (MultifaceBlock.m_153900_(state, direction) == wanted) return true;
        }
        return false;
    }
}
