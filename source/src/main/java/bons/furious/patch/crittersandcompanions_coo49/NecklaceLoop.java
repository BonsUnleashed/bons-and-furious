package bons.furious.patch.crittersandcompanions_coo49;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch crittersandcompanions_necklace_loop (Critters and Companions 2.7.0 for Minecraft 1.21.1, both
 * sides).
 *
 * PearlNecklaceItem.getWearing(entity) answers "which pearl necklace does this player wear?" with six chained stream
 * stages (its own equipment stream - the inventory's items, then the extra equipment slots (PlatformHooks
 * .getAdditionalEquipment) -, map to item, filter necklaces, cast, max by level). It is asked for every player candidate of
 * every mob targeting goal that carries Critters and Companions' necklace range rule (NearestAttackableTargetGoalMixin) and
 * for every swimming player each tick (swim speed), on both sides.
 *
 * The switch walks the same equipment stream (Critters and Companions' own getEquipment, so the same inventory read and
 * the same extra-equipment call at the same point, in the same order) with one consumer that keeps the first necklace of
 * the highest level: Stream.max with Comparator.comparing keeps the first of equal elements, and so does this. A non-player
 * gets Optional.empty() as before. Same Optional contents for every input. Runtime flag
 * -Dbons_and_furious.crittersandcompanionsNecklaceLoop=false. No Critters and Companions code is carried (All Rights
 * Reserved: our own loop, its own methods).
 */
public final class NecklaceLoop {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.crittersandcompanionsNecklaceLoop", "true"));
    private static volatile boolean announced;

    private NecklaceLoop() {
    }

    private static final class Best implements Consumer<ItemStack> {
        final Class<?> type;
        final ToIntFunction<Object> level;
        Object best;
        int bestLevel;

        Best(Class<?> type, ToIntFunction<Object> level) {
            this.type = type;
            this.level = level;
        }

        @Override
        public void accept(ItemStack stack) {
            Item item = stack.getItem();
            if (!type.isInstance(item)) return;
            int l = level.applyAsInt(item);
            if (best == null || l > bestLevel) {
                best = item;
                bestLevel = l;
            }
        }
    }

    /** The first necklace of the highest level among the equipment stream's items. */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> wearing(Stream<ItemStack> equipment, Class<?> necklaceType, ToIntFunction<Object> level) {
        if (!announced) announce();
        Best b = new Best(necklaceType, level);
        equipment.forEach(b);
        return Optional.ofNullable((T) b.best);
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: crittersandcompanions_necklace_loop: the pearl necklace lookup walks the equipment once, without stream stages");
    }
}
