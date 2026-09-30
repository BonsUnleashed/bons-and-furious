package bons.furious.mixin.curios;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotAttribute;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

/**
 * curios_lazy_modifier_map (Curios API 5.14.1+1.20.1, both sides).
 *
 * clearCachedSlotModifiers built a HashMultimap (with its bookkeeping and view wrappers) on every call, even when no
 * slot attribute was found. The slot modifiers are now collected in a plain map of sets that is only created for the
 * first modifier, using the same Guava-created HashMap and HashSets a HashMultimap uses (expected sizes 12 and 2), and
 * the final removal pass is skipped when nothing was collected. The same modifiers are removed in the same order.
 */
@Mixin(value = CurioInventoryCapability.CurioInventoryWrapper.class, remap = false)
public abstract class CurioInventoryWrapperMixin {
    @Shadow
    Map<String, ICurioStacksHandler> curios;

    @Shadow
    public abstract LivingEntity getWearer();

    /**
     * @author BonsUnleashed
     * @reason Collect slot modifiers lazily instead of in an always-allocated HashMultimap.
     */
    @Overwrite
    public void clearCachedSlotModifiers() {
        Map<String, Set<AttributeModifier>> slots = null;

        for (Map.Entry<String, ICurioStacksHandler> entry : this.curios.entrySet()) {
            ICurioStacksHandler stacksHandler = entry.getValue();
            Set<AttributeModifier> modifiers = stacksHandler.getCachedModifiers();

            if (!modifiers.isEmpty()) {
                IDynamicStackHandler stacks = stacksHandler.getStacks();
                NonNullList<Boolean> renderStates = stacksHandler.getRenders();
                String id = entry.getKey();

                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);

                    if (!stack.m_41619_()) {
                        SlotContext slotContext = new SlotContext(id, this.getWearer(), i, false,
                                renderStates.size() > i && renderStates.get(i));
                        UUID uuid = CuriosApi.getSlotUuid(slotContext);
                        Multimap<Attribute, AttributeModifier> map = CuriosApi.getAttributeModifiers(slotContext, uuid, stack);

                        for (Attribute attribute : map.keySet()) {
                            if (attribute instanceof SlotAttribute wrapper) {
                                slots = ac$collectSlotModifiers(slots, wrapper.getIdentifier(), map.get(attribute));
                            }
                        }
                    }
                }
            }
        }

        if (slots == null) {
            return;
        }

        for (Map.Entry<String, ? extends Collection<AttributeModifier>> entry : slots.entrySet()) {
            String id = entry.getKey();
            ICurioStacksHandler stacksHandler = this.curios.get(id);

            if (stacksHandler != null) {
                for (AttributeModifier attributeModifier : entry.getValue()) {
                    stacksHandler.getCachedModifiers().remove(attributeModifier);
                }
                stacksHandler.clearCachedModifiers();
            }
        }
    }

    /**
     * Adds modifiers to the set of one slot identifier, creating the map and the set on first use. Same effect as
     * HashMultimap.putAll(id, modifiers): an empty collection adds nothing, and a new key is only stored when at least
     * one modifier was added.
     */
    @Unique
    private static <T> Map<String, Set<T>> ac$collectSlotModifiers(Map<String, Set<T>> slots, String id, Collection<T> modifiers) {
        Objects.requireNonNull(modifiers);
        if (modifiers.isEmpty()) return slots;
        if (slots == null) slots = Maps.newHashMapWithExpectedSize(12);
        Set<T> set = slots.get(id);
        if (set == null) {
            set = Sets.newHashSetWithExpectedSize(2);
            if (set.addAll(modifiers)) slots.put(id, set);
        } else {
            set.addAll(modifiers);
        }
        return slots;
    }
}
