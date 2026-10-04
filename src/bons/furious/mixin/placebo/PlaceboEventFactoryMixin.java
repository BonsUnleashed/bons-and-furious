package bons.furious.mixin.placebo;

import bons.furious.patch.placebo.EnchantmentEventListeners;
import dev.shadowsoffire.placebo.events.GetEnchantmentLevelEvent;
import dev.shadowsoffire.placebo.events.PlaceboEventFactory;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * placebo_enchantment_event_skip (Placebo 8.6.3, MIT: the two method bodies below carry Placebo's own code after our
 * check). When EnchantmentEventListeners says nothing listens for GetEnchantmentLevelEvent, the single-enchantment query
 * returns the level it was given and the all-enchantments query returns its HashMap copy, without the event and the
 * post (see EnchantmentEventListeners for why that is the posted result). A stack that is not an ItemStack, as Placebo's
 * cast would refuse, takes Placebo's path.
 */
@Mixin(value = PlaceboEventFactory.class, remap = false)
public abstract class PlaceboEventFactoryMixin {
    /**
     * @author BonsUnleashed
     * @reason Skip building and posting the event while nothing listens for it (placebo_enchantment_event_skip).
     */
    @Overwrite
    public static int getEnchantmentLevelSpecific(int level, IForgeItemStack stack, Enchantment ench) {
        if (stack instanceof ItemStack && EnchantmentEventListeners.none()) {
            if (EnchantmentEventListeners.SHADOW) EnchantmentEventListeners.shadow(level, bons$posted(level, stack, ench));
            return level;
        }
        return bons$posted(level, stack, ench);
    }

    @Unique
    private static int bons$posted(int level, IForgeItemStack stack, Enchantment ench) {
        HashMap<Enchantment, Integer> map = new HashMap<>();
        map.put(ench, level);
        GetEnchantmentLevelEvent event = new GetEnchantmentLevelEvent((ItemStack) stack, map);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getEnchantments().get(ench);
    }

    /**
     * @author BonsUnleashed
     * @reason Skip building and posting the event while nothing listens for it (placebo_enchantment_event_skip).
     */
    @Overwrite
    public static Map<Enchantment, Integer> getEnchantmentLevel(Map<Enchantment, Integer> enchantments, IForgeItemStack stack) {
        enchantments = new HashMap<>(enchantments);
        if (stack instanceof ItemStack && EnchantmentEventListeners.none()) {
            return enchantments;
        }
        GetEnchantmentLevelEvent event = new GetEnchantmentLevelEvent((ItemStack) stack, enchantments);
        MinecraftForge.EVENT_BUS.post(event);
        return enchantments;
    }
}
