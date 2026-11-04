package bons.furious.patch.mcreator_guards49;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.GeoItem;

/**
 * Bons and Furious 1.0.49, group mcreator_guards49: the per-mod callers of the two MCreator GeckoLib animation layers, each
 * its own switch with its own guard (the target's handler fingerprint), so one updated mod or one Collections Of
 * Optimizations option makes that mod's switch step aside alone. The two layers are the 1.0.45 designs:
 *
 * Entity animation guard (layer of mcreator_animation_entity_guard). MCreator's GeckoLib template registers
 * EntityAnimationFactory.onEntityTick(LivingTickEvent) for every living entity on every tick, both sides; its body is
 * "if (event != null && event.getEntity() != null)" plus one "if (event.getEntity() instanceof X syncable) {...}" block per
 * animated entity class of the mod, and every tested class X is a GeckoLib GeoEntity (the offline proof checks the shape
 * and every X on the tested jar). For a plain LivingTickEvent (exactly that class) whose entity is not a GeoEntity no test can
 * match, so the handler hands the method a null event and its own first test returns at once. GeckoLib entities, a null
 * entity and event subclasses run the original unchanged. Identical: the skipped code is only getEntity() calls (a field
 * read) and instanceof tests. Targets here: Macabre 1.0.0 (122 tested classes; 0.9.2 identical, COO's pin 0.8.4 an accepted
 * alternative build), Armageddon 3.3.0b (30), Critters n' Crawlers 2.2.5 (28); each kept only where the offline bench is
 * faster on JDK 21 and JDK 25 by at least 7 % (the 1.0.45 rule that left the short chains out; Skarrier Mobs 1.1.2's 24
 * tests gained 6.2 % on JDK 25 and are not included).
 *
 * Held-item animation idle (layer of mcreator_item_animation_idle). MCreator's ItemAnimationFactory.animatedItems(
 * PlayerTickEvent) runs for every player twice a tick on both sides; it copies both held stacks, then acts only when
 * "phase == START and either copy's item is a GeoItem". The same test on the held stacks themselves (copy() keeps getItem())
 * decides at the start; when it is false the method returns before the copies. Exact variant: only the two throwaway copies
 * are not made. Target here: Macabre 1.0.0 (0.8.4 and 0.9.2 ship the same class).
 *
 * Runtime flags: -Dbons_and_furious.<camelName>=false runs that mod's handler as shipped (names on each Caller). No shadow
 * mode: the offline proof reads every skipped path off the handler's bytecode and the guard pins that bytecode.
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
         * LivingTickEvent whose entity is not a GeckoLib entity; the event itself otherwise.
         */
        public LivingEvent.LivingTickEvent filter(LivingEvent.LivingTickEvent event) {
            if (this.enabled && event != null && event.getClass() == LivingEvent.LivingTickEvent.class && !(event.getEntity() instanceof GeoEntity)) {
                if (!this.announced) announce();
                return null;
            }
            return event;
        }

        /** Held-item animation idle: true when animatedItems cannot act this call (it then returns before copying the hands). */
        public boolean idle(TickEvent.PlayerTickEvent event) {
            if (!this.enabled) return false;
            Player player = event.player;
            Item main = player.m_21205_().m_41720_();          // getMainHandItem().getItem()
            Item off = player.m_21206_().m_41720_();           // getOffhandItem().getItem()
            if (event.phase == TickEvent.Phase.START && (main instanceof GeoItem || off instanceof GeoItem)) return false;
            if (!this.announced) announce();
            return true;
        }

        private void announce() {
            this.announced = true;
            LOGGER.info("Bons and Furious: {} applies ({})", this.key, this.what);
        }
    }

    static final String ENTITY = "the MCreator entity animation handler skips entities that are not GeckoLib entities";
    static final String ITEM = "the MCreator held-item animation handler returns before copying the hands when it cannot act";

    public static final Caller MACABRE = new Caller("macabre_animation_entity_guard", "macabreAnimationEntityGuard", "Macabre: " + ENTITY);
    public static final Caller ARMAGEDDON = new Caller("armageddon_animation_entity_guard", "armageddonAnimationEntityGuard", "Armageddon: " + ENTITY);
    public static final Caller CNC = new Caller("cnc_animation_entity_guard", "cncAnimationEntityGuard", "Critters n' Crawlers: " + ENTITY);
    public static final Caller MACABRE_ITEM = new Caller("macabre_item_animation_idle", "macabreItemAnimationIdle", "Macabre: " + ITEM);

    private AnimationGuards() {
    }
}
