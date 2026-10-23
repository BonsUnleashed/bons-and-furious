package bons.furious.patch.mcreator_entity_guards;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * Bons and Furious switch mcreator_animation_entity_guard (since 1.0.45; both sides). Targets: the MCreator
 * EntityAnimationFactory.onEntityTick of Born in Chaos 1.7.5 (83 tested classes) and Luminous Beasts 1.2.3 (35) (tested
 * builds in TargetGuards). 16 more MCreator factories of the pack were measured and are left out: their instanceof chains
 * are short (or their classes shallow) and the JIT makes them as cheap as this guard's own check.
 *
 * What the mods do. MCreator's GeckoLib template registers onEntityTick for every LivingTickEvent, so it runs for every
 * living entity on every tick on both sides. Its body is "if (event != null && event.getEntity() != null)" followed by
 * one "if (event.getEntity() instanceof X syncable) {...}" block per animated entity class of the mod (83 in Born in
 * Chaos); nothing runs outside those blocks. Every tested class X is a GeckoLib GeoEntity (the offline proof checks the
 * method's shape and every X on the installed jars).
 *
 * What the switch does. For a plain LivingTickEvent (exactly that class) whose entity is not a GeoEntity, none of the
 * instanceof tests can match, so the handler hands the method a null event and the method's own first test returns at
 * once: the same design as the 1.0.19 *_animation_entity_guard keys of the mcreator group, here with a run-time flag.
 * GeckoLib entities, a null entity (the method's own null test returns either way) and event subclasses run the
 * original code unchanged. Identical: the skipped code is only instanceof tests and event.getEntity() calls (a field
 * read), which have no effect; no argument the method can act on is withheld.
 *
 * Guarded per target (TargetGuards, EntityGuardsPlugin): each mod's onEntityTick must match the fingerprint of its
 * tested build, so an updated mod steps aside alone (WARN) while the other factories keep the switch.
 *
 * -Dbons_and_furious.mcreatorAnimationEntityGuard=false runs every factory as shipped.
 */
public final class EntityGuards {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final String KEY = "mcreator_animation_entity_guard";
    /** mcreator_animation_entity_guard: -Dbons_and_furious.mcreatorAnimationEntityGuard=false runs the factories as shipped. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mcreatorAnimationEntityGuard", "true"));
    private static volatile boolean announced;

    private EntityGuards() {
    }

    /**
     * The event onEntityTick should see: null (its own first test then returns) for a plain LivingTickEvent whose entity
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
        LOGGER.info("Bons and Furious: {} applies (MCreator entity animation handlers skip entities that are not GeckoLib entities)", KEY);
    }
}
