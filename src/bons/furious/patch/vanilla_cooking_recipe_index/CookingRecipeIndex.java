package bons.furious.patch.vanilla_cooking_recipe_index;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, layer vanilla_cooking_recipe_item_index (both sides): an exact first-match index for the cooking
 * recipe lookups of mods that ask RecipeManager.getRecipeFor(type, container, level) with a one-slot input. No Minecraft, Forge
 * or mod code is carried.
 *
 * What the mods do. Cooking for Blockheads' oven (OvenBlockEntity.getSmeltingResult: 9 slots per frame on the oven screen, per
 * hopper insert test, per finished item) and Mystical Agriculture's essence furnaces (EssenceFurnaceTileEntity.tick, every tick
 * a furnace burns or holds fuel and input) call getRecipeFor(SMELTING, ...), which walks every smelting recipe of the pack in
 * table order and asks each one matches(container, level) until one does (Forge 47.4.28: byType(type).values().stream()
 * .filter(matches).findFirst()). With ~1,000 smelting recipes in a large pack that is tens of microseconds per call.
 *
 * What the layer does. Per RecipeManager (a field the layer's own mixin adds) and cooking type, a table of the type's recipes
 * in that same order, each marked PROVABLE when its class does not override AbstractCookingRecipe.matches (which is
 * ingredient.test(container.getItem(0))) and its ingredient is exactly vanilla's Ingredient class (Forge's NBT, compound,
 * difference and intersection ingredients are other classes). For a provable recipe the answer is a function of the input's
 * item alone: vanilla's Ingredient.test is "null -> false; empty ingredient -> input empty; else an item of getItems() is the
 * input's item", and getItem() is AIR exactly for an empty stack. Per input item the layer keeps the first provable recipe that
 * matches it and the recipes that are not provable before it, in order. A lookup asks those others matches(container, level)
 * in order and answers the first that does, else that first provable match, else empty: the recipe the walk finds.
 *
 * When it applies. Only while (1) the layer's probe found RecipeManager.getRecipeFor and its filter lambda, the table lookup
 * byType, AbstractCookingRecipe.matches and Ingredient.test exactly as shipped after every other mod's mixins (an injector of
 * Polymorph, Kiwi or BCLib on getRecipeFor, which can answer differently per player or context, turns the whole layer off,
 * and so does any change to Ingredient.test, ModernFix's faster_ingredients hook included: its answer for one item follows
 * its reload state), (2) the manager is RecipeManager itself, not a subclass (FastSuite installs its own), and (3) the
 * memo's vanilla_recipe_generation_token is current (same recipe table, no tag rebind, Forge's ingredient counter
 * unchanged). Otherwise the call runs as shipped. A tag rebind on another thread (the integrated server's reload while the
 * client looks up) is seen when TagsUpdatedEvent fires; until then the stock answer itself races with the rebind and the
 * memo gives the answer of the binding before it.
 *
 * Exact: the same recipe object (or none) for every call; the Optional is a new object as before (the callers only unwrap it).
 * What no longer runs per call: matches() of the provable recipes before the answer (pure reads of their ingredient's item
 * array or the item's tags).
 *
 * Runtime flag: -Dbons_and_furious.vanillaCookingRecipeItemIndex=false (all callers), plus each caller's own flag. SHADOW MODE:
 * -Dbons_and_furious.vanillaCookingRecipeItemIndex.shadow=true also runs the walk on every call, returns its answer and compares
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class CookingRecipeIndex {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaCookingRecipeItemIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaCookingRecipeItemIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Lookups answered by the index, type tables built, item entries built (statistics for probes and the harness). */
    public static final AtomicLong ANSWERED = new AtomicLong(), TABLES = new AtomicLong(), ENTRIES = new AtomicLong();
    /** The probe's bits (RecipeIndexProbe.bits, set by RecipeIndexPlugin.postApply): one per probed class as shipped. */
    public static final int PROBE_MANAGER = 1, PROBE_COOKING = 2, PROBE_INGREDIENT = 4, PROBE_ALL = 7;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final VarHandle INGREDIENT;
    private static volatile boolean announced, warnedProbe;

    static {
        VarHandle v = null;
        try {
            v = MethodHandles.privateLookupIn(AbstractCookingRecipe.class, MethodHandles.lookup()).findVarHandle(AbstractCookingRecipe.class, "f_43729_", Ingredient.class);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_cooking_recipe_item_index cannot read a cooking recipe's ingredient ({}); recipe lookups run as shipped", t.toString());
        }
        INGREDIENT = v;
    }

    /** Implemented by RecipeManager through the layer's own mixin: the per-manager memo. */
    public interface Holder {
        Object bons$cookingIndex();

        void bons$cookingIndex(Object index);
    }

    /** One caller switch: its key, its runtime flag, its one INFO line. */
    public static final class Caller {
        public final String key;
        public final String flag;
        public volatile boolean enabled;
        volatile boolean announced;
        final String what;

        Caller(String key, String camelName, String what) {
            this.key = key;
            this.flag = "bons_and_furious." + camelName;
            this.enabled = !"false".equalsIgnoreCase(System.getProperty(this.flag, "true"));
            this.what = what;
        }
    }

    /** A caller switch of this layer (call once, from the caller's helper). */
    public static Caller caller(String key, String camelName, String what) {
        return new Caller(key, camelName, what);
    }

    /**
     * For callers in other groups, which reach this class through a MethodHandle (their group compiles on its own): the same as
     * {@link #caller}, typed Object. Call once (a static final field of the caller's helper).
     */
    public static Object register(String key, String camelName, String what) {
        return new Caller(key, camelName, what);
    }

    /** For callers in other groups: {@link #recipeFor} with the token {@link #register} returned. */
    @SuppressWarnings({"rawtypes"})
    public static Optional answer(Object caller, RecipeManager manager, RecipeType type, Container container, Level level, Operation<Optional> original) {
        if (!(caller instanceof Caller c)) return original.call(manager, type, container, level);
        return recipeFor(c, manager, type, container, level, original);
    }

    /** Whether a recipe class keeps AbstractCookingRecipe.matches (cached per class). */
    private static final ClassValue<Boolean> KEEPS_MATCHES = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("m_5818_", Container.class, Level.class).getDeclaringClass() == AbstractCookingRecipe.class;
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
        }
    };

    /** The memo of one manager: the token it was built under and one table per cooking type. */
    static final class ManagerIndex {
        final RecipeGenerationToken.Token token;
        final Map<Object, Map<Object, Object>> table;
        final ConcurrentHashMap<RecipeType<?>, TypeIndex> types = new ConcurrentHashMap<>();

        ManagerIndex(RecipeGenerationToken.Token token, Map<Object, Map<Object, Object>> table) {
            this.token = token;
            this.table = table;
        }
    }

    /** One type's recipes in table order, their provable flags and ingredients, and the per-item entries. */
    static final class TypeIndex {
        /** The table's map of this type (the same object getRecipeFor walks) and its size when the order was taken. */
        final Map<Object, Object> byType;
        final Recipe<?>[] order;
        final Ingredient[] ingredient;   // null where not provable
        final ConcurrentHashMap<Item, Entry> items = new ConcurrentHashMap<>();
        final int provable;

        TypeIndex(Map<Object, Object> byType) {
            this.byType = byType;
            List<Recipe<?>> list = new ArrayList<>(byType.size());
            for (Object r : byType.values()) list.add((Recipe<?>) r);
            this.order = list.toArray(new Recipe<?>[0]);
            this.ingredient = new Ingredient[this.order.length];
            int n = 0;
            for (int i = 0; i < this.order.length; i++) {
                Recipe<?> r = this.order[i];
                if (r instanceof AbstractCookingRecipe && KEEPS_MATCHES.get(r.getClass())) {
                    Ingredient ing = (Ingredient) INGREDIENT.get((AbstractCookingRecipe) r);
                    if (ing != null && ing.getClass() == Ingredient.class) {
                        this.ingredient[i] = ing;
                        n++;
                    }
                }
            }
            this.provable = n;
        }

        Entry entry(ItemStack input) {
            Item item = input.m_41720_();
            Entry e = this.items.get(item);
            if (e != null) return e;
            int answer = -1;
            List<Integer> others = null;
            for (int i = 0; i < this.order.length; i++) {
                Ingredient ing = this.ingredient[i];
                if (ing == null) {
                    if (others == null) others = new ArrayList<>(2);
                    others.add(i);
                } else if (ing.test(input)) {
                    answer = i;
                    break;
                }
            }
            int[] o = others == null ? EMPTY : others.stream().mapToInt(Integer::intValue).toArray();
            e = new Entry(o, answer < 0 ? null : this.order[answer]);
            Entry prev = this.items.putIfAbsent(item, e);
            if (prev != null) return prev;
            ENTRIES.incrementAndGet();
            return e;
        }
    }

    static final int[] EMPTY = new int[0];

    /** For one input item: the recipes that are not provable before its first provable match, and that match (or null). */
    record Entry(int[] others, Recipe<?> answer) {}

    private CookingRecipeIndex() {
    }

    /**
     * In place of {@code manager.getRecipeFor(type, container, level)} at a caller's call site (the caller's WrapOperation).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional recipeFor(Caller caller, RecipeManager manager, RecipeType type, Container container, Level level, Operation<Optional> original) {
        if (!enabled || !caller.enabled || INGREDIENT == null || !(manager instanceof Holder holder) || manager.getClass() != RecipeManager.class
                || container == null || type == null) {
            return original.call(manager, type, container, level);
        }
        if (RecipeIndexProbe.bits != PROBE_ALL) {
            if (!warnedProbe) {
                warnedProbe = true;
                LOGGER.info("Bons and Furious: vanilla_cooking_recipe_item_index steps aside: {}; recipe lookups run as shipped", probeWhy());
            }
            return original.call(manager, type, container, level);
        }
        TypeIndex ti = typeIndex(holder, manager, type);
        if (ti == null) return original.call(manager, type, container, level);
        ItemStack input = container.m_8020_(0);
        if (input == null) return original.call(manager, type, container, level);
        Entry e = ti.entry(input);
        Optional answer = null;
        for (int i : e.others()) {
            Recipe r = ti.order[i];
            if (r.m_5818_(container, level)) {
                answer = Optional.of(r);
                break;
            }
        }
        if (answer == null) answer = Optional.ofNullable(e.answer());
        ANSWERED.incrementAndGet();
        if (!caller.announced) announce(caller);
        if (SHADOW) {
            Optional fresh = original.call(manager, type, container, level);
            SHADOW_CHECKS.incrementAndGet();
            if (fresh.orElse(null) != answer.orElse(null) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: vanilla_cooking_recipe_item_index SHADOW MISMATCH {}: {} for {} found {}, the index {}", SHADOW_MISMATCHES.get(),
                        caller.key, input, fresh.orElse(null), answer.orElse(null));
            }
            return fresh;
        }
        return answer;
    }

    static TypeIndex typeIndex(Holder holder, RecipeManager manager, RecipeType<?> type) {
        Object memo = holder.bons$cookingIndex();
        ManagerIndex mi = memo instanceof ManagerIndex m && RecipeGenerationToken.current(manager, m.token) ? m : null;
        if (mi == null) {
            RecipeGenerationToken.Token token = RecipeGenerationToken.token(manager);
            Map<Object, Map<Object, Object>> table = RecipeGenerationToken.table(manager);
            if (token == null || table == null) return null;
            mi = new ManagerIndex(token, table);
            holder.bons$cookingIndex(mi);
        }
        TypeIndex ti = mi.types.get(type);
        // the server's per-type maps are immutable; the client's (replaceRecipes) are HashMaps nothing writes after: a size that
        // changed anyway means someone did, so the type's table is taken again
        if (ti != null && ti.byType.size() != ti.order.length) {
            mi.types.remove(type, ti);
            ti = null;
        }
        if (ti == null) {
            Map<Object, Object> byType = mi.table.getOrDefault(type, Collections.emptyMap());   // what byType (m_44054_) reads
            ti = new TypeIndex(byType);
            TypeIndex prev = mi.types.putIfAbsent(type, ti);
            if (prev != null) ti = prev;
            else TABLES.incrementAndGet();
        }
        return ti;
    }

    private static void announce(Caller c) {
        c.announced = true;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_cooking_recipe_item_index: one-slot cooking recipe lookups are answered from a first-match index per input item{}",
                    SHADOW ? " (SHADOW MODE: the walk runs every time and is compared)" : "");
        }
        LOGGER.info("Bons and Furious: {} applies ({})", c.key, c.what);
    }

    static String probeWhy() {
        int b = RecipeIndexProbe.bits;
        String probeNote = RecipeIndexProbe.note;
        if (b == 0) return "its probe did not run (the layer switch is off or its guard does not match)";
        List<String> why = new ArrayList<>();
        if ((b & PROBE_MANAGER) == 0) why.add("RecipeManager.getRecipeFor is changed by another mod");
        if ((b & PROBE_COOKING) == 0) why.add("AbstractCookingRecipe.matches is changed by another mod");
        if ((b & PROBE_INGREDIENT) == 0) why.add("Ingredient.test is changed by another mod");
        return String.join("; ", why) + (probeNote.isEmpty() ? "" : " (" + probeNote + ")");
    }

    /** For the harness: the probe verdict as the plugin would set it. */
    public static void probe(int bits, String note) {
        RecipeIndexProbe.bits = bits;
        RecipeIndexProbe.note = note == null ? "" : note;
    }
}
