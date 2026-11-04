package bons.furious.patch.terramity_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch terramity_procedure_clip_reuse (Terramity 0.9.8; both sides; since 1.0.49; a trade-off, off by
 * default). Idea: Collections Of Optimizations (terramity.memoizeProcedureRaycasts), idea text only. Our own logic; no
 * Terramity code (All Rights Reserved, injectors only).
 *
 * Terramity's procedures look along an entity's view with the same ray several times in a row: one Level.clip for the hit
 * block's x, another for its y and another for its z, each with a new ClipContext built from the same entity the same way,
 * with only reads of positions in between. Its tick procedures do this every tick: the Conductite Scouter and the Void
 * Glasses scan 64 steps along the view with three such rays per step, and the TG hand and three beam entities cast two or
 * three per tick.
 *
 * What changes, in the six tick procedures only (TerramityClipCensus.py finds the sites at build time): the first ray of
 * such a run is traced and kept for that call of the procedure; a later ray of the same run whose start, end and level
 * equal the kept one's returns the kept hit result instead of being traced again. Any other ray is traced as before.
 *
 * -Dbons_and_furious.terramityProcedureClipReuse=false traces every ray.
 */
public final class ClipReuse {
    public static final String KEY = "terramity_procedure_clip_reuse";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.terramityProcedureClipReuse", "true"));
    private static volatile boolean announced;

    private ClipReuse() {
    }

    /** A FIRST site: trace the ray and keep it for the AGAIN sites that follow in a straight line. */
    public static BlockHitResult first(Level level, ClipContext context, Operation<BlockHitResult> original, LocalRef<Object[]> kept) {
        BlockHitResult hit = original.call(level, context);
        kept.set(enabled ? new Object[] {level, context.m_45702_(), context.m_45693_(), hit} : null);
        return hit;
    }

    /** An AGAIN site: the kept hit when this ray has the kept ray's start, end and level, else traced (and kept) as a FIRST. */
    public static BlockHitResult again(Level level, ClipContext context, Operation<BlockHitResult> original, LocalRef<Object[]> kept) {
        Object[] k = kept.get();
        if (enabled && k != null && k[0] == level && k[1].equals(context.m_45702_()) && k[2].equals(context.m_45693_())) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies (Terramity's tick procedures reuse a ray they just traced)", KEY);
            }
            return (BlockHitResult) k[3];
        }
        return first(level, context, original, kept);
    }
}
