package bons.furious.patch.ie_sound;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch ie_multiblock_sound_replaced (Immersive Engineering 10.2.0-183, client). Fix for IE issue #6378
 * as it occurs in the 1.20.1 build.
 *
 * IE's multiblocks (crusher, diesel generator, arc furnace, ...) start their looping machine sound with
 * MultiblockSound.startSound, which returns `() -> soundManager.isActive(instance)`; every client tick the multiblock
 * starts a new sound whenever that answers false. When a PlaySoundEvent handler replaces the instance - IE's own earmuffs
 * wrap every tickable block sound in an IEMuffledTickableSound - the sound engine plays the replacement and never reports
 * the original instance as active, so the multiblock starts another sound (and another muffled copy) every tick. Each of
 * those keeps looping until the multiblock is broken: the sound engine's list of ticking sounds grows by 20 per second per
 * machine in hearing range until no sound channel is left (and other sounds stop playing).
 *
 * The switch remembers, for an IE MultiblockSound, the instance Forge's PlaySoundEvent left in its place
 * (SoundEngineReplacementMixin, at ForgeHooksClient.playSound inside SoundEngine.play), and the multiblock's "is my sound
 * playing" check also counts that replacement as its sound (MultiblockSoundMixin). Sounds nobody replaced, and sounds a
 * handler cancelled (no replacement plays), answer exactly as before.
 */
public final class MultiblockSoundFix {
    /** Runtime switch. -Dbons_and_furious.ieMultiblockSoundReplaced=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ieMultiblockSoundReplaced", "true"));
    /** Probe counters (plain longs): replacements remembered; checks answered true through a replacement. */
    public static long linked, heldByReplacement;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private MultiblockSoundFix() {
    }

    /** After Forge's PlaySoundEvent: remember what replaced an IE multiblock sound (null when nothing did). */
    public static SoundInstance link(SoundInstance original, SoundInstance played) {
        if (original instanceof IeSoundLink link) {
            SoundInstance other = played != original ? played : null;
            link.bons$setPlayedAs(other);
            if (other != null) linked++;
        }
        return played;
    }

    /** MultiblockSound's `soundManager.isActive(instance)`, also true while the instance's replacement plays. */
    public static boolean isActive(SoundManager manager, SoundInstance instance, Operation<Boolean> original) {
        boolean active = original.call(manager, instance);
        if (active || !enabled || !(instance instanceof IeSoundLink link)) return active;
        SoundInstance played = link.bons$playedAs();
        if (played == null || !original.call(manager, played)) return false;
        heldByReplacement++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: ie_multiblock_sound_replaced keeps an Immersive Engineering machine sound that another handler replaced (earmuffs) from restarting every tick");
        }
        return true;
    }
}
