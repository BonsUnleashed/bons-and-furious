package bons.furious.patch.mysticalagriculture_coo49;

import bons.furious.mixin.mysticalagriculture_coo49.RecipeManagerProbeMixin;
import com.blakebr0.mysticalagriculture.crafting.recipe.SoulExtractionRecipe;
import com.blakebr0.mysticalagriculture.init.ModRecipeTypes;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch mysticalagriculture_soul_extractor_idle_skip (Mystical Agriculture 7.0.24 with Cucumber
 * 7.0.16, server; kind opt, default on; helper of SoulRecipeScanMixin; no Mystical Agriculture or Cucumber code carried).
 *
 * What the mods do. Every tick a Soul Extractor asks Cucumber's CachedRecipe for its recipe: the remembered recipe is
 * tested first, and when it does not match (always, while the input slot is empty) RecipeManager.getRecipeFor scans every
 * soul extraction recipe: per recipe a new InvWrapper around the extractor's recipe container and an Ingredient.test of the
 * input slot. An idle extractor (empty input slot) runs that scan every tick, finds nothing and stores null.
 *
 * What the switch does. Right before that look-up (inside CachedRecipe.check, after the remembered recipe was tested as the
 * mod does) the check answers false and forgets the remembered recipe, which is exactly what the look-up's empty answer
 * makes it do, without the look-up (the level's getRecipeManager, a getter, is then not called either), but only when the
 * scan certainly finds nothing:
 *  - the recipe type is Mystical Agriculture's soul extraction type and the recipe manager is RecipeManager itself;
 *  - RecipeManager.getRecipeFor, its filter, byType, Ingredient.test and SoulExtractionRecipe's matches methods are as
 *    shipped after every other mod's mixins (MaProbe; any change, for example a recipe-selection mod hooking getRecipeFor
 *    or ModernFix's ingredient hook, makes the switch step aside with one INFO line);
 *  - for the current recipe generation (vanilla_recipe_generation_token: the recipe table, tag rebinds and Forge's
 *    ingredient counter) every recipe of that type is exactly SoulExtractionRecipe, whose matches reads the container's
 *    slot 0 first and answers false when its input ingredient (exactly Ingredient) rejects it, and every such ingredient
 *    rejects an empty stack (checked once per generation: an ingredient that accepts an empty slot, an empty ingredient
 *    or one whose items include air, keeps the scan; a refused generation is looked at again every 64th call only, since
 *    keeping the scan is always exact);
 *  - the container's slot 0 (what each recipe reads first, read once here: a plain read of the extractor's item handler)
 *    is empty; with no soul recipe at all the scan finds nothing either.
 * The checks run cheapest refusal first (type, probe verdict, a refused generation, slot 0); only then is the level asked
 * for its recipe manager (the same getter the mod calls next: when the switch does not answer, the mod's own call follows).
 * Runtime flag bons_and_furious.mysticalagricultureSoulExtractorIdleSkip; SHADOW MODE
 * -Dbons_and_furious.mysticalagricultureSoulExtractorIdleSkip.shadow=true runs the same look-up where the switch would
 * answer, lets the mod's own look-up run as well, and counts SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for the first 20).
 */
public final class SoulExtractorIdle {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mysticalagricultureSoulExtractorIdleSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mysticalagricultureSoulExtractorIdleSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SKIPPED = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final MethodHandle TOKEN_OF, STILL_CURRENT;
    private static volatile Proof proof;
    private static volatile RecipeType<?> soulType;
    private static volatile boolean announced, steppedAside;

    static {
        MethodHandle t = null, s = null;
        try {
            Class<?> c = Class.forName("bons.furious.patch.vanilla_cooking_recipe_index.RecipeGenerationToken", false, SoulExtractorIdle.class.getClassLoader());
            MethodHandles.Lookup l = MethodHandles.lookup();
            t = l.findStatic(c, "tokenOf", MethodType.methodType(Object.class, RecipeManager.class));
            s = l.findStatic(c, "stillCurrent", MethodType.methodType(boolean.class, RecipeManager.class, Object.class));
        } catch (Throwable e) {
            LOGGER.warn("Bons and Furious: mysticalagriculture_soul_extractor_idle_skip cannot reach the recipe generation token ({}); the Soul Extractor's recipe look-up runs as shipped", e.toString());
            t = null;
            s = null;
        }
        TOKEN_OF = t;
        STILL_CURRENT = s;
    }

    /** One recipe generation's verdict for one manager and type: how many recipes, or why the scan must run (null = proven). */
    record Proof(RecipeManager manager, RecipeType<?> type, Object token, int recipes, String why) {}

    private SoulExtractorIdle() {
    }

    /**
     * Right before CachedRecipe.check's {@code level.getRecipeManager().getRecipeFor(type, container, level)} (type already
     * compared with the soul extraction type by the caller): true when that look-up certainly answers Optional.empty() with
     * no other effect (see the class comment), false = let the mod make it.
     */
    public static boolean none(Level level, RecipeType<?> type, Container container) {
        if (type == null || container == null || level == null) return false;
        if (MaProbe.bits != MaProbe.PROBE_ALL) {
            if (!steppedAside) stepAside(MaProbe.why());   // the reason is put together once, for the one INFO line
            return false;
        }
        // a generation already refused keeps the scan; it is looked at again only every 64th call (running the scan is
        // always what the mod does, so a generation that became provable meanwhile only loses a few skips)
        Proof known = proof;
        if (known != null && known.why() != null && known.type() == type && (++retry & 63) != 0) return false;
        // slot 0: the read every soul recipe makes first (a plain read of the extractor's handler), so a filled input leaves
        // here; a container whose read throws keeps the scan, which then behaves as shipped
        ItemStack input;
        try {
            input = container.m_8020_(0);
        } catch (RuntimeException e) {
            return false;
        }
        if (input == null || !input.m_41619_()) return false;
        RecipeManager manager;
        try {
            manager = level.m_7465_();   // the getter the mod calls next
        } catch (RuntimeException e) {
            return false;
        }
        if (manager == null || manager.getClass() != RecipeManager.class || STILL_CURRENT == null) return false;
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
            t = ModRecipeTypes.SOUL_EXTRACTION.get();
        } catch (Throwable e) {
            return null;
        }
        soulType = t;
        return t;
    }

    /** Calls since a refused verdict was last checked against the current generation (racy on purpose: only a pace). */
    private static int retry;

    static Proof proof(RecipeManager manager, RecipeType<?> type) {
        Proof p = proof;
        if (p != null && p.manager() == manager && p.type() == type && current(manager, p.token())) return p;
        Object token = tokenOf(manager);   // taken before the table is read
        if (token == null) return null;
        String why = null;
        int n = 0;
        try {
            Map<?, ?> table = ((RecipeManagerProbeMixin) manager).bons$mysticalagricultureRecipes();
            if (table == null) {
                why = "the recipe manager has no recipe table";
            } else {
                @SuppressWarnings("unchecked")
                Map<?, ?> byType = (Map<?, ?>) ((Map<Object, Object>) table).getOrDefault(type, Collections.emptyMap());   // what byType reads
                for (Object r : byType.values()) {
                    if (r == null || r.getClass() != SoulExtractionRecipe.class) {
                        why = "a soul extraction recipe of another class (" + (r == null ? "null" : r.getClass().getName()) + ")";
                        break;
                    }
                    Ingredient in = ((SoulExtractionRecipe) r).m_7527_().get(0);
                    if (in == null || in.getClass() != Ingredient.class) {
                        why = "an input ingredient of another class (" + (in == null ? "null" : in.getClass().getName()) + ")";
                        break;
                    }
                    if (in.test(ItemStack.f_41583_)) {
                        why = "an input ingredient accepts an empty slot";
                        break;
                    }
                    n++;
                }
            }
        } catch (Throwable t) {
            why = "the recipes could not be read (" + t + ")";
        }
        p = new Proof(manager, type, token, n, why);
        proof = p;
        return p;
    }

    static Object tokenOf(RecipeManager manager) {
        try {
            return TOKEN_OF == null ? null : (Object) TOKEN_OF.invokeExact(manager);
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean current(RecipeManager manager, Object token) {
        try {
            return STILL_CURRENT != null && (boolean) STILL_CURRENT.invokeExact(manager, token);
        } catch (Throwable t) {
            return false;
        }
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
