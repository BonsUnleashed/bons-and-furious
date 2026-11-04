package bons.furious.mixin.curios_presence;

import bons.furious.patch.curios_presence.CuriosItemLookups;
import bons.furious.patch.curios_presence.CuriosPresenceWrapperDuck;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

/**
 * curios_equipped_presence (Curios API 5.14.1+1.20.1, LGPL-3.0; both sides; since 1.0.49).
 *
 * CurioInventoryWrapper.findFirstCurio(Item) and findCurios(Item) are wrapped as a whole (MixinExtras @WrapMethod): for
 * Curios' own wrapper class CuriosItemLookups runs the lean twin of the walk over this wrapper's own slot map (CuriosLean:
 * the same handler and stack calls in the same order, the same records on a hit), otherwise the original runs. Also adds
 * read access to the slot map and wearer for CuriosHelper's lookups and the CuriosPresence API (CuriosPresenceWrapperDuck).
 */
@Mixin(value = CurioInventoryCapability.CurioInventoryWrapper.class, remap = false)
public abstract class CurioInventoryWrapperPresenceMixin implements CuriosPresenceWrapperDuck {
    @Shadow
    Map<String, ICurioStacksHandler> curios;

    @Shadow
    LivingEntity wearer;

    @Override
    public Map<String, ICurioStacksHandler> bons$presenceCurios() {
        return this.curios;
    }

    @Override
    public LivingEntity bons$presenceWearer() {
        return this.wearer;
    }

    @WrapMethod(method = "findFirstCurio(Lnet/minecraft/world/item/Item;)Ljava/util/Optional;")
    private Optional<SlotResult> bons$presenceFirst(Item item, Operation<Optional<SlotResult>> original) {
        return CuriosItemLookups.wrapperFirst(this, this.curios, this.wearer, item, original);
    }

    @WrapMethod(method = "findCurios(Lnet/minecraft/world/item/Item;)Ljava/util/List;")
    private List<SlotResult> bons$presenceAll(Item item, Operation<List<SlotResult>> original) {
        return CuriosItemLookups.wrapperAll(this, this.curios, this.wearer, item, original);
    }
}
