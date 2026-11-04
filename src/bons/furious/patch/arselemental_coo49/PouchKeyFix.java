package bons.furious.patch.arselemental_coo49;

import alexthw.ars_elemental.client.ClientEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch arselemental_curio_pouch_keybind (Ars Elemental 0.6.7.8 / 0.6.8.0, client).
 *
 * Ars Elemental registers the curio pouch key (J by default) and writes its handler, ClientEvents.openBackpackGui (every
 * client tick: key pressed, no pouch screen open, a pouch equipped -> ask the server to open it), as an instance method
 * with @SubscribeEvent on a class that is subscribed to the mod bus as a class (@Mod.EventBusSubscriber, bus MOD). A class
 * subscription registers static methods only, so the handler is never registered and the key does nothing.
 *
 * Gameplay fix (off by default): when Ars Elemental registers its key mapping (ClientEvents.registerKeyBindings, once,
 * at client start), one ClientEvents object is registered on the Forge event bus, which subscribes exactly its instance
 * handlers: openBackpackGui, Ars Elemental's own code, which then runs as written (its key, screen and pouch checks and
 * its OpenCurioBagPacket). Nothing else changes. Runtime flag -Dbons_and_furious.arselementalCurioPouchKeybind=false.
 * Ars Elemental is LGPL-3.0; no code of it is carried.
 */
public final class PouchKeyFix {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.arselementalCurioPouchKeybind", "true"));
    /** Offline harness only: the bus to register on (null = MinecraftForge.EVENT_BUS). */
    public static volatile IEventBus bus;
    private static boolean registered;

    private PouchKeyFix() {
    }

    /** Once: the mod's own instance handler onto the Forge bus. */
    public static synchronized void register() {
        if (!enabled || registered) return;
        registered = true;
        try {
            (bus != null ? bus : MinecraftForge.EVENT_BUS).register(new ClientEvents());
            LOGGER.info("Bons and Furious: arselemental_curio_pouch_keybind: Ars Elemental's curio pouch key now opens the pouch");
        } catch (Throwable t) {   // never break key registration: without the handler the key stays as before
            LOGGER.warn("Bons and Furious: arselemental_curio_pouch_keybind could not register Ars Elemental's pouch key handler ({}); the key stays as before", t.toString());
        }
    }
}
