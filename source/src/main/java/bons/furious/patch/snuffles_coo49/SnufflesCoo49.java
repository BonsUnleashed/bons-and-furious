package bons.furious.patch.snuffles_coo49;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches of group snuffles_coo49 (Snuffles 1.1.2 for NeoForge 1.21.1, MIT; client). Ours; no Snuffles
 * code is carried. Idea: Collections Of Optimizations (snuffles.serverSideFluffRegrow, snuffles.cacheModelParts), idea
 * text only.
 *
 * snuffles_fluff_regrow_server_only (fix): Snuffle.tick runs "no fluff and not a baby: count fluffGrowTime down, at 0
 * setFluff(true)" on both sides. fluffGrowTime is a plain field: never synced, rolled (18,000-24,000 ticks) when the
 * entity object is built and reset only by onSheared. NeoForge 1.21.1's ShearsItem.interactLivingEntity calls onSheared on
 * both sides, so the client copy is reset (with a timer rolled on its own) only when this client's own player shears;
 * shears of other players, dispensers and anything else that shears on the server never reset it, and a snuffle first
 * seen bald counts down from the client's own roll. So the client copy flips DATA_FLUFF to true locally when its own
 * timer ends (the very next tick after such a shear once it has run out, or up to minutes before the server), while the
 * server says the snuffle is bald until its own 15-20 minute timer runs out: the player sees (and tries to shear) fluff
 * that is not there. With the fix the regrow block is skipped on client levels (SnuffleFluffRegrowMixin), so the client
 * only shows DATA_FLUFF as the server sends it. Client-only writes were never sent anywhere (DATA_FLUFF is
 * server-to-client), and the client-side fluffGrowTime is never read by anything but that block.
 * Flag: -Dbons_and_furious.snufflesFluffRegrowServerOnly=false.
 *
 * snuffles_model_parts_reuse (exact variant): see SnuffleModelPartsMixin. Flag:
 * -Dbons_and_furious.snufflesModelPartsReuse=false.
 */
public final class SnufflesCoo49 {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean regrowEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.snufflesFluffRegrowServerOnly", "true"));
    public static volatile boolean partsEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.snufflesModelPartsReuse", "true"));
    private static volatile boolean regrowAnnounced, partsAnnounced;

    private SnufflesCoo49() {
    }

    /** True when this snuffle is a client-side copy whose regrow block must not run (the switch is on). */
    public static boolean clientSkipsRegrow(Entity snuffle) {
        if (!regrowEnabled) return false;
        Level level = snuffle.level();
        if (level == null || !level.isClientSide) return false;
        if (!regrowAnnounced) {
            regrowAnnounced = true;
            LOGGER.info("Bons and Furious: snuffles_fluff_regrow_server_only applies (client snuffles show the server's fluff state)");
        }
        return true;
    }

    public static void announceParts() {
        if (!partsAnnounced) {
            partsAnnounced = true;
            LOGGER.info("Bons and Furious: snuffles_model_parts_reuse applies (the snuffle model keeps its part list)");
        }
    }
}
