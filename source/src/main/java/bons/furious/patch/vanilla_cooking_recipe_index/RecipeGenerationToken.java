package bons.furious.patch.vanilla_cooking_recipe_index;

import com.google.common.collect.Multimap;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, vanilla_recipe_generation_token (helper of the vanilla_cooking_recipe_item_index layer; no switch of
 * its own; Minecraft 1.21.1 with NeoForge 21.1.252): when may an exact recipe memo be reused? A memo built from a
 * RecipeManager stays valid exactly while
 *   1. the manager's recipe table (RecipeManager.byType) is the same object: RecipeManager.apply on every data pack load
 *      and replaceRecipes on the client build a new ImmutableMultimap and assign it; the table and its per-type lists
 *      cannot change after that;
 *   2. no tag was rebound: every tag binding of the game posts NeoForge's TagsUpdatedEvent (the server's
 *      ReloadableServerResources.updateRegistryTags, the client's TagCollector.updateTags), counted here by a
 *      HIGHEST-priority listener registered before the first memo is built. An ingredient keeps the items it resolved the
 *      first time (Ingredient.getItems), so a rebind changes no answer the memo already holds; the count makes every memo
 *      start over anyway, as on Forge.
 * {@link #token} is a cheap snapshot of the two (one field read, one counter read); a memo keeps the token it was built
 * under and is rebuilt when the token differs. No Minecraft or NeoForge code is carried.
 */
public final class RecipeGenerationToken {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final VarHandle RECIPES;
    private static final AtomicInteger TAGS = new AtomicInteger();
    private static volatile boolean listening, noListener;
    /** False when this helper cannot read what it needs (the index then never answers). */
    public static final boolean AVAILABLE;

    static {
        VarHandle recipes = null;
        try {
            recipes = MethodHandles.privateLookupIn(RecipeManager.class, MethodHandles.lookup()).findVarHandle(RecipeManager.class, "byType", Multimap.class);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_cooking_recipe_item_index cannot read the recipe table ({}); recipe lookups run as shipped", t.toString());
            recipes = null;
        }
        RECIPES = recipes;
        AVAILABLE = recipes != null;
    }

    private RecipeGenerationToken() {
    }

    /** The token: recipe table identity, tag rebinds seen. */
    public record Token(Object recipes, int tags) {
        public boolean same(Token o) {
            return o != null && o.recipes == this.recipes && o.tags == this.tags;
        }
    }

    /** The current token of a manager (null when unavailable). Registers the tag listener on first use. */
    public static Token token(RecipeManager manager) {
        if (!AVAILABLE) return null;
        if (!listening) listen();
        if (noListener) return null;   // tag rebinds could not be counted: keep no memo
        return new Token(RECIPES.get(manager), TAGS.get());
    }

    /** Whether a token is still the current one (no allocation). */
    public static boolean current(RecipeManager manager, Token t) {
        return t != null && RECIPES.get(manager) == t.recipes() && TAGS.get() == t.tags();
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

    /** The manager's recipe table, RecipeManager.byType (for building a memo; prefer the table of the token just taken). */
    @SuppressWarnings("unchecked")
    public static Multimap<RecipeType<?>, RecipeHolder<?>> table(RecipeManager manager) {
        return (Multimap<RecipeType<?>, RecipeHolder<?>>) RECIPES.get(manager);
    }

    private static synchronized void listen() {
        if (listening) return;
        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TagsUpdatedEvent.class, e -> TAGS.incrementAndGet());
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
