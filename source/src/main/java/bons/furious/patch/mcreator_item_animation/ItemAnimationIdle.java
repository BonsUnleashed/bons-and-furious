package bons.furious.patch.mcreator_item_animation;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoItem;

/**
 * Bons and Furious switch mcreator_item_animation_idle (since 1.0.45; both sides). Targets: the MCreator
 * ItemAnimationFactory.animatedItems(PlayerTickEvent) of Born in Chaos 1.7.5, Rustic Engineer 1.1.2, Terramity 0.9.8
 * and Under the Moon 1.0.5 (tested builds in TargetGuards). Ben's Sharks' copy keeps its own switch
 * (benssharks_item_animation_idle).
 *
 * What the mods do. The handler runs for every player twice a tick (START and END phase) on both sides. It first copies
 * both held stacks (ItemStack.copy: capability serialisation, a new stack with AttachCapabilitiesEvent, a tag copy),
 * then tests "phase == START and either copy's item is a GeckoLib GeoItem"; every effect of the method (the geckoAnim
 * tag of the held item, the item's animationprocedure on the client) is inside that test. Terramity's copy is 9,576
 * bytes, too big for the JIT on a server without -XX:-DontCompileHugeMethods, so it runs interpreted.
 *
 * What the switch does. A HEAD injection evaluates the same test on the held stacks themselves (copy() keeps getItem():
 * an empty stack is AIR either way) and returns at once when it is false; otherwise the method runs as shipped. Exact
 * variant, the standard of benssharks_item_animation_idle: when it returns early, only the two throwaway copies are not
 * made (they are never stored or read after the test); the held stacks, their tags, the items and every listener-visible
 * state of the game are the same.
 *
 * Guarded per target (TargetGuards, ItemAnimationPlugin): each mod's animatedItems must match the fingerprint of its tested
 * build, so an updated mod stands aside alone. -Dbons_and_furious.mcreatorItemAnimationIdle=false runs them as shipped.
 */
public final class ItemAnimationIdle {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final String KEY = "mcreator_item_animation_idle";
    /** mcreator_item_animation_idle: -Dbons_and_furious.mcreatorItemAnimationIdle=false runs the handlers as shipped. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mcreatorItemAnimationIdle", "true"));
    private static volatile boolean announced;

    private ItemAnimationIdle() {
    }

    /** True when the handler cannot act this call (it is then cancelled before it copies the held stacks). */
    public static boolean idle(PlayerTickEvent.Post event) {
        if (!enabled) return false;
        Player player = event.getEntity();
        Item main = player.getMainHandItem().getItem();          // getMainHandItem().getItem()
        Item off = player.getOffhandItem().getItem();           // getOffhandItem().getItem()
        if ((main instanceof GeoItem || off instanceof GeoItem)) return false;
        if (!announced) announce();
        return true;
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: {} applies (MCreator held-item animation handlers return before copying the hands when they cannot act)", KEY);
    }
}
