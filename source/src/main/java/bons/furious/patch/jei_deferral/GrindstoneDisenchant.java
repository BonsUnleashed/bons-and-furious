package bons.furious.patch.jei_deferral;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.common.platform.IPlatformIngredientHelper;
import mezz.jei.common.util.RegistryUtil;
import mezz.jei.library.util.ResourceLocationUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** JEI 19.51's enchantment/support-item/level ordering, suspended between bounded recipe steps. */
public final class GrindstoneDisenchant {
    public interface CanEnchant {
        boolean test(ItemStack stack, Holder<Enchantment> enchantment, ResourceLocation enchantmentId);
    }

    public interface Builder {
        IJeiGrindstoneRecipe build(ItemStack topInput, ItemStack bottomInput, ResourceLocation uid);
    }

    private final IPlatformIngredientHelper ingredientHelper;
    private final CanEnchant canEnchant;
    private final Builder builder;
    private final List<Holder.Reference<Enchantment>> enchantments;
    private Iterator<Holder<Item>> items = Collections.emptyIterator();
    private int enchantmentIndex;
    private Holder<Enchantment> enchantment;
    private ResourceLocation enchantmentId;
    private String enchantmentPath;
    private ItemStack stack;
    private int level;

    public GrindstoneDisenchant(IPlatformIngredientHelper ingredientHelper, CanEnchant canEnchant, Builder builder) {
        this.ingredientHelper = ingredientHelper;
        this.canEnchant = canEnchant;
        this.builder = builder;
        Registry<Enchantment> registry = RegistryUtil.getRegistry(Registries.ENCHANTMENT);
        this.enchantments = registry.holders().toList();
    }

    Object next(long deadlineNanos) {
        while (System.nanoTime() < deadlineNanos) {
            if (stack != null) {
                if (level <= Math.min(enchantment.value().getMaxLevel(), 10)) {
                    int currentLevel = level++;
                    ItemStack enchanted = stack.copy();
                    enchanted.enchant(enchantment, currentLevel);
                    String path = "grindstone.disenchantment.%s.%s.%s".formatted(
                            stack.getItem().getDescriptionId(), enchantmentPath, Integer.toString(currentLevel));
                    ResourceLocation uid = ResourceLocation.withDefaultNamespace(ResourceLocationUtil.sanitizePath(path));
                    IJeiGrindstoneRecipe recipe = builder.build(enchanted, ItemStack.EMPTY, uid);
                    if (recipe != null) return recipe;
                    continue;
                }
                stack = null;
            }
            if (items.hasNext()) {
                ItemStack candidate = new ItemStack(items.next());
                if (candidate.isEnchantable() && canEnchant.test(candidate, enchantment, enchantmentId)) {
                    stack = candidate;
                    level = 1;
                }
                continue;
            }
            if (enchantmentIndex >= enchantments.size()) return Deferral.END;
            enchantment = enchantments.get(enchantmentIndex++);
            if (enchantment.is(EnchantmentTags.CURSE)) continue;
            enchantmentId = enchantment.unwrapKey().map(key -> key.location()).orElse(null);
            enchantmentPath = enchantmentId == null ? null : enchantmentId.getPath();
            items = ingredientHelper.getSupportedItems(enchantment).iterator();
        }
        return Deferral.PAUSE;
    }
}