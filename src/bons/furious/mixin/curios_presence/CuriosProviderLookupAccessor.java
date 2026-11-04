package bons.furious.mixin.curios_presence;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

/**
 * curios_inventory_lookup_layer (Curios API 5.14.1+1.20.1, since 1.0.49): read access to the two final fields of Curios'
 * inventory provider, its LazyOptional (the object every inventory lookup of that entity answers while the type has slots)
 * and its wearer. Adds no behaviour.
 */
@Mixin(value = CurioInventoryCapability.Provider.class, remap = false)
public interface CuriosProviderLookupAccessor {
    @Accessor(value = "optional", remap = false)
    LazyOptional<ICuriosItemHandler> bons$lookupOptional();

    @Accessor(value = "wearer", remap = false)
    LivingEntity bons$lookupWearer();
}
