package bons.furious.patch.mcreator_guards49;

import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * Bons and Furious 1.0.49, switch cnc_animation_entity_guard (group mcreator_guards49, both sides), the caller of the
 * mcreator_animation_entity_guard design (1.0.45) for Critters n' Crawlers 2.2.5 for Minecraft 1.21.1, with its own switch
 * and its own guard (the handler's fingerprint), so an updated Critters n' Crawlers steps aside alone.
 *
 * What the mod does. MCreator's GeckoLib template registers EntityAnimationFactory.onEntityTick(EntityTickEvent.Pre), so it
 * runs for every entity on every tick, both sides; its body is "if (event != null && event.getEntity() != null)" plus one
 * "if (event.getEntity() instanceof X syncable) {...}" block per animated entity class of the mod (28), and every tested
 * class X is a GeckoLib GeoEntity. For a plain EntityTickEvent.Pre (exactly that class) whose entity is not a GeoEntity no
 * test can match, so the handler hands the method a null event and its own first test returns at once. GeckoLib entities,
 * a null entity and event subclasses run the original unchanged. Identical: the skipped code is only getEntity() calls (a
 * field read) and instanceof tests.
 *
 * Runtime flag: -Dbons_and_furious.cncAnimationEntityGuard=false runs the handler as shipped. No shadow mode: every skipped
 * path is read off the handler's bytecode and the guard pins that bytecode.
 */
public final class CncAnimationGuard {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "cnc_animation_entity_guard";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cncAnimationEntityGuard", "true"));
    private static volatile boolean announced;

    private CncAnimationGuard() {
    }

    /**
     * The event onEntityTick should see: null (its own first test then returns) for a plain EntityTickEvent.Pre whose entity
     * is not a GeckoLib entity; the event itself otherwise.
     */
    public static EntityTickEvent.Pre filter(EntityTickEvent.Pre event) {
        if (enabled && event != null && event.getClass() == EntityTickEvent.Pre.class && !(event.getEntity() instanceof GeoEntity)) {
            if (!announced) announce();
            return null;
        }
        return event;
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: {} applies (Critters n' Crawlers: the MCreator entity animation handler skips entities that are not GeckoLib entities)", KEY);
    }
}
