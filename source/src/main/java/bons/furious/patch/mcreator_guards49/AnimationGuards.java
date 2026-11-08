package bons.furious.patch.mcreator_guards49;

import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * Bons and Furious 1.0.49, group mcreator_guards49 (Minecraft 1.21.1 / NeoForge): the per-mod callers of the MCreator
 * GeckoLib entity animation layer, each its own switch with its own guard (the target's handler fingerprint), so one
 * updated mod makes that mod's switch step aside alone. The layer is the 1.0.45 design (on this port line the
 * mcreator_entity_guards group's EntityGuards):
 *
 * Entity animation guard (layer of mcreator_animation_entity_guard). MCreator's GeckoLib template registers
 * EntityAnimationFactory.onEntityTick(EntityTickEvent.Pre) on the game bus: on 1.21.1 it runs for every entity (living or
 * not) on every tick, both sides; its body is "if (event != null && event.getEntity() != null)" plus one
 * "if (event.getEntity() instanceof X syncable) {...}" block per animated entity class of the mod, and every tested class X
 * is a GeckoLib GeoEntity (the offline check reads the shape and every X on the tested jar). For a plain
 * EntityTickEvent.Pre (exactly that class) whose entity is not a GeoEntity no test can match, so the handler hands the
 * method a null event and its own first test returns at once. GeckoLib entities, a null entity and event subclasses run
 * the original unchanged. Identical: the skipped code is only getEntity() calls (a field read) and instanceof tests.
 * Target here: Armageddon 3.3.0b for NeoForge 1.21.1 (30 tested classes).
 *
 * Runtime flags: -Dbons_and_furious.<camelName>=false runs that mod's handler as shipped (names on each Caller). No shadow
 * mode: the offline check reads every skipped path off the handler's bytecode and the guard pins that bytecode.
 */
public final class AnimationGuards {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    /** One caller switch: its key, its runtime flag, its one INFO line. */
    public static final class Caller {
        public final String key;
        public final String flag;
        public volatile boolean enabled;
        private volatile boolean announced;
        private final String what;

        Caller(String key, String camelName, String what) {
            this.key = key;
            this.flag = "bons_and_furious." + camelName;
            this.enabled = !"false".equalsIgnoreCase(System.getProperty(this.flag, "true"));
            this.what = what;
        }

        /**
         * Entity animation guard: the event onEntityTick should see, null (its own first test then returns) for a plain
         * EntityTickEvent.Pre whose entity is not a GeckoLib entity; the event itself otherwise.
         */
        public EntityTickEvent.Pre filter(EntityTickEvent.Pre event) {
            if (this.enabled && event != null && event.getClass() == EntityTickEvent.Pre.class && !(event.getEntity() instanceof GeoEntity)) {
                if (!this.announced) announce();
                return null;
            }
            return event;
        }

        private void announce() {
            this.announced = true;
            LOGGER.info("Bons and Furious: {} applies ({})", this.key, this.what);
        }
    }

    static final String ENTITY = "the MCreator entity animation handler skips entities that are not GeckoLib entities";

    public static final Caller ARMAGEDDON = new Caller("armageddon_animation_entity_guard", "armageddonAnimationEntityGuard", "Armageddon: " + ENTITY);

    private AnimationGuards() {
    }
}
