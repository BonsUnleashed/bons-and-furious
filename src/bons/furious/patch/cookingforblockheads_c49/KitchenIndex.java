package bons.furious.patch.cookingforblockheads_c49;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cookingforblockheads_recipe_book_item_index (Cooking for Blockheads 16.0.15; the server side of
 * the recipe book, so both jars). Cooking for Blockheads is All Rights Reserved: no Cooking for Blockheads code is carried,
 * only an early answer in front of one of its searches. Ours, idea from Collections Of Optimizations
 * (cookingforblockheads.indexRecipeBookScan), idea text only.
 *
 * What Cooking for Blockheads does: whenever a cooking table's recipe book changes (every click, every inventory change of
 * the menu), RecipeBookMenu.findAndSendItemList asks CookingRegistry.getRecipeStatus for every food recipe of the game, and
 * findAndSendRecipes does the same for the recipes of the selected dish. For every ingredient that asks
 * findSourceCandidates, which asks findAnyItemStack for every item the ingredient accepts: a new predicate
 * (isSameItemSameTags with the asked stack, count above 0) and a findSource call on every item provider of the kitchen,
 * each walking all of its slots. Hundreds of recipes x their ingredients x the accepted items x every slot of the kitchen.
 *
 * What the switch does: when one of those two methods has built its provider list (CookingRegistry.getItemProviders, a new
 * list every call), the items the kitchen can hand out are collected once into a set, and while that method runs,
 * findAnyItemStack answers "nothing found" at once for an asked item that is not in the set. Why identical: findAnyItemStack
 * returns a SourceItem only when some provider's findSource does, and the providers the index accepts can only return a
 * stack of the asked item: the base findSource (AbstractKitchenItemProvider) returns a slot's stack only when the predicate
 * holds for it, which needs the same item; DefaultKitchenItemProvider adds a further condition (preservation upgrade); the
 * fridge also offers its snow and ice stacks, the cutting board its board stack, the sink and the milk jar the stacks of
 * their fixed item lists (all of them are put into the set, whatever water, milk or upgrade there is). Within the two
 * methods nothing changes a slot (the simulation only counts uses). The index is used only for the very list it was built
 * for, on the thread that built it, and only when EVERY provider is of one of those exact classes: counters, fruit baskets,
 * spice racks and the player's inventory (DefaultKitchenItemProvider and its subclasses that add no method), item-handler
 * providers (ItemHandlerKitchenItemProvider), fridges, cutting boards, sinks and milk or cow jars. Any other provider class,
 * for example another mod's, leaves the whole call as shipped. An asked item that is in the set runs Cooking for
 * Blockheads' own search, so the same SourceItem, the same simulated use counts and the same order come back.
 *
 * Flag: -Dbons_and_furious.cookingforblockheadsRecipeBookItemIndex=false restores every search at runtime. Shadow mode
 * (-Dbons_and_furious.cookingforblockheadsRecipeBookItemIndex.shadow=true): nothing is skipped; every search the index
 * would answer runs and must find nothing (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class KitchenIndex {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch (the config switch acts when the classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cookingforblockheadsRecipeBookItemIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cookingforblockheadsRecipeBookItemIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Searches answered by the index (a plain counter for probes and the offline proof). */
    public static long answered;
    private static volatile boolean announced;

    /** The fridge provider's snow and ice stacks (its ice unit offers them), read through accessors. */
    public interface FridgeStacks {
        ItemStack bons$cookingforblockheads_c49Snow();

        ItemStack bons$cookingforblockheads_c49Ice();
    }

    /** The cutting board provider's board stack. */
    public interface CuttingBoardStack {
        ItemStack bons$cookingforblockheads_c49Board();
    }

    /** The fixed item list a sink or milk jar provider offers (filled once when the provider is made). */
    public interface FixedStacks {
        NonNullList<ItemStack> bons$cookingforblockheads_c49Stacks();
    }

    private static final String CFB = "net.blay09.mods.cookingforblockheads.";
    private static final Class<?> DEFAULT = find(CFB + "api.capability.DefaultKitchenItemProvider");
    private static final Class<?> ITEM_HANDLER = find(CFB + "compat.ItemHandlerKitchenItemProvider");
    private static final Class<?> COUNTER = find(CFB + "tile.CounterBlockEntity$2");
    private static final Class<?> FRUIT_BASKET = find(CFB + "tile.FruitBasketBlockEntity$2");
    private static final Class<?> SPICE_RACK = find(CFB + "tile.SpiceRackBlockEntity$2");
    private static final Class<?> FRIDGE = find(CFB + "tile.FridgeBlockEntity$2");
    private static final Class<?> CUTTING_BOARD = find(CFB + "tile.CuttingBoardBlockEntity$1");
    private static final Class<?> SINK = find(CFB + "tile.SinkBlockEntity$SinkItemProvider");
    private static final Class<?> MILK_JAR = find(CFB + "tile.MilkJarBlockEntity$MilkJarItemProvider");

    private static final class Scope {
        final Thread owner;
        final List<?> providers;
        final ReferenceOpenHashSet<Item> items;

        Scope(Thread owner, List<?> providers, ReferenceOpenHashSet<Item> items) {
            this.owner = owner;
            this.providers = providers;
            this.items = items;
        }
    }

    private static volatile Scope current;

    private KitchenIndex() {
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, KitchenIndex.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /**
     * The provider list RecipeBookMenu.findAndSendItemList / findAndSendRecipes just built: index it (or leave no index when
     * a provider is of an unknown class) and hand it back unchanged.
     */
    public static <T> List<T> open(List<T> providers) {
        current = null;
        if (!enabled || providers == null) return providers;
        ReferenceOpenHashSet<Item> items = new ReferenceOpenHashSet<>();
        for (int i = 0, n = providers.size(); i < n; i++) {
            if (!collect(providers.get(i), items)) return providers;
        }
        current = new Scope(Thread.currentThread(), providers, items);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: cookingforblockheads_recipe_book_item_index applies (recipe book searches skip items no kitchen slot holds)");
        }
        return providers;
    }

    /** The end of findAndSendItemList / findAndSendRecipes (also when it throws). */
    public static void close() {
        Scope s = current;
        if (s != null && s.owner == Thread.currentThread()) current = null;
    }

    /** True when findAnyItemStack(stack, providers, ...) can only answer null: the open index says no provider has that item. */
    public static boolean absent(ItemStack stack, List<?> providers) {
        Scope s = current;
        if (s == null || s.providers != providers || s.owner != Thread.currentThread() || !enabled) return false;
        return !stack.m_41619_() && !s.items.contains(stack.m_41720_());
    }

    private static boolean collect(Object provider, ReferenceOpenHashSet<Item> items) {
        if (provider == null) return false;
        Class<?> c = provider.getClass();
        if (c == DEFAULT || c == ITEM_HANDLER || c == COUNTER || c == FRUIT_BASKET || c == SPICE_RACK) {
            slots(provider, items);
            return true;
        }
        if (c == FRIDGE) {
            slots(provider, items);
            add(((FridgeStacks) provider).bons$cookingforblockheads_c49Snow(), items);
            add(((FridgeStacks) provider).bons$cookingforblockheads_c49Ice(), items);
            return true;
        }
        if (c == CUTTING_BOARD) {
            slots(provider, items);
            add(((CuttingBoardStack) provider).bons$cookingforblockheads_c49Board(), items);
            return true;
        }
        if (c == SINK || c == MILK_JAR) {
            NonNullList<ItemStack> stacks = ((FixedStacks) provider).bons$cookingforblockheads_c49Stacks();
            for (int i = 0, n = stacks.size(); i < n; i++) add(stacks.get(i), items);
            return true;
        }
        return false;
    }

    private static void slots(Object provider, ReferenceOpenHashSet<Item> items) {
        net.blay09.mods.cookingforblockheads.api.capability.IKitchenItemProvider p =
                (net.blay09.mods.cookingforblockheads.api.capability.IKitchenItemProvider) provider;
        for (int i = 0, n = p.getSlots(); i < n; i++) add(p.getStackInSlot(i), items);
    }

    private static void add(ItemStack stack, ReferenceOpenHashSet<Item> items) {
        if (stack != null && !stack.m_41619_()) items.add(stack.m_41720_());
    }

    /** Shadow mode: the search the index would have skipped found something (counted, first 20 logged). */
    public static void shadowFound(ItemStack stack) {
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: cookingforblockheads_recipe_book_item_index shadow mismatch #{}: {} was found although no indexed slot holds it", m, stack);
    }
}
