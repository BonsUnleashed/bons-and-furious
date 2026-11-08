package bons.furious.patch.mysticalagriculture_coo49;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious 1.0.49 switch mysticalagriculture_soul_extractor_empty_skip (Mystical Agriculture with Cucumber; 1.21.1
 * tested build: Mystical Agriculture 1.21.1-8.0.29 with Cucumber 1.21.1-8.0.16, server; kind change, OFF by default; helper
 * of SoulExtractorEmptyMixin; no Mystical Agriculture or Cucumber code carried). The opt-in twin of
 * mysticalagriculture_soul_extractor_idle_skip for packs where that exact switch has to step aside (another mod changed
 * RecipeManager.getRecipeFor or Ingredient.test, for example an ingredient speed-up).
 *
 * What it does. SoulExtractorTileEntity.getActiveRecipe() answers null at once while the input slot (slot 0 of the recipe
 * input it builds from the extractor's slots) is empty, and forgets the remembered recipe as the mod's own empty-slot
 * look-up does: no remembered-recipe test, no scan.
 *
 * Trade-off (off by default). A soul extraction recipe whose input ingredient accepts an empty slot (a tag that holds
 * minecraft:air, or a custom ingredient that accepts an empty stack) no longer runs from an empty input slot; with the
 * switch off the extractor runs such a recipe with nothing in its input. Every other recipe behaves as before (none of them
 * can match an empty slot). Runtime flag bons_and_furious.mysticalagricultureSoulExtractorEmptySkip.
 */
public final class SoulExtractorEmpty {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mysticalagricultureSoulExtractorEmptySkip", "true"));
    private static volatile boolean announced;

    private SoulExtractorEmpty() {
    }

    /** Whether the extractor's input slot (slot 0 of the recipe input) is empty (then getActiveRecipe answers null). */
    public static boolean empty(RecipeInput input) {
        if (input == null || input.size() < 1) return false;
        ItemStack first = input.getItem(0);
        if (first == null || !first.isEmpty()) return false;
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: mysticalagriculture_soul_extractor_empty_skip applies (a Soul Extractor with an empty input slot looks no recipe up)");
        }
        return true;
    }
}
