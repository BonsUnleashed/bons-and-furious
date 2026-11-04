package bons.furious.patch.bloodmagic_coo49;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches bloodmagic_arc_furnace_recipe_memo and bloodmagic_arc_recipe_list_memo (Blood Magic 3.3.8-50,
 * CC BY 4.0; both sides; exact). Ours; no Blood Magic code is carried. Idea: Collections Of Optimizations
 * (bloodmagic.cacheArcFurnaceRecipe, bloodmagic.cacheArcRecipeList), idea text only.
 *
 * What the mod does. TileAlchemicalReactionChamber.tick (both sides, every tick of every chamber) asks
 * BloodMagicRecipeRegistrar.getARC(level, input, tool, fluid) when both slots hold something: getARC asks
 * level.getRecipeManager().getAllRecipesFor(ARC) (List.copyOf of every ARC recipe, about a hundred) and walks it. With a
 * furnace tool in and no ARC recipe for the input, the tick then asks getRecipeFor(SMELTING, a one-slot copy of the input,
 * level): a first-match walk over every smelting recipe of the pack (the empty input included), whose answer the tick only
 * unwraps (isPresent / get / assemble).
 *
 * bloodmagic_arc_furnace_recipe_memo (BmArcFurnaceRecipeMixin): the getRecipeFor call goes to the vanilla_cooking_recipe_item_index
 * layer (CookingRecipeIndex.answer of the vanilla_cooking_recipe_index group, reached by name), which answers the same recipe
 * object from its first-match index per input item while its probe (getRecipeFor, its filter, byType, cooking matches and
 * Ingredient.test as shipped) and its recipe generation token allow, and otherwise makes the call. The container is Blood
 * Magic's InventoryWrapper(1) holding the copy in slot 0, the input the layer reads. Exact (the layer's proof); the Optional
 * is a new object as before. The layer steps aside whole where Polymorph, Kiwi or BCLib change getRecipeFor.
 *
 * bloodmagic_arc_recipe_list_memo (BmArcRecipeListMixin): on a server level (getARC's level argument) the list getARC gets
 * is the one the original built for the current recipe table, handed out again while (1) the recipe generation token of the
 * same layer (RecipeGenerationToken, by name: the same recipe table object, no tag rebind, Forge's ingredient counter
 * unchanged) is current, (2) the table's map for ARC is the same object and an immutable map (a Guava ImmutableMap, as
 * RecipeManager.apply builds every server table, or the shared empty map), (3) the manager is RecipeManager itself, and (4)
 * BmProbe saw getAllRecipesFor and byType as shipped. Then a fresh call would return List.copyOf of the same values in the
 * same order: an equal immutable list. getARC only iterates the list (bytecode), so handing out the same immutable list is
 * exact. A recipe table that is not an ImmutableMap (replaceRecipes, a mod that writes its own tables) runs as shipped after
 * one type test; an immutable table whose ARC map is mutable is remembered as such and, while its token is current, runs as
 * shipped too. Client levels always run as shipped: their tables come from replaceRecipes with HashMaps, where a check that
 * the map is unchanged costs as much as the copy. Memory: at most two kept lists, the manager held weakly.
 *
 * Runtime flags: -Dbons_and_furious.bloodmagicArcFurnaceRecipeMemo=false (the layer's caller flag too),
 * -Dbons_and_furious.bloodmagicArcRecipeListMemo=false. SHADOW MODE (rigs):
 * -Dbons_and_furious.bloodmagicArcRecipeListMemo.shadow=true makes the original call on every hit as well and compares the two
 * lists element by element (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20); the original's list is returned. The
 * furnace key's shadow is the layer's (-Dbons_and_furious.vanillaCookingRecipeItemIndex.shadow=true).
 */
public final class BmRecipes {
    public static final String FURNACE_KEY = "bloodmagic_arc_furnace_recipe_memo", LIST_KEY = "bloodmagic_arc_recipe_list_memo";
    public static volatile boolean furnaceEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.bloodmagicArcFurnaceRecipeMemo", "true"));
    public static volatile boolean listEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.bloodmagicArcRecipeListMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.bloodmagicArcRecipeListMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields): list memo hits and builds. */
    public static long listHits, listBuilds;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String INDEX = "bons.furious.patch.vanilla_cooking_recipe_index.CookingRecipeIndex";
    private static final String TOKEN = "bons.furious.patch.vanilla_cooking_recipe_index.RecipeGenerationToken";
    private static final MethodHandle ANSWER, TOKEN_OF, STILL_CURRENT, TABLE;
    private static final Object CALLER;
    private static volatile boolean announcedList, warnedProbe;

    static {
        MethodHandle answer = null, tokenOf = null, stillCurrent = null, table = null;
        Object caller = null;
        MethodHandles.Lookup l = MethodHandles.lookup();
        try {
            Class<?> index = Class.forName(INDEX, false, BmRecipes.class.getClassLoader());
            MethodHandle register = l.findStatic(index, "register", MethodType.methodType(Object.class, String.class, String.class, String.class));
            answer = l.findStatic(index, "answer", MethodType.methodType(Optional.class, Object.class, RecipeManager.class, RecipeType.class, Container.class,
                    Level.class, Operation.class));
            caller = (Object) register.invokeExact(FURNACE_KEY, "bloodmagicArcFurnaceRecipeMemo",
                    "Blood Magic's alchemical reaction chamber finds its furnace-mode smelting recipe from the index");
        } catch (Throwable t) {
            answer = null;
            caller = null;
            LOGGER.warn("Bons and Furious: {} cannot reach the cooking recipe index ({}); the furnace-mode recipe lookup runs as shipped", FURNACE_KEY, t.toString());
        }
        try {
            Class<?> token = Class.forName(TOKEN, false, BmRecipes.class.getClassLoader());
            tokenOf = l.findStatic(token, "tokenOf", MethodType.methodType(Object.class, RecipeManager.class));
            stillCurrent = l.findStatic(token, "stillCurrent", MethodType.methodType(boolean.class, RecipeManager.class, Object.class));
            table = l.findStatic(token, "table", MethodType.methodType(Map.class, RecipeManager.class));
        } catch (Throwable t) {
            tokenOf = null;
            stillCurrent = null;
            table = null;
            LOGGER.warn("Bons and Furious: {} cannot reach the recipe generation token ({}); getARC asks for the recipe list as shipped", LIST_KEY, t.toString());
        }
        ANSWER = answer;
        CALLER = caller;
        TOKEN_OF = tokenOf;
        STILL_CURRENT = stillCurrent;
        TABLE = table;
    }

    private BmRecipes() {
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException rethrow(Throwable t) throws T {
        throw (T) t;
    }

    // ------------------------------------------------------------------------------------------------ furnace mode

    /** level.getRecipeManager().getRecipeFor(SMELTING, invWrapper, level) in the chamber's tick, wrapped. */
    @SuppressWarnings({"rawtypes"})
    public static Optional furnace(RecipeManager manager, RecipeType type, Container container, Level level, Operation<Optional> original) {
        if (!furnaceEnabled || ANSWER == null || CALLER == null) return original.call(manager, type, container, level);
        try {
            return (Optional) ANSWER.invokeExact(CALLER, manager, type, container, level, (Operation) original);
        } catch (Throwable t) {
            throw rethrow(t);   // the original's own exceptions pass through unchanged (the layer made the call)
        }
    }

    // ------------------------------------------------------------------------------------------------ ARC recipe list

    /**
     * One kept entry: the manager (weakly), the type, the token it was built under, the type's map then, and the list (null
     * for a table whose map is mutable: no memo for it, every call runs as shipped).
     */
    record Kept(WeakReference<RecipeManager> manager, Object type, Object token, Map<?, ?> inner, List<?> list) {
    }

    private static volatile Kept slot0, slot1;
    private static volatile int nextSlot;

    private static Object tokenOf(RecipeManager m) {
        try {
            return (Object) TOKEN_OF.invokeExact(m);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean current(RecipeManager m, Object token) {
        try {
            return token != null && (boolean) STILL_CURRENT.invokeExact(m, token);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The manager's recipe table (RecipeManager.recipes), or null. */
    private static Map<?, ?> outer(RecipeManager m) {
        try {
            return (Map<?, ?>) TABLE.invokeExact(m);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The table's map of the type, what byType returns (getOrDefault with the shared empty map); null for no table. */
    private static Map<?, ?> innerOf(Map<?, ?> table, Object type) {
        if (table == null) return null;
        Object inner = table.get(type);
        return inner == null ? Collections.emptyMap() : (Map<?, ?>) inner;
    }

    /** True when the map's values are, in iteration order, exactly (identity) the list's elements. */
    static boolean sameOrder(Map<?, ?> inner, List<?> list) {
        if (inner.size() != list.size()) return false;
        Iterator<?> a = inner.values().iterator();
        for (Object x : list) {
            if (!a.hasNext() || a.next() != x) return false;
        }
        return !a.hasNext();
    }

    private static Kept find(RecipeManager manager, Object type) {
        Kept k = slot0;
        if (k != null && k.manager().get() == manager && k.type() == type) return k;
        k = slot1;
        if (k != null && k.manager().get() == manager && k.type() == type) return k;
        return null;
    }

    private static void keep(RecipeManager manager, Kept k) {
        Kept a = slot0;
        if (a == null || a.manager().get() == manager || a.manager().get() == null) {
            slot0 = k;
            return;
        }
        Kept b = slot1;
        if (b == null || b.manager().get() == manager || b.manager().get() == null) {
            slot1 = k;
            return;
        }
        if ((nextSlot++ & 1) == 0) slot0 = k;
        else slot1 = k;
    }

    static boolean immutable(Map<?, ?> inner) {
        return inner instanceof ImmutableMap || inner == Collections.emptyMap();
    }

    /** level.getRecipeManager().getAllRecipesFor(ARC) in getARC(world, ...), redirected: every "as shipped" is that same call. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List arcList(RecipeManager manager, RecipeType type, Level world) {
        if (!listEnabled || world == null || world.f_46443_ || manager == null || type == null || TOKEN_OF == null || manager.getClass() != RecipeManager.class)
            return manager.m_44013_(type);
        Map<?, ?> table = outer(manager);
        // a table that is not RecipeManager.apply's ImmutableMap (replaceRecipes, a mod's own) runs as shipped: one type test
        if (!(table instanceof ImmutableMap)) return manager.m_44013_(type);
        if (!BmProbe.ok()) {
            if (!warnedProbe) {
                warnedProbe = true;
                LOGGER.info("Bons and Furious: {} steps aside: {}; getARC asks for the recipe list as shipped", LIST_KEY, BmProbe.note());
            }
            return manager.m_44013_(type);
        }
        Kept k = find(manager, type);
        if (k != null && current(manager, k.token())) {
            if (k.list() == null) return manager.m_44013_(type);   // a mutable map under this token: run as shipped
            Map<?, ?> now = innerOf(table, type);
            if (now != null && now == k.inner()) {
                if (SHADOW) {
                    List asked = manager.m_44013_(type);
                    SHADOW_CHECKS.incrementAndGet();
                    if (!sameElements(asked, k.list()) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                        LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH: kept {} recipes, asked {}", LIST_KEY, k.list().size(), asked.size());
                    return asked;
                }
                listHits++;
                return k.list();
            }
        }
        Object token = tokenOf(manager);   // taken before the list is built
        List list = manager.m_44013_(type);
        listBuilds++;
        if (token != null && list != null) {
            Map<?, ?> inner = innerOf(outer(manager), type);   // the table as it is after the call
            if (inner != null) {
                boolean kept = immutable(inner) && sameOrder(inner, list);
                keep(manager, new Kept(new WeakReference<>(manager), type, token, inner, kept ? list : null));
                if (kept && !announcedList) {
                    announcedList = true;
                    LOGGER.info("Bons and Furious: {} applies (Blood Magic's ARC recipe list is built once per recipe table)", LIST_KEY);
                }
            }
        }
        return list;
    }

    static boolean sameElements(List<?> a, List<?> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    /** For the harness: forget the kept lists. */
    public static void forget() {
        slot0 = null;
        slot1 = null;
    }
}
