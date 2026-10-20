package bons.furious.patch.jei_deferral;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.common.util.RegistryUtil;
import mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipe;
import mezz.jei.library.util.ResourceLocationUtil;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * jei_join_recipe_deferral, JEI 15.62 and later (tested 15.71.0.224): GrindstoneRecipeMaker.getDisenchantRecipes(), walked
 * one step at a time. Since JEI 15.62.0.214 ("Group grindstone disenchantment recipe variants") that method is one eager
 * loop that builds ONE recipe per (enchantment, level) holding every supported item:
 * <pre>
 *   for enchantment in enchantment registry order, curses skipped:
 *     enchantmentId = enchantment registry key of enchantment
 *     supportedItems = for item in item registry order: stack = new ItemStack(item); kept when stack.isEnchantable()
 *                      and canEnchant(stack, enchantment, enchantmentId)
 *     for level in 1..min(maxLevel, 10):
 *       topInputs, bottomInputs, outputs = new ArrayList(supportedItems.size()) each
 *       for stack in supportedItems:
 *         enchantedStack = stack.copy(); enchantedStack.enchant(enchantment, level)
 *         output = platformHelper.getGrindstoneResult(grindstoneMenu, enchantedStack, EMPTY)
 *         unless output.isEmpty(): topInputs += enchantedStack, bottomInputs += EMPTY, outputs += output
 *       unless topInputs.isEmpty(): new GrindstoneRecipe(topInputs, bottomInputs, outputs, -1, -1, uid(enchantmentId, level))
 * </pre>
 * Same loops, same order, same calls and arguments here, the same recipe class through its public constructor with lists
 * of the same capacity, and the same uid: JEI's private getDisenchantmentRecipeUid restated ("grindstone.disenchantment.
 * NAMESPACE.PATH.LEVEL" through ResourceLocationUtil.sanitizePath in namespace minecraft; null without an enchantment id;
 * the switch's guard fingerprints that method, so this restatement only runs against the code it restates). canEnchant is
 * JEI's own private method and the grindstone result is computed by JEI's platform helper (both passed in from the mixin).
 * The walk can stop between any two steps when the frame's time is up; inside a (enchantment, level) group it keeps the
 * group's three lists and hands out the recipe once the group is complete, so a recipe never misses an item. The one
 * exact saving, as in the JEI 15.59 walk (GrindstoneDisenchant): whether a fresh new ItemStack(item) is enchantable does not
 * depend on the enchantment, so it is computed once per item (on a stack of its own); every (enchantment, item) pair that
 * passes still gets its own fresh stack for canEnchant and the supported list. Minecraft members by SRG name.
 */
public final class GrindstoneDisenchantGroups implements Deferral.Producer {
    public interface Result {
        ItemStack grindstoneResult(ItemStack topInput, ItemStack bottomInput);
    }

    private final GrindstoneDisenchant.CanEnchant canEnchant;
    private final Result result;
    private final Registry<Enchantment> enchantmentRegistry;
    private final List<Enchantment> enchantments;
    private final List<Item> items;
    private final byte[] enchantable;   // 0 = not checked yet, 1 = yes, 2 = no

    private int enchantmentIndex = -1;
    private Enchantment enchantment;
    private ResourceLocation enchantmentId;
    private List<ItemStack> supportedItems;   // the current enchantment's supported stacks, filled while itemIndex runs
    private int itemIndex;                     // next item of the item list to test (collecting), items.size() when done
    private int level;                         // the level being built, 0 while collecting
    private int supportedIndex;                // next supported stack of the current level
    private List<ItemStack> topInputs, bottomInputs, outputs;

    public GrindstoneDisenchantGroups(GrindstoneDisenchant.CanEnchant canEnchant, Result result) {
        this.canEnchant = canEnchant;
        this.result = result;
        this.enchantmentRegistry = RegistryUtil.getRegistry(Registries.f_256762_);
        Registry<Item> itemRegistry = RegistryUtil.getRegistry(Registries.f_256913_);
        this.enchantments = this.enchantmentRegistry.m_123024_().toList();
        this.items = itemRegistry.m_123024_().toList();
        this.enchantable = new byte[this.items.size()];
    }

    @Override
    public Object next(long deadlineNanos) {
        int steps = 0;
        while (true) {
            if (enchantment == null && !nextEnchantment()) return Deferral.END;
            if (level == 0) {
                // collecting this enchantment's supported items, in item order
                if (itemIndex < items.size()) {
                    int i = itemIndex++;
                    if (enchantable[i] == 0) enchantable[i] = new ItemStack(items.get(i)).m_41792_() ? (byte) 1 : (byte) 2;
                    if (enchantable[i] == 1) {
                        ItemStack stack = new ItemStack(items.get(i));
                        if (canEnchant.test(stack, enchantment, enchantmentId)) supportedItems.add(stack);
                    }
                } else {
                    startLevel(1);
                }
            } else if (level <= Math.min(enchantment.m_6586_(), 10)) {
                if (supportedIndex < supportedItems.size()) {
                    ItemStack stack = supportedItems.get(supportedIndex++);
                    ItemStack enchantedStack = stack.m_41777_();
                    enchantedStack.m_41663_(enchantment, level);
                    ItemStack output = result.grindstoneResult(enchantedStack, ItemStack.f_41583_);
                    if (!output.m_41619_()) {
                        topInputs.add(enchantedStack);
                        bottomInputs.add(ItemStack.f_41583_);
                        outputs.add(output);
                    }
                    // a simulated grindstone takes tens of microseconds: look at the clock after each one
                    if (System.nanoTime() >= deadlineNanos) return Deferral.PAUSE;
                    continue;
                } else {
                    int done = level;
                    List<ItemStack> top = topInputs, bottom = bottomInputs, out = outputs;
                    startLevel(level + 1);
                    if (!top.isEmpty()) return new GrindstoneRecipe(top, bottom, out, -1, -1, uid(enchantmentId, done));
                }
            } else {
                enchantment = null;   // every level done: the next enchantment
            }
            if ((++steps & 63) == 0 && System.nanoTime() >= deadlineNanos) return Deferral.PAUSE;
        }
    }

    private void startLevel(int lv) {
        level = lv;
        supportedIndex = 0;
        topInputs = new ArrayList<>(supportedItems.size());
        bottomInputs = new ArrayList<>(supportedItems.size());
        outputs = new ArrayList<>(supportedItems.size());
    }

    /** Moves to the next enchantment in registry order, curses skipped; false when there is none. */
    private boolean nextEnchantment() {
        do {
            enchantmentIndex++;
            if (enchantmentIndex >= enchantments.size()) {
                enchantmentIndex = enchantments.size();
                return false;
            }
            enchantment = enchantments.get(enchantmentIndex);
        } while (enchantment.m_6589_());
        enchantmentId = enchantmentRegistry.m_7981_(enchantment);
        supportedItems = new ArrayList<>();
        itemIndex = 0;
        level = 0;
        return true;
    }

    /** JEI 15.62+ GrindstoneRecipeMaker.getDisenchantmentRecipeUid, restated (MIT; guarded by its fingerprint). */
    static ResourceLocation uid(ResourceLocation enchantmentId, int level) {
        if (enchantmentId == null) return null;
        String rawPath = "grindstone.disenchantment.%s.%s.%s".formatted(enchantmentId.m_135827_(), enchantmentId.m_135815_(), Integer.toString(level));
        return new ResourceLocation("minecraft", ResourceLocationUtil.sanitizePath(rawPath));
    }
}
