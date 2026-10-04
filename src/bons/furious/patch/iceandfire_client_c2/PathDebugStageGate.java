package bons.furious.patch.iceandfire_client_c2;

import com.github.alexthe666.iceandfire.pathfinding.raycoms.WorldRenderMacros;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

/**
 * Bons and Furious switch iceandfire_path_debug_stage_gate (Ice and Fire 2.1.13-1.20.1-beta-5, client only). No Ice and
 * Fire code is carried here.
 *
 * Ice and Fire's ClientEvents.renderWorldLastEvent listens to RenderLevelStageEvent and hands EVERY stage (eleven Forge
 * stages per frame) to its pathfinding-debug WorldEventContext.renderWorldLastEvent, which fetches its buffer source,
 * copies seven values (pose stack, partial tick, level, player, main-hand item, render distance) into the context's
 * fields, pushes a pose, translates it by the camera, and pops it again. Only two stages use any of that:
 * AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS draws the debug paths (PathfindingDebugRenderer.render, then endBatch) and
 * AFTER_TRIPWIRE_BLOCKS calls endBatch. At every other stage the call now ends right after fetching the buffer source.
 *
 * Why identical: the buffer source is still fetched first at every stage (its lazy creation, and any failure of it, happen
 * exactly when they did). What is skipped is: getters (event pose stack and partial tick, the client level and player, the
 * player's main-hand item, the render-distance option, the camera position), plain writes to the context's public fields,
 * and a pose push + translate + pop that leaves the pose stack as it was (Embeddium's pooled push/pop included: the pooled
 * pose goes back where it came from). The context's fields are read only by PathfindingDebugRenderer.render, which runs
 * only at the kept stage, after the same call has rewritten all of them (byte census of all 511 client jars: only
 * ClientEvents, WorldEventContext and PathfindingDebugRenderer reference WorldEventContext). RenderLevelStageEvent is not
 * cancellable, so nothing else can observe the skipped call. Calls where the original would fail (no Minecraft, no
 * player, no pose stack or an empty one) are always handed to the original, so it fails at the same stage as before.
 * Kept stages run the original unchanged.
 */
public final class PathDebugStageGate {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.iceandfirePathDebugStageGate=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.iceandfirePathDebugStageGate", "true"));
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean announced;

    private PathDebugStageGate() {
    }

    /**
     * True when the call for this stage can end here (after the buffer source has been fetched, as the original's first
     * statement does); false when the original must run.
     */
    public static boolean skip(RenderLevelStageEvent event) {
        if (!enabled) return false;
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS || stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return false;
        Minecraft mc = Minecraft.m_91087_();
        if (mc == null || mc.f_91074_ == null || mc.f_91066_ == null || mc.f_91063_ == null || mc.f_91063_.m_109153_() == null) return false;
        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) return false;
        try {
            poseStack.m_85850_();             // the original's push reads the top pose: an empty stack must fail in the original
        } catch (RuntimeException e) {
            return false;
        }
        WorldRenderMacros.getBufferSource();  // the original's first statement (lazy creation stays where it was)
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: iceandfire_path_debug_stage_gate: Ice and Fire's pathfinding debug context is prepared only at the two render stages that use it");
        }
        return true;
    }
}
