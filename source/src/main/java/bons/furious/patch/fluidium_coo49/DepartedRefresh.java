package bons.furious.patch.fluidium_coo49;

import me.kall.fluidium.common.data.ActiveChunks;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch fluidium_departed_dimension_refresh (Fluidium 1.21.1-1.4.0; kind change, gameplay fix, off by
 * default; server; helper of DepartedDimensionMixin; no Fluidium code carried).
 *
 * What Fluidium does. ActiveChunks keeps, per dimension, the chunks near players (fluids there always update; fluids elsewhere
 * may skip an update by Fluidium's random delay). A dimension's list is rebuilt from its players on its next level tick
 * (LevelTickEvent.Pre on a server level) after it was marked in UPDATE_REQUIRED: on login, logout, chunk-border and height
 * moves of a player (its ServerPlayer mixin), and on a dimension change - but dimChange marks only the dimension the player
 * arrived in (event.getTo()). The dimension the player left keeps the player's old chunks in its list until something else
 * marks it, so fluids around the spot the player left keep updating at full rate there.
 *
 * What the switch does. dimChange also marks the departed dimension (event.getFrom()), queued on the server the same way
 * (server.execute), so that dimension's list is rebuilt from its remaining players on its next level tick. Gameplay fix (off
 * by default): after a portal trip, fluids near the spot the player left go back to Fluidium's random delay. Nothing is saved
 * or sent differently. Runtime flag bons_and_furious.fluidiumDepartedDimensionRefresh.
 */
public final class DepartedRefresh {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fluidiumDepartedDimensionRefresh", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private DepartedRefresh() {
    }

    /** At the return of ActiveChunks.dimChange(event). */
    public static void departed(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!enabled || event == null || event.getFrom() == null || !(event.getEntity() instanceof ServerPlayer player) || player.server == null) return;
        net.minecraft.resources.ResourceLocation from = event.getFrom().location();
        player.server.execute(() -> ActiveChunks.UPDATE_REQUIRED.add(from));
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fluidium_departed_dimension_refresh: a dimension a player leaves rebuilds its Fluidium active chunks");
        }
    }
}
