package bons.furious.patch.jei_deferral;

import java.util.List;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.common.util.RegistryUtil;
import mezz.jei.library.util.ResourceLocationUtil;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * JEI 15.59 GrindstoneRecipeMaker.getDisenchantRecipes(), walked one step at a time. That method is one eager loop:
 * <pre>
 *   for enchantment in enchantment registry order, curses skipped:
 *     for item in item registry order:
 *       stack = new ItemStack(item)
 *       if stack.isEnchantable() and canEnchant(stack, enchantment):
 *         for level in 1..min(maxLevel, 10):
 *           recipe = getGrindstoneRecipe(copy of stack enchanted at level, EMPTY, uid "grindstone.disenchantment.<item>.<ench>.<level>")
 * </pre>
 * Same loops, same order, same uid strings here; canEnchant and getGrindstoneRecipe are JEI's own private methods (passed
 * in as lambdas from the mixin). One exact saving: whether a fresh new ItemStack(item) is enchantable does not depend on
 * the enchantment, so it is computed once per item instead of once per (enchantment, item) pair; every pair that passes
 * still gets its own fresh stack. Minecraft members are called by SRG name (no remapping in this build). The switch's guard
 * fingerprints JEI's getDisenchantRecipes, so this walk only runs against the loop it restates.
 */
public final class GrindstoneDisenchant {
    public interface CanEnchant {
        boolean test(ItemStack stack, Enchantment enchantment, ResourceLocation enchantmentId);
    }

    public interface Builder {
        IJeiGrindstoneRecipe build(ItemStack topInput, ItemStack bottomInput, ResourceLocation uid);
    }

    private final CanEnchant canEnchant;
    private final Builder builder;
    private final Registry<Enchantment> enchantmentRegistry;
    private final List<Enchantment> enchantments;
    private final List<Item> items;
    private final byte[] enchantable;   // 0 = not checked yet, 1 = yes, 2 = no

    private int enchantmentIndex = -1;
    private int itemIndex = -1;
    private Enchantment enchantment;
    private ResourceLocation enchantmentId;
    private String enchantmentPath;
    private ItemStack stack;            // the current pair's fresh stack while its levels are being built
    private int level;

    public GrindstoneDisenchant(CanEnchant canEnchant, Builder builder) {
        this.canEnchant = canEnchant;
        this.builder = builder;
        this.enchantmentRegistry = RegistryUtil.getRegistry(Registries.f_256762_);
        Registry<Item> itemRegistry = RegistryUtil.getRegistry(Registries.f_256913_);
        this.enchantments = this.enchantmentRegistry.m_123024_().toList();
        this.items = itemRegistry.m_123024_().toList();
        this.enchantable = new byte[this.items.size()];
    }

    Object next(long deadlineNanos) {
        int scanned = 0;
        while (true) {
            if (stack != null) {
                if (level <= Math.min(enchantment.m_6586_(), 10)) {
                    int lv = level++;
                    ItemStack enchantedStack = stack.m_41777_();
                    enchantedStack.m_41663_(enchantment, lv);
                    String itemId = stack.m_41720_().m_5524_();
                    String rawPath = "grindstone.disenchantment.%s.%s.%s".formatted(itemId, enchantmentPath, Integer.toString(lv));
                    ResourceLocation uid = new ResourceLocation("minecraft", ResourceLocationUtil.sanitizePath(rawPath));
                    IJeiGrindstoneRecipe recipe = builder.build(enchantedStack, ItemStack.f_41583_, uid);
                    if (recipe != null) {
                        return recipe;
                    }
                    continue;
                }
                stack = null;
            }
            if (!advance()) {
                return Deferral.END;
            }
            int i = itemIndex;
            if (enchantable[i] == 0) {
                enchantable[i] = new ItemStack(items.get(i)).m_41792_() ? (byte) 1 : (byte) 2;
            }
            if (enchantable[i] == 1) {
                ItemStack fresh = new ItemStack(items.get(i));
                if (canEnchant.test(fresh, enchantment, enchantmentId)) {
                    stack = fresh;
                    level = 1;
                }
            }
            if ((++scanned & 63) == 0 && System.nanoTime() >= deadlineNanos) {
                return Deferral.PAUSE;
            }
        }
    }

    /** Moves to the next (enchantment, item) pair in JEI's loop order; false when the loop is over. */
    private boolean advance() {
        if (items.isEmpty()) {
            return false;
        }
        if (enchantment != null && itemIndex + 1 < items.size()) {
            itemIndex++;
            return true;
        }
        do {
            enchantmentIndex++;
            if (enchantmentIndex >= enchantments.size()) {
                enchantment = null;
                itemIndex = items.size();
                return false;
            }
            enchantment = enchantments.get(enchantmentIndex);
        } while (enchantment.m_6589_());
        enchantmentId = enchantmentRegistry.m_7981_(enchantment);
        enchantmentPath = enchantmentId != null ? enchantmentId.m_135815_() : null;
        itemIndex = 0;
        return true;
    }
}
