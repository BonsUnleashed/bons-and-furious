package bons.furious.patch.saintsdragons_coo49;

import com.leon.saintsdragons.forge.client.event.DragonUIEventHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch saintsdragons_ride_hud_once_per_frame (Saint's Dragons 0.9.88, identical in 0.9.85, MIT;
 * client; since 1.0.49; trade-off, off by default).
 *
 * Saint's Dragons draws its riding HUD (the ride health bar, the melee-mode toast and the Ignivorus / Raevyx / Volitans
 * breath and charge meters) from its RenderGuiOverlayEvent.Post listener, after every HUD layer of the frame (15 to 25 a
 * frame) instead of once; only its hotbar parts look at which layer it is. With the switch on that part of the listener
 * is left out on every layer and run once per frame instead, from a RenderGuiEvent.Post listener at HIGHEST priority
 * (right after the last layer) that calls the mod's own listener with an event for a layer that is not the hotbar; the
 * hotbar parts are unchanged.
 *
 * What a player can notice: the translucent parts (the toast's background, fading meters, charge levels) show their own
 * transparency instead of being stacked nearly opaque; the ride health bar eases to a new value over half a second (its
 * easing steps once per draw) instead of almost at once; the HUD is drawn after the frame's last HUD layer.
 * If the frame listener cannot be registered, nothing is left out (one WARN). Runtime flag
 * -Dbons_and_furious.saintsdragonsRideHudOncePerFrame=false (drawn per layer again). No code of Saint's Dragons is
 * carried: its own listener draws, once.
 */
public final class SaintsHud {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.saintsdragonsRideHudOncePerFrame", "true"));
    /** The once-per-frame listener is on the Forge bus (only then is the per-layer part left out). */
    public static volatile boolean registered;
    private static volatile boolean triedRegister;
    /** The listener is being run by onFrame (render thread only). */
    private static boolean drawing;
    /** The layer the frame's single run reports: never the hotbar, so the mod's hotbar-only parts stay out of it. */
    private static final NamedGuiOverlay FRAME = new NamedGuiOverlay(new ResourceLocation("bons_and_furious", "saintsdragons_hud_frame"), (gui, graphics, partialTick, width, height) -> {
    });

    private SaintsHud() {
    }

    /** At the start of the listener's per-layer part: true = leave it out (it runs once per frame instead). */
    public static boolean skipTail() {
        if (!enabled || drawing) return false;
        if (!registered && !triedRegister) register();
        return registered;
    }

    private static synchronized void register() {
        if (registered || triedRegister) return;
        triedRegister = true;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, RenderGuiEvent.Post.class, SaintsHud::onFrame);
            registered = true;
            LOGGER.info("Bons and Furious: saintsdragons_ride_hud_once_per_frame: Saint's Dragons' riding HUD is drawn once per frame");
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: saintsdragons_ride_hud_once_per_frame could not register its frame listener ({}); the riding HUD is drawn as before", t.toString());
        }
    }

    /** RenderGuiEvent.Post (HIGHEST): the mod's own overlay listener once, for a layer that is not the hotbar. */
    public static void onFrame(RenderGuiEvent.Post event) {
        if (!enabled || drawing) return;
        drawing = true;
        try {
            DragonUIEventHandler.onRenderGuiOverlay(new RenderGuiOverlayEvent.Post(event.getWindow(), event.getGuiGraphics(), event.getPartialTick(), FRAME));
        } finally {
            drawing = false;
        }
    }
}
