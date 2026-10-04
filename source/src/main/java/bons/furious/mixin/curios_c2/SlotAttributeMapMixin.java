package bons.furious.mixin.curios_c2;

import bons.furious.patch.curios_c2.CuriosMaps;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import top.theillusivec4.curios.api.SlotAttribute;

/**
 * curios_thread_safe_caches (Curios API, LGPL-3.0-or-later; 1.21.1 tested build curios-neoforge-9.5.1+1.21.1; both
 * sides): SlotAttribute.getOrCreate, the only accessor of the static HashMap SLOT_ATTRIBUTES, runs under one lock (see
 * {@link CuriosMaps}).
 *
 * Ported to 1.21.1: getOrCreate now returns a Holder&lt;Attribute&gt; and fills the map itself (Curios' 1.20.1
 * MixinSlotAttribute HEAD inject no longer exists); the second wrapped accessor of 1.20.1, CuriosImplMixinHooks.getUuid,
 * is gone (see CuriosMaps), so this is the key's only mixin.
 */
@Mixin(value = SlotAttribute.class, remap = false)
public abstract class SlotAttributeMapMixin {
    @WrapMethod(method = "getOrCreate")
    private static Holder<Attribute> bons$lockedSlotAttribute(String id, Operation<Holder<Attribute>> original) {
        return CuriosMaps.slotAttribute(id, original);
    }
}
