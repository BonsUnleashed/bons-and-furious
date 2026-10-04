package bons.furious.patch.legendarymonsters_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix legendary_monsters_camera_null_guard (Legendary Monsters 2.2.2, All Rights Reserved; client only).
 * No Legendary Monsters code is carried.
 *
 * ClientEvent.onCameraSetup (ViewportEvent.ComputeCameraAngles, every frame) reads Minecraft.getInstance().player and
 * dereferences it ({@code player.tickCount + frameTime}) one line before its own {@code if (player != null)}, so an
 * angles event posted while there is no local player throws a NullPointerException out of the camera setup. Vanilla's own
 * renderLevel needs a camera entity, so only a mod that renders a client level with its own camera and no player can get
 * there; the guard is free and exact. With a local player the listener runs unchanged; without one it does nothing, which
 * is what the rest of its body does for a null player (everything after that line is inside the null check, and the two
 * calls before it are plain getters).
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
            Minecraft mc = Minecraft.m_91087_();
            if (mc != null && mc.f_91074_ == null) {
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
