package bons.furious.patch.mcreator_guards49;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.GeoItem;

/**
 * Bons and Furious 1.0.49, group mcreator_guards49 (Minecraft 1.21.1 / NeoForge): Macabre's callers of the two MCreator
 * GeckoLib animation layers, each its own switch with its own guard (the handler's fingerprint), so an updated Macabre
 * steps aside alone. The two layers are the 1.0.45 designs (on this port line the mcreator_entity_guards and
 * mcreator_item_animation groups):
 *
 * Entity animation guard (switch macabre_animation_entity_guard). MCreator's GeckoLib template registers
 * EntityAnimationFactory.onEntityTick(EntityTickEvent.Pre) on the game bus: on 1.21.1 it runs for every entity (living or
 * not) on every tick, both sides; its body is "if (event != null && event.getEntity() != null)" plus one
 * "if (event.getEntity() instanceof X syncable) {...}" block per animated entity class of the mod, and every tested class X
 * is a GeckoLib GeoEntity (Macabre 1.0.1: 122 classes, each implementing GeoEntity itself; nothing runs outside those
 * blocks). For a plain EntityTickEvent.Pre (exactly that class) whose entity is not a GeoEntity no test can match, so the
 * handler hands the method a null event and its own first test returns at once. GeckoLib entities, a null entity and
 * event subclasses run the original unchanged. Identical: the skipped code is only getEntity() calls (a field read) and
 * instanceof tests.
 *
 * Held-item animation idle (switch macabre_item_animation_idle). MCreator's ItemAnimationFactory.animatedItems(
 * PlayerTickEvent.Post) runs for every player once a tick on both sides; it copies both held stacks, then acts only when
 * "either copy's item is a GeoItem" (13 item classes of the mod behind that test). The same test on the held stacks
 * themselves (copy() keeps getItem(): an empty stack is AIR either way) decides at the start; when it is false the method
 * returns before the copies. Exact variant: only the two throwaway copies are not made (they are never stored or read
 * after the test; the item's verifyComponentsAfterLoad, which ItemStack's constructor calls for each copy, is not called
 * for them).
 *
 * Runtime flags: -Dbons_and_furious.macabreAnimationEntityGuard=false and -Dbons_and_furious.macabreItemAnimationIdle=false
 * run the handler as shipped. No shadow mode: every skipped path is read off the handler's bytecode and the guard pins
 * that bytecode.
 */
public final class MacabreAnimationGuards {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String ENTITY_KEY = "macabre_animation_entity_guard";
    public static final String ITEM_KEY = "macabre_item_animation_idle";
    public static volatile boolean entityEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.macabreAnimationEntityGuard", "true"));
    public static volatile boolean itemEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.macabreItemAnimationIdle", "true"));
    private static volatile boolean entityAnnounced, itemAnnounced;

    private MacabreAnimationGuards() {
    }

    /**
     * Entity animation guard: the event onEntityTick should see, null (its own first test then returns) for a plain
     * EntityTickEvent.Pre whose entity is not a GeckoLib entity; the event itself otherwise.
     */
    public static EntityTickEvent.Pre filter(EntityTickEvent.Pre event) {
        if (entityEnabled && event != null && event.getClass() == EntityTickEvent.Pre.class && !(event.getEntity() instanceof GeoEntity)) {
            if (!entityAnnounced) {
                entityAnnounced = true;
                LOGGER.info("Bons and Furious: {} applies (Macabre: the MCreator entity animation handler skips entities that are not GeckoLib entities)", ENTITY_KEY);
            }
            return null;
        }
        return event;
    }

    /** Held-item animation idle: true when animatedItems cannot act this call (it then returns before copying the hands). */
    public static boolean idle(PlayerTickEvent.Post event) {
        if (!itemEnabled || event == null) return false;
        Player player = event.getEntity();
        Item main = player.getMainHandItem().getItem();          // getMainHandItem().getItem()
        Item off = player.getOffhandItem().getItem();           // getOffhandItem().getItem()
        if (main instanceof GeoItem || off instanceof GeoItem) return false;
        if (!itemAnnounced) {
            itemAnnounced = true;
            LOGGER.info("Bons and Furious: {} applies (Macabre: the MCreator held-item animation handler returns before copying the hands when it cannot act)", ITEM_KEY);
        }
        return true;
    }
}
