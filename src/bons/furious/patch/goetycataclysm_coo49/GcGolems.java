package bons.furious.patch.goetycataclysm_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group goetycataclysm_coo49 (Goety Cataclysm 1.20-1.9.2; its 1.20-1.7.0 has the same methods).
 * Helper of the group's mixins; Goety Cataclysm is all rights reserved: no code of it is carried, the logic is ours.
 *
 * <p>goety_cataclysm_golem_types_once (fix, both sides): GCEvents.worldLoad calls GCGolemTypes.addGolems on every
 * LevelEvent.Load (each server level, and the client level after every dimension change), and every call adds five new
 * constants to Goety's extensible GolemType enum (GolemType.create) plus five entries to NEW_GOLEM_TYPES, never removed:
 * a leak, and every GolemType.values() walk (getMolds, getGolemList, spawnServant) grows with it. The switch lets the first
 * call that completes register the five types exactly as before and skips every later call (once per game). Nothing else
 * changes: the molds are stateless (only static final fields), getMolds lists the first-registered mold first either way,
 * nothing reads GolemType ordinals except the unload sweep, GolemType.valueOf is not used, and addGolems has no other
 * caller.
 *
 * <p>goety_cataclysm_golem_unload_skip (exact, both sides): GCEvents.worldUnload clones GolemType.values(), searches
 * NEW_GOLEM_TYPES for every constant and builds an ArrayUtils.remove copy for each match, then discards every copy: the
 * method changes nothing (ArrayUtils.remove returns a new array; an ordinal is always inside the snapshot it came from, so
 * nothing throws). The switch returns at its start.
 *
 * <p>goety_cataclysm_want_looting_key_check (gameplay fix, off by default, server): GCEvents.LootEvents asks whether the
 * killing projectile or effect entity belongs to Cataclysm by searching its TRANSLATED type name (e.g. "Wave") for
 * "cataclysm" / "goety_cataclysm", which practically never matches, so the Ring of Want's looting bonus never applies to
 * those kills. With the switch the two tests see the untranslated key instead (entity.cataclysm.wave): for a translatable
 * description (what EntityType.getDescription builds from getDescriptionId) the key, otherwise the original text.
 *
 * <p>Runtime flags (=false: off): bons_and_furious.goetyCataclysmGolemTypesOnce, .goetyCataclysmGolemUnloadSkip,
 * .goetyCataclysmWantLootingKeyCheck. No shadow mode (the first two are one-shot / no-op paths proven offline; the third is
 * a documented behaviour change).
 */
public final class GcGolems {
    public static volatile boolean once = flag("goetyCataclysmGolemTypesOnce");
    public static volatile boolean unloadSkip = flag("goetyCataclysmGolemUnloadSkip");
    public static volatile boolean lootKey = flag("goetyCataclysmWantLootingKeyCheck");
    /** Statistics (plain fields): registrations skipped, sweeps skipped, looting tests answered with the key. */
    public static long skippedRegistrations, skippedSweeps, keyAnswers;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Object LOCK = new Object();
    private static volatile boolean registered, announcedOnce, announcedLoot;

    private GcGolems() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** Wrapper of GCGolemTypes.addGolems(): the original once (the first call that completes), later calls skipped. */
    public static void addGolems(Operation<Void> original) {
        if (!once) {
            original.call();
            return;
        }
        if (!registered) {
            synchronized (LOCK) {
                if (!registered) {
                    original.call();          // throws as before; then the next load tries again, as the stock code would
                    registered = true;
                    return;
                }
            }
        }
        skippedRegistrations++;
        if (!announcedOnce) {
            announcedOnce = true;
            LOGGER.info("Bons and Furious: goety_cataclysm_golem_types_once: Goety Cataclysm's golem types stay registered once (later world loads no longer add duplicates)");
        }
    }

    /** GCEvents.worldUnload: true = return at its start (its body only builds copies it discards). */
    public static boolean skipSweep() {
        if (!unloadSkip) return false;
        skippedSweeps++;
        return true;
    }

    /** Test seam for the offline proof: the registration state (a fresh game). */
    public static void resetForProof() {
        registered = false;
    }

    /** The two Component.getString() reads of GCEvents.LootEvents (the killing entity's type description). */
    public static String lootName(Component description, Operation<String> original) {
        if (lootKey && description != null && description.m_214077_() instanceof TranslatableContents tc) {
            keyAnswers++;
            if (!announcedLoot) {
                announcedLoot = true;
                LOGGER.info("Bons and Furious: goety_cataclysm_want_looting_key_check: the Ring of Want's Cataclysm kill test reads entity type keys (entity.cataclysm.*)");
            }
            return tc.m_237508_();
        }
        return original.call(description);
    }
}
