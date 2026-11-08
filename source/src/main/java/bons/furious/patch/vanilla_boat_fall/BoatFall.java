package bons.furious.patch.vanilla_boat_fall;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_boat_fall_no_break (Minecraft 1.21.1 with NeoForge 21.1.252, server side incl. the
 * integrated server). Kind change used as a gameplay fix: off by default; with the config key off the mixin is not
 * applied and Boat runs as shipped. No Mojang or NeoForge code here.
 *
 * What vanilla does. Boat.checkFallDamage: a boat that is not riding anything and lands on ground (status ON_LAND) after
 * falling more than three blocks hands the fall to its passengers (causeFallDamage: they take their fall damage), then, on
 * the server and if it is not removed yet, kills itself (kill; a chest boat's ChestBoat.remove spills its chest on that
 * removal) and, while the doEntityDrops game rule is on, drops three planks of its type and two sticks (spawnAtLocation;
 * the sticks are NeoForge's Boat.Type.getSticks, plain sticks for every vanilla type), and finally resets its fall
 * distance. A boat that lands in any other status only resets its fall distance.
 *
 * What the switch does. Only the kill and the five drops are left out: the boat survives the landing (a chest boat keeps
 * its chest and contents), its passengers take the same fall damage, its fall distance is reset as before, and every
 * other path of the method (landing in water, falling, riding) is unchanged. -Dbons_and_furious.boatFallNoBreak=false
 * turns the kill and drops back on at run time while the key is on.
 */
public final class BoatFall {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.boatFallNoBreak", "true"));
    private static volatile boolean announced;

    private BoatFall() {
    }

    /** checkFallDamage is about to break a boat that fell onto land: true = keep it (the switch acts). */
    public static boolean keep() {
        if (!enabled) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_boat_fall_no_break applies (a boat that falls onto land no longer breaks into planks and sticks)");
        }
        return true;
    }
}
