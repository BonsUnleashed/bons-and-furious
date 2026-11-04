package bons.furious.patch.simplyswords_footfalls;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch simplyswords_footfalls_unheld_skip (Simply Swords 1.56.0-1.20.1, Forge; both sides; since 1.0.49).
 * Helper of HelperMethodsFootfallsMixin; no Simply Swords code is carried (Timefall Development License).
 *
 * What the mod does. Every Simply Swords unique sword (40 classes), Simply More's uniques (through its footfalls helper) and
 * Simply Swords: Overhaul's Molten Edge / Soul Pyre call HelperMethods.createFootfalls(entity, stack, level, ...) from
 * inventoryTick: for every inventory slot holding such a sword, every tick, on both sides. The method is one if-block,
 * {@code if (entity instanceof Player p && Config.getBoolean("enableWeaponFootfalls", "General", default)
 * && p.getItemBySlot(MAINHAND) == stack) { particles }}, and Config.getBoolean first runs Config.safeValueFetch, which reads
 * config/simplyswords_main/general.json5 from disk (and, without simplyswords_config_unchanged_reuse, parses all of it):
 * about 130 us per call measured in 1.0.30 even with that switch. So every sword lying in an inventory costs a file read
 * per tick per side and draws nothing.
 *
 * What the switch does. When the entity is a player whose main-hand stack is not this very stack (the method's own
 * reference test), the method is handed a null entity, so its first test ({@code instanceof Player}) fails and it returns at
 * once. Every other call (a held sword, or an entity that is not a player, which the method itself skips without reading
 * anything) runs the original unchanged.
 *
 * Why the result is the same. On the skipped path the original makes exactly two calls (the offline proof reads them off
 * its bytecode): Config.getBoolean and Player.getItemBySlot, and then returns. getItemBySlot is a read of the inventory
 * (Better Combat's hook on it, in our pack, also only reads), and the switch asks it the same question at the start of the
 * method instead. Config.getBoolean refreshes Simply Swords' four private value tables from the file and reads one entry:
 * every Config.getX refreshes the tables itself before it reads them, and nothing else reads them, so no later reader can
 * tell whether this refresh happened; it throws nothing (all its I/O and parse errors are caught inside). Particles and
 * Math.random draws happen only on the held path, which is untouched.
 * Exact variant, the one internal difference: Simply Swords no longer reads its general config file for swords that are
 * not in the main hand, so on a broken or incomplete general.json5 its console warnings come once per held-sword call
 * instead of once per sword per tick.
 *
 * Runtime flag: -Dbons_and_furious.simplyswordsFootfallsUnheldSkip=false runs every call as shipped. No shadow mode: the
 * skipped path's two calls are read off the bytecode by the offline proof and the guard pins that bytecode.
 */
public final class FootfallsUnheld {
    public static final String KEY = "simplyswords_footfalls_unheld_skip";
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.simplyswordsFootfallsUnheldSkip", "true"));
    /** Calls skipped (statistics for probes and the harness; plain field, racy by design). */
    public static long skipped;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private FootfallsUnheld() {
    }

    /**
     * The entity createFootfalls should see: null (its own first test then returns) for a player whose main hand does not
     * hold this stack; the entity itself otherwise.
     */
    public static Entity entity(Entity entity, ItemStack stack) {
        if (enabled && entity instanceof Player player && player.m_6844_(EquipmentSlot.MAINHAND) != stack) {
            skipped++;
            if (!announced) announce();
            return null;
        }
        return entity;
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: {} applies (Simply Swords no longer reads its config file for footfalls of swords that are not in the main hand)", KEY);
    }
}
