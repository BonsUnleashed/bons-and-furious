package bons.furious.patch.ribbits_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix ribbits_performer_absent_guard (Ribbits 3.0.2, LGPL-3.0; server). No Ribbits code is carried.
 *
 * PlayerInstrumentTracker.removePerformer(performer) iterates {@code performerToAudienceMap.get(performer)} without a null
 * check, so removing a performer that is not (or no longer) in the map throws a NullPointerException on the server
 * thread. Its callers are the server-tick audience update (performer removed, or no maraca in the used hand) and
 * MaracaItem.releaseUsing. On Forge 47.4.16 the sequence reported elsewhere does not occur (dropping the maraca stops the
 * use in the same packet handler, and a normal stop calls onStopUsing, not releaseUsing), so this is a latent guard: an
 * absent entry is treated as an empty audience, so the loop does nothing and the (no-op) remove still runs. When the
 * entry exists, the same set is returned and everything runs as before.
 *
 * Runtime flag: -Dbons_and_furious.ribbitsPerformerAbsentGuard=false keeps the original behaviour.
 */
public final class PerformerGuard {
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ribbitsPerformerAbsentGuard", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    /** Removals of an absent performer that were answered with an empty audience (for probes and the harness). */
    public static volatile long guarded;

    private PerformerGuard() {
    }

    /** The @WrapOperation handler for {@code performerToAudienceMap.get(performer)} in removePerformer. */
    public static Object audience(Object map, Object performer, Operation<Object> original) {
        Object audience = original.call(map, performer);
        if (audience == null && enabled) {
            guarded++;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: ribbits_performer_absent_guard: a maraca performer that was not tracked was removed without an audience (Ribbits would have thrown)");
            }
            return Collections.emptySet();
        }
        return audience;
    }
}
