package bons.furious.mixin.curios;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

/**
 * curios_foreign_capability_fast_path (Curios API 5.14.1+1.20.1, both sides).
 *
 * Curios attaches this provider to living entities, and Forge asks it about every capability queried on such an
 * entity. The provider looked up the entity's slot map first and only then checked whether the query was for the
 * Curios inventory at all, so every other mod's capability query paid a slot lookup. A query for any other capability
 * now returns LazyOptional.empty() at once: the original returns that same empty singleton on both of its paths for
 * such a query, and the slot lookup has no side effects. Curios inventory queries run the original code unchanged.
 */
@Mixin(value = CurioInventoryCapability.Provider.class, remap = false)
public abstract class ForeignCapabilityMixin {
    @Shadow
    @Final
    LazyOptional<ICuriosItemHandler> optional;

    @Shadow
    @Final
    LivingEntity wearer;

    /**
     * @author BonsUnleashed
     * @reason Answer other capabilities before the slot lookup (the only change; the rest is the original method).
     */
    @Overwrite
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction facing) {
        if (capability != CuriosCapability.INVENTORY) {
            return LazyOptional.empty();
        }
        if (CuriosApi.getEntitySlots(this.wearer).isEmpty()) {
            return LazyOptional.empty();
        }
        return CuriosCapability.INVENTORY.orEmpty(capability, this.optional);
    }
}
