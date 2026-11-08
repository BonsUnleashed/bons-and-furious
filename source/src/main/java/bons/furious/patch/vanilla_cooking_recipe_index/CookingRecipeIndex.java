package bons.furious.patch.vanilla_cooking_recipe_index;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, layer vanilla_cooking_recipe_item_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): an
 * exact first-match index for the cooking recipe lookups of mods that ask RecipeManager.getRecipeFor(type, input, level) with
 * a one-slot input (SingleRecipeInput). No Minecraft, NeoForge or mod code is carried.
 *
 * What the game does. getRecipeFor(type, input, level) is getRecipeFor(type, input, level, (RecipeHolder) null): an empty
 * input answers empty at once; otherwise byType(type), the type's list in the recipe table (RecipeManager.byType, an
 * ImmutableMultimap that apply and replaceRecipes build anew), is streamed in table order and each holder is asked
 * holder.value().matches(input, level) until one does (findFirst). With ~1,000 smelting recipes in a large pack that is tens
 * of microseconds per call.
 *
 * What the layer does. Per RecipeManager (a field the layer's own mixin adds) and recipe type, a table of the type's holders
 * in that same order, each marked PROVABLE when its recipe's class keeps AbstractCookingRecipe.matches (the SingleRecipeInput
 * method, which is ingredient.test(input.item()), and its RecipeInput bridge) and its ingredient is not one of NeoForge's
 * custom ingredients (the customIngredient field, which test() reads right after its null test, is null). For a provable
 * recipe the answer is a function of the input's item alone: Ingredient.test is then "null -> false; empty ingredient ->
 * input empty; else an item of getItems() is the input's item", getItems() is resolved once and kept for the ingredient's
 * life, and getItem() is AIR exactly for an empty stack. Per input item the layer keeps the first provable recipe that
 * matches it and the recipes that are not provable before it, in order. A lookup asks those others matches(input, level) in
 * order and answers the first that does, else that first provable match, else empty: the holder the walk finds.
 *
 * When it applies. Only while (1) the layer's probe found RecipeManager.getRecipeFor (both forms), the filter lambda, the
 * table lookup byType, AbstractCookingRecipe.matches (both) and Ingredient.test exactly as shipped after every other mod's
 * mixins (a change to the lookup, which can answer differently per player or context, turns the whole layer off, and so
 * does any change to the ingredient test, ModernFix 5.27.24's faster_ingredients included: it answers a single-tag
 * ingredient from the item's live tags while no server reload runs), (2) the manager is RecipeManager itself, not a
 * subclass, and its recipe table is an ImmutableMultimap, (3) the input is a SingleRecipeInput whose stack is not empty,
 * and (4) the memo's vanilla_recipe_generation_token is current (same recipe table, no tag rebind since). Otherwise the
 * call runs as shipped, and so does a lookup whose index entry cannot be built (an ingredient test that throws).
 *
 * Exact: the same holder (or none) for every call; the Optional is a new object as before (the callers only unwrap it).
 * What no longer runs per call: matches() of the provable recipes before the answer (pure reads of their ingredient's item
 * array).
 *
 * Callers: each is its own switch with a Caller from {@link #caller} and one MixinExtras @WrapOperation of its
 * RecipeManager.getRecipeFor(RecipeType, RecipeInput, Level) call that hands the call to {@link #recipeFor}.
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
    /** AbstractCookingRecipe.ingredient and Ingredient.customIngredient; INGREDIENT is null unless both could be read. */
    private static final VarHandle INGREDIENT, CUSTOM;
    private static volatile boolean announced, warnedProbe;

    static {
        VarHandle v = null, c = null;
        try {
            v = MethodHandles.privateLookupIn(AbstractCookingRecipe.class, MethodHandles.lookup()).findVarHandle(AbstractCookingRecipe.class, "ingredient", Ingredient.class);
            c = MethodHandles.privateLookupIn(Ingredient.class, MethodHandles.lookup()).findVarHandle(Ingredient.class, "customIngredient", ICustomIngredient.class);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_cooking_recipe_item_index cannot read a cooking recipe's ingredient ({}); recipe lookups run as shipped", t.toString());
            v = null;
            c = null;
        }
        INGREDIENT = v;
        CUSTOM = c;
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

    /** A caller switch of this layer (call once, from the caller's helper: a static final field). */
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
    public static Optional answer(Object caller, RecipeManager manager, RecipeType type, RecipeInput input, Level level, Operation<Optional> original) {
        if (!(caller instanceof Caller c)) return original.call(manager, type, input, level);
        return recipeFor(c, manager, type, input, level, original);
    }

    /** Whether a recipe class keeps AbstractCookingRecipe.matches, the SingleRecipeInput method and its bridge (cached per class). */
    private static final ClassValue<Boolean> KEEPS_MATCHES = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("matches", SingleRecipeInput.class, Level.class).getDeclaringClass() == AbstractCookingRecipe.class
                        && type.getMethod("matches", RecipeInput.class, Level.class).getDeclaringClass() == AbstractCookingRecipe.class;
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
        }
    };

    /** The memo of one manager: the token it was built under (whose table it reads) and one table per recipe type. */
    static final class ManagerIndex {
        final RecipeGenerationToken.Token token;
        final Multimap<RecipeType<?>, RecipeHolder<?>> table;
        final ConcurrentHashMap<RecipeType<?>, TypeIndex> types = new ConcurrentHashMap<>();

        ManagerIndex(RecipeGenerationToken.Token token, Multimap<RecipeType<?>, RecipeHolder<?>> table) {
            this.token = token;
            this.table = table;
        }
    }

    /** One type's holders in table order, their provable ingredients, and the per-item entries. */
    static final class TypeIndex {
        final RecipeHolder<?>[] order;
        final Ingredient[] ingredient;   // null where not provable
        final ConcurrentHashMap<Item, Entry> items = new ConcurrentHashMap<>();
        final int provable;

        TypeIndex(Collection<RecipeHolder<?>> holders) {
            this.order = holders.toArray(new RecipeHolder<?>[0]);
            this.ingredient = new Ingredient[this.order.length];
            int n = 0;
            for (int i = 0; i < this.order.length; i++) {
                RecipeHolder<?> h = this.order[i];
                Recipe<?> r = h == null ? null : h.value();
                if (r instanceof AbstractCookingRecipe cooking && KEEPS_MATCHES.get(r.getClass())) {
                    Ingredient ing = (Ingredient) INGREDIENT.get(cooking);
                    if (ing != null && (ICustomIngredient) CUSTOM.get(ing) == null) {
                        this.ingredient[i] = ing;
                        n++;
                    }
                }
            }
            this.provable = n;
        }

        Entry entry(ItemStack input) {
            Item item = input.getItem();
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
    record Entry(int[] others, RecipeHolder<?> answer) {}

    private CookingRecipeIndex() {
    }

    /**
     * In place of {@code manager.getRecipeFor(type, input, level)} at a caller's call site (the caller's WrapOperation, whose
     * Operation is called with exactly those four arguments when the index does not answer).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional recipeFor(Caller caller, RecipeManager manager, RecipeType type, RecipeInput input, Level level, Operation<Optional> original) {
        if (!enabled || caller == null || !caller.enabled || INGREDIENT == null || !(manager instanceof Holder holder)
                || manager.getClass() != RecipeManager.class || type == null || input == null || input.getClass() != SingleRecipeInput.class) {
            return original.call(manager, type, input, level);
        }
        if (RecipeIndexProbe.bits != PROBE_ALL) {
            if (!warnedProbe) {
                warnedProbe = true;
                LOGGER.info("Bons and Furious: vanilla_cooking_recipe_item_index steps aside: {}; recipe lookups run as shipped", probeWhy());
            }
            return original.call(manager, type, input, level);
        }
        ItemStack stack = ((SingleRecipeInput) input).item();
        if (stack == null || input.isEmpty()) return original.call(manager, type, input, level);
        TypeIndex ti = typeIndex(holder, manager, type);
        if (ti == null) return original.call(manager, type, input, level);
        Entry e;
        try {
            e = ti.entry(stack);
        } catch (RuntimeException ex) {
            return original.call(manager, type, input, level);
        }
        Optional answer = null;
        for (int i : e.others()) {
            RecipeHolder h = ti.order[i];
            Recipe r = h.value();
            if (r.matches(input, level)) {
                answer = Optional.of(h);
                break;
            }
        }
        if (answer == null) answer = Optional.ofNullable(e.answer());
        ANSWERED.incrementAndGet();
        if (!caller.announced) announce(caller);
        if (SHADOW) {
            Optional fresh = original.call(manager, type, input, level);
            SHADOW_CHECKS.incrementAndGet();
            if (fresh.orElse(null) != answer.orElse(null) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: vanilla_cooking_recipe_item_index SHADOW MISMATCH {}: {} for {} found {}, the index {}", SHADOW_MISMATCHES.get(),
                        caller.key, stack, fresh.orElse(null), answer.orElse(null));
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
            Object recipes = token == null ? null : token.recipes();
            // apply and replaceRecipes always build an ImmutableMultimap; any other table belongs to another mod
            if (!(recipes instanceof ImmutableMultimap)) return null;
            @SuppressWarnings("unchecked")
            Multimap<RecipeType<?>, RecipeHolder<?>> table = (Multimap<RecipeType<?>, RecipeHolder<?>>) recipes;
            mi = new ManagerIndex(token, table);
            holder.bons$cookingIndex(mi);
        }
        TypeIndex ti = mi.types.get(type);
        if (ti == null) {
            try {
                ti = new TypeIndex(mi.table.get(type));   // the collection byType returns, in the order the walk streams it
            } catch (RuntimeException e) {
                return null;
            }
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
        if (b == 0 && probeNote.isEmpty()) return "its probe did not run (the layer switch is off or its guard does not match)";
        List<String> why = new ArrayList<>();
        if ((b & PROBE_MANAGER) == 0) why.add("RecipeManager.getRecipeFor is not as shipped");
        if ((b & PROBE_COOKING) == 0) why.add("AbstractCookingRecipe.matches is not as shipped");
        if ((b & PROBE_INGREDIENT) == 0) why.add("Ingredient.test is not as shipped");
        return String.join("; ", why) + (probeNote.isEmpty() ? "" : " (" + probeNote + ")");
    }

    /** For the harness: the probe verdict as the plugin would set it. */
    public static void probe(int bits, String note) {
        RecipeIndexProbe.bits = bits;
        RecipeIndexProbe.note = note == null ? "" : note;
    }
}
