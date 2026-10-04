package bons.furious.patch.legendarymonsters_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix legendary_monsters_camera_null_guard (Legendary Monsters, All Rights Reserved; 1.21.1 tested build:
 * Legendary Monsters 2.2.3 for NeoForge 1.21.1, legendary_monsters-2.2.3 MC 1.21.1.jar; client only). No Legendary
 * Monsters code is carried.
 *
 * ClientEvent.onCameraSetup (ViewportEvent.ComputeCameraAngles, every frame, priority HIGHEST) reads
 * Minecraft.getInstance().player and dereferences it ({@code player.tickCount + partialTick}) before anything else, so an
 * angles event posted while there is no local player throws a NullPointerException out of the camera setup. Vanilla's own
 * renderLevel needs a camera entity, so only a mod that renders a client level with its own camera and no player can get
 * there; the guard is free and exact. With a local player the listener runs unchanged; without one it does nothing,
 * which is what the listener itself does before it throws (the calls before the dereference are plain getters).
 *
 * Ported to 1.21.1: Legendary Monsters 2.2.3 dropped the listener's own {@code if (player != null)} entirely (2.2.2 had it
 * one line too late), so every line after the first dereference also reads the player; the partial tick now comes from
 * Minecraft.getTimer().getGameTimeDeltaPartialTick(false), a getter. The bug is not fixed upstream; same wrapper.
 *
 * Runtime flag: -Dbons_and_furious.legendaryMonstersCameraNullGuard=false runs the original on every event.
 */
public final class CameraNullGuard {
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.legendaryMonstersCameraNullGuard", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    /** Events skipped because there was no local player (for probes and the harness). */
    public static volatile long skipped;

    private CameraNullGuard() {
    }

    /** The @WrapMethod handler body for ClientEvent.onCameraSetup(event). */
    public static void onCameraSetup(Object event, Operation<Void> original) {
        if (enabled) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player == null) {
                skipped++;
                if (!announced) {
                    announced = true;
                    LOGGER.info("Bons and Furious: legendary_monsters_camera_null_guard: a camera-angles event without a local player was skipped by Legendary Monsters' camera shake listener (it would have thrown)");
                }
                return;
            }
        }
        original.call(event);
    }
}
