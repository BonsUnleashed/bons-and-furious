package bons.furious.patch.mysticalagriculture_coo49;

import bons.furious.mixin.mysticalagriculture_coo49.RecipeManagerProbeMixin;
import com.blakebr0.mysticalagriculture.crafting.recipe.SoulExtractionRecipe;
import com.google.common.collect.Multimap;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch mysticalagriculture_soul_extractor_idle_skip (Mystical Agriculture with Cucumber; 1.21.1
 * tested build: Mystical Agriculture 1.21.1-8.0.29 with Cucumber 1.21.1-8.0.16, server; kind opt, default on; helper of
 * SoulRecipeScanMixin; no Mystical Agriculture or Cucumber code carried).
 *
 * What the mods do. Every tick a Soul Extractor asks Cucumber's CachedRecipe for its recipe with its three slots as one
 * ShapelessCraftingInput: the remembered recipe is tested first, and when it does not match (always, while the input slot is
 * empty) RecipeManager.getRecipeFor scans every soul extraction recipe (unless the whole input is empty: then vanilla answers
 * nothing at once): per recipe the matches bridge, a size check and an Ingredient.test of the input slot. An idle extractor
 * (an empty input slot beside fuel or a jar) runs that scan every tick, finds nothing and stores null.
 *
 * What the switch does. Right before that look-up (inside CachedRecipe.check, after the remembered recipe was tested as the
 * mod does) the check answers false and forgets the remembered recipe, which is exactly what the look-up's empty answer
 * makes it do, without the look-up (the level's getRecipeManager, a getter, is then not called either), but only when the
 * scan certainly finds nothing:
 *  - the recipe type is Mystical Agriculture's soul extraction type and the recipe manager is RecipeManager itself;
 *  - both getRecipeFor overloads of the look-up, its filter, byType, Ingredient.test / getItems / isEmpty and
 *    SoulExtractionRecipe's matches, its bridge and getIngredients are as shipped after every other mod's mixins (MaProbe; any
 *    change, for example a recipe-selection mod hooking getRecipeFor or an ingredient speed-up, makes the switch step aside
 *    with one INFO line);
 *  - for the recipe set loaded right now (the manager's byType multimap: immutable, a new one on every data pack load and
 *    every client recipe sync) every recipe of that type is exactly SoulExtractionRecipe, whose matches answers false for an
 *    input smaller than three slots and otherwise when its input ingredient rejects slot 0, and every such ingredient is a
 *    plain Ingredient (exactly that class, no NeoForge custom ingredient) that rejects an empty stack (checked once per
 *    recipe set: an ingredient whose items include air keeps the scan; a refused set is looked at again every 64th call
 *    only, since keeping the scan is always exact). A plain ingredient resolves its items once and keeps them (NeoForge
 *    21.1 has no ingredient invalidation), so its answer for an empty stack cannot change while its recipe set is loaded;
 *  - the input is a CraftingInput (what the recipes' matches bridge casts to) and its slot 0 (what each recipe reads first,
 *    read once here) is empty; with no soul recipe at all the scan finds nothing either.
 * The checks run cheapest refusal first (type, probe verdict, a refused set, slot 0); only then is the level asked for its
 * recipe manager (the same getter the mod calls next: when the switch does not answer, the mod's own call follows).
 * Runtime flag bons_and_furious.mysticalagricultureSoulExtractorIdleSkip; SHADOW MODE
 * -Dbons_and_furious.mysticalagricultureSoulExtractorIdleSkip.shadow=true runs the same look-up where the switch would
 * answer, lets the mod's own look-up run as well, and counts SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for the first 20).
 */
public final class SoulExtractorIdle {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mysticalagricultureSoulExtractorIdleSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mysticalagricultureSoulExtractorIdleSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SKIPPED = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final ResourceLocation SOUL_EXTRACTION = ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "soul_extraction");
    private static volatile Proof proof;
    private static volatile RecipeType<?> soulType;
    private static volatile boolean announced, steppedAside;

    /**
     * One recipe set's verdict for one manager and type: how many recipes, or why the scan must run (null = proven). The
     * manager and its multimap are held weakly, so a verdict never keeps a closed world's recipes.
     */
    record Proof(WeakReference<RecipeManager> manager, WeakReference<Object> table, RecipeType<?> type, int recipes, String why) {}

    private SoulExtractorIdle() {
    }

    /**
     * Right before CachedRecipe.check's {@code level.getRecipeManager().getRecipeFor(type, input, level)} (type already
     * compared with the soul extraction type by the caller): true when that look-up certainly answers Optional.empty() with
     * no other effect (see the class comment), false = let the mod make it.
     */
    public static boolean none(Level level, RecipeType<?> type, RecipeInput input) {
        if (type == null || input == null || level == null) return false;
        if (MaProbe.bits != MaProbe.PROBE_ALL) {
            if (!steppedAside) stepAside(MaProbe.why());   // the reason is put together once, for the one INFO line
            return false;
        }
        // a recipe set already refused keeps the scan; it is looked at again only every 64th call (running the scan is
        // always what the mod does, so a set that became provable meanwhile only loses a few skips)
        Proof known = proof;
        if (known != null && known.why() != null && known.type() == type && (++retry & 63) != 0) return false;
        // slot 0: the read every soul recipe makes first (after its size check); a filled input leaves here; an input the
        // recipes' bridge would not take, or whose read throws, keeps the scan, which then behaves as shipped
        if (!(input instanceof CraftingInput craft)) return false;
        ItemStack first;
        try {
            if (craft.size() < 1) return false;
            first = craft.getItem(0);
        } catch (RuntimeException e) {
            return false;
        }
        if (first == null || !first.isEmpty()) return false;
        RecipeManager manager;
        try {
            manager = level.getRecipeManager();   // the getter the mod calls next
        } catch (RuntimeException e) {
            return false;
        }
        if (manager == null || manager.getClass() != RecipeManager.class) return false;
        Proof p = proof(manager, type);
        if (p == null || p.why() != null) return false;
        if (!announced) announce(p);
        SKIPPED.incrementAndGet();
        return true;
    }

    /** Shadow mode: the scan's own answer, compared with the empty answer the switch would have given. */
    @SuppressWarnings("rawtypes")
    public static Optional shadow(Optional fresh) {
        long n = SHADOW_CHECKS.incrementAndGet();
        if (fresh != null && fresh.isPresent() && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: mysticalagriculture_soul_extractor_idle_skip SHADOW MISMATCH {} (check {}): the scan found {}", SHADOW_MISMATCHES.get(), n, fresh.get());
        return fresh;
    }

    /** Mystical Agriculture's soul extraction recipe type (resolved on first use; null while it is not registered yet). */
    public static RecipeType<?> soulType() {
        RecipeType<?> t = soulType;
        return t != null ? t : resolveSoulType();
    }

    private static RecipeType<?> resolveSoulType() {
        RecipeType<?> t;
        try {
            t = BuiltInRegistries.RECIPE_TYPE.get(SOUL_EXTRACTION);
        } catch (Throwable e) {
            return null;
        }
        if (t != null) soulType = t;
        return t;
    }

    /** Calls since a refused verdict was last checked against the current recipe set (racy on purpose: only a pace). */
    private static int retry;

    static Proof proof(RecipeManager manager, RecipeType<?> type) {
        Object table;
        try {
            table = ((RecipeManagerProbeMixin) manager).bons$mysticalagricultureByType();
        } catch (Throwable t) {
            return null;
        }
        if (table == null) return null;
        Proof p = proof;
        if (p != null && p.type() == type && p.manager().get() == manager && p.table().get() == table) return p;
        String why = null;
        int n = 0;
        try {
            @SuppressWarnings("unchecked")
            Collection<?> holders = ((Multimap<Object, Object>) table).get(type);   // what byType(type) reads
            for (Object h : holders) {
                Object r = h instanceof RecipeHolder<?> holder ? holder.value() : null;
                if (r == null || r.getClass() != SoulExtractionRecipe.class) {
                    why = "a soul extraction recipe of another class (" + (r == null ? String.valueOf(h) : r.getClass().getName()) + ")";
                    break;
                }
                List<Ingredient> inputs = ((SoulExtractionRecipe) r).getIngredients();
                Ingredient in = inputs == null || inputs.isEmpty() ? null : inputs.get(0);
                if (in == null || in.getClass() != Ingredient.class || in.isCustom()) {
                    why = "an input ingredient of another kind (" + (in == null ? "none" : in.isCustom() ? "custom " + in.getCustomIngredient().getClass().getName() : in.getClass().getName()) + ")";
                    break;
                }
                if (in.test(ItemStack.EMPTY)) {
                    why = "an input ingredient accepts an empty slot";
                    break;
                }
                n++;
            }
        } catch (Throwable t) {
            why = "the recipes could not be read (" + t + ")";
        }
        p = new Proof(new WeakReference<>(manager), new WeakReference<>(table), type, n, why);
        proof = p;
        return p;
    }

    private static void stepAside(String why) {
        if (steppedAside) return;
        steppedAside = true;
        LOGGER.info("Bons and Furious: mysticalagriculture_soul_extractor_idle_skip steps aside: {}; the Soul Extractor's recipe look-up runs as shipped", why);
    }

    private static void announce(Proof p) {
        announced = true;
        LOGGER.info("Bons and Furious: mysticalagriculture_soul_extractor_idle_skip applies (an idle Soul Extractor finds no recipe without scanning the {} soul extraction recipes, none of which accepts an empty input){}",
                p.recipes(), SHADOW ? " (SHADOW MODE: the scan runs every time and is compared)" : "");
    }

    /** For the harness: forget the kept verdict and the resolved type. */
    public static void reset() {
        proof = null;
        soulType = null;
    }
}
