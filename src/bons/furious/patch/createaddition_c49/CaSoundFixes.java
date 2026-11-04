package bons.furious.patch.createaddition_c49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious gameplay fixes for Create Crafts &amp; Additions 1.20.1-1.3.3 (MIT; client side), both off by default.
 * Ours; no Create Crafts &amp; Additions code is carried.
 *
 * What the mod does. Its electric machines report their ambient hum to CASoundScapes.play every client tick, which starts
 * one looping sound per machine group and pitch. CASoundScapes.tick() runs every 5 animation ticks a prune that is meant
 * to end the loops whose machines stopped reporting, and reads Create's "enable ambient sounds" setting for it as
 * {@code disable = enableAmbientSounds} (Create's own SoundScapes reads {@code disable = !enableAmbientSounds}). With
 * ambient sounds on (the default) every loop is therefore ended every 5 ticks and started again by the next report: the
 * hum restarts four times a second. ClientEventHandler.tickSoundscapes also calls CASoundScapes.tick() on both phases of
 * every client tick (Create runs its SoundScapes only at the END phase), so the prune can run twice in one tick.
 *
 * createaddition_ambient_sound_toggle_fix: the prune reads the setting as Create does. With ambient sounds on, a loop is
 * ended only when none of its machines reported since the last prune; with them off, every loop is ended at the next
 * prune (and none is started, as before).
 *
 * createaddition_soundscape_end_phase: CASoundScapes.tick() runs only at the END phase of the client tick, once per tick.
 *
 * -Dbons_and_furious.createadditionAmbientSoundToggleFix=false / -Dbons_and_furious.createadditionSoundscapeEndPhase=false
 * switch them off at run time (when their keys are on).
 */
public final class CaSoundFixes {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean toggleFix = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.createadditionAmbientSoundToggleFix", "true"));
    public static volatile boolean endPhase = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.createadditionSoundscapeEndPhase", "true"));
    private static volatile boolean announcedToggle, announcedEnd;

    private CaSoundFixes() {}

    /** The prune's "disable" value: Create's reading of enableAmbientSounds when the fix is on, the mod's own otherwise. */
    public static boolean disable(boolean enableAmbientSounds) {
        if (!toggleFix) return enableAmbientSounds;
        if (!announcedToggle) {
            announcedToggle = true;
            LOGGER.info("Bons and Furious: createaddition_ambient_sound_toggle_fix applies (Create Crafts & Additions keeps machine hum loops running while their machines report)");
        }
        return !enableAmbientSounds;
    }

    /** True when tickSoundscapes must not run CASoundScapes.tick() for this phase. */
    public static boolean skipPhase(boolean endPhaseEvent) {
        if (!endPhase || endPhaseEvent) return false;
        if (!announcedEnd) {
            announcedEnd = true;
            LOGGER.info("Bons and Furious: createaddition_soundscape_end_phase applies (Create Crafts & Additions ticks its sound scapes once per client tick)");
        }
        return true;
    }
}
