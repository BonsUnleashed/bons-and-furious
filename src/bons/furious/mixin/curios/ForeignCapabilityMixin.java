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
 * 1.0.34: only while the wearer has a level. For a living entity built without one, Curios' slot lookup throws
 * (CuriosApi.getEntitySlots reads level().isClientSide()) whatever the capability, so there the original code runs and
 * throws as before.
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
        // 1.0.34: the early answer only where the slot lookup cannot throw (a wearer with a level); a null wearer and a
        // wearer without a level take Curios' own lines below (empty map, resp. its NullPointerException), as before
        if (capability != CuriosCapability.INVENTORY && this.wearer != null && this.wearer.m_9236_() != null) {
            return LazyOptional.empty();
        }
        if (CuriosApi.getEntitySlots(this.wearer).isEmpty()) {
            return LazyOptional.empty();
        }
        return CuriosCapability.INVENTORY.orEmpty(capability, this.optional);
    }
}
