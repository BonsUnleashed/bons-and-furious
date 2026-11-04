package bons.furious.patch.vanilla_cooking_recipe_index;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, vanilla_recipe_generation_token (helper of the vanilla_cooking_recipe_item_index layer; no switch of
 * its own): when may an exact recipe memo be reused? A memo built from a RecipeManager stays valid exactly while
 *   1. the manager's recipe table (RecipeManager.f_44007_) is the same object (RecipeManager.apply on every data pack load and
 *      replaceRecipes on the client build a new one; the table and its per-type maps are immutable),
 *   2. no tag was rebound: every tag binding of the game fires Forge's TagsUpdatedEvent (server data load, client tag packet),
 *      counted here by a HIGHEST-priority listener registered before the first memo is built; an ingredient that names a tag
 *      may answer from the live holder tags (ModernFix's faster_ingredients) or from its item array resolved after them,
 *   3. Forge's Ingredient.INVALIDATION_COUNTER is unchanged (Ingredient.invalidateAll on registry id remaps clears every
 *      ingredient's resolved items).
 * {@link #token} is a cheap snapshot of the three (one field read, two counter reads); a memo keeps the token it was built
 * under and is rebuilt when the token differs. No Minecraft or Forge code is carried.
 */
public final class RecipeGenerationToken {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final VarHandle RECIPES;
    private static final AtomicInteger INGREDIENT_COUNTER;
    private static final AtomicInteger TAGS = new AtomicInteger();
    private static volatile boolean listening, noListener;
    /** False when this helper cannot read what it needs (the index then never answers). */
    public static final boolean AVAILABLE;

    static {
        VarHandle recipes = null;
        AtomicInteger counter = null;
        try {
            recipes = MethodHandles.privateLookupIn(RecipeManager.class, MethodHandles.lookup()).findVarHandle(RecipeManager.class, "f_44007_", Map.class);
            Field f = Ingredient.class.getDeclaredField("INVALIDATION_COUNTER");
            f.setAccessible(true);
            counter = (AtomicInteger) f.get(null);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_cooking_recipe_item_index cannot read the recipe table or Forge's ingredient counter ({}); recipe lookups run as shipped", t.toString());
            recipes = null;
            counter = null;
        }
        RECIPES = recipes;
        INGREDIENT_COUNTER = counter;
        AVAILABLE = recipes != null && counter != null;
    }

    private RecipeGenerationToken() {
    }

    /** The token: recipe table identity, tag rebinds seen, Forge's ingredient counter. */
    public record Token(Object recipes, int tags, int ingredients) {
        public boolean same(Token o) {
            return o != null && o.recipes == this.recipes && o.tags == this.tags && o.ingredients == this.ingredients;
        }
    }

    /** The current token of a manager (null when unavailable). Registers the tag listener on first use. */
    public static Token token(RecipeManager manager) {
        if (!AVAILABLE) return null;
        if (!listening) listen();
        if (noListener) return null;   // tag rebinds could not be counted: keep no memo
        return new Token(RECIPES.get(manager), TAGS.get(), INGREDIENT_COUNTER.get());
    }

    /** Whether a token is still the current one (no allocation). */
    public static boolean current(RecipeManager manager, Token t) {
        return t != null && RECIPES.get(manager) == t.recipes() && TAGS.get() == t.tags() && INGREDIENT_COUNTER.get() == t.ingredients();
    }

    /**
     * For memos in other groups, which reach this class through a MethodHandle: the current token typed Object (null when
     * unavailable: then keep no memo).
     */
    public static Object tokenOf(RecipeManager manager) {
        return token(manager);
    }

    /** For memos in other groups: whether a token from {@link #tokenOf} is still the current one (false for null). */
    public static boolean stillCurrent(RecipeManager manager, Object token) {
        return token instanceof Token t && current(manager, t);
    }

    /** The manager's recipe table (for building a memo under the token just taken). */
    @SuppressWarnings("unchecked")
    public static Map<Object, Map<Object, Object>> table(RecipeManager manager) {
        return (Map<Object, Map<Object, Object>>) RECIPES.get(manager);
    }

    private static synchronized void listen() {
        if (listening) return;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TagsUpdatedEvent.class, e -> TAGS.incrementAndGet());
        } catch (Throwable t) {
            noListener = true;
            LOGGER.warn("Bons and Furious: vanilla_cooking_recipe_item_index cannot count tag rebinds ({}); recipe lookups run as shipped", t.toString());
        }
        listening = true;
    }

    /** For the harness: a tag rebind as TagsUpdatedEvent reports it. */
    public static void tagsRebound() {
        TAGS.incrementAndGet();
    }
}
