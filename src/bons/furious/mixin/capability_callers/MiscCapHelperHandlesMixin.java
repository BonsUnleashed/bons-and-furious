package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.GoetyHandles;
import com.Polarice3.Goety.common.capabilities.misc.IMisc;
import com.Polarice3.Goety.utils.MiscCapHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * goety_capability_handles (Goety 2.5.60.0, MIT; both sides; since 1.0.49): MiscCapHelper.getCapability(LivingEntity) is
 * wrapped (the kept value while the layer says it is the same answer, see GoetyHandles); its orElseGet is redirected to the
 * same steps orElseGet takes (the optional's value once, else the supplier) plus one line that keeps a present value. No
 * Goety code is carried.
 */
@Mixin(value = MiscCapHelper.class, remap = false)
public abstract class MiscCapHelperHandlesMixin {
    @WrapMethod(method = "getCapability(Lnet/minecraft/world/entity/LivingEntity;)Lcom/Polarice3/Goety/common/capabilities/misc/IMisc;")
    private static IMisc bons$keptMisc(LivingEntity entity, Operation<IMisc> original) {
        Object kept = GoetyHandles.kept(entity, GoetyHandles.MISC);
        if (kept == GoetyHandles.MISS) return original.call(entity);
        if (GoetyHandles.SHADOW) return (IMisc) GoetyHandles.shadow(entity, GoetyHandles.MISC, kept, original.call(entity));
        return (IMisc) kept;
    }

    @Redirect(method = "getCapability(Lnet/minecraft/world/entity/LivingEntity;)Lcom/Polarice3/Goety/common/capabilities/misc/IMisc;",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/util/LazyOptional;orElseGet(Lnet/minecraftforge/common/util/NonNullSupplier;)Ljava/lang/Object;"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object bons$keepMisc(LazyOptional optional, NonNullSupplier other, LivingEntity entity) {
        Object value = ((LazyOptional<Object>) optional).orElse(null);
        if (value != null) {
            GoetyHandles.keep(entity, GoetyHandles.MISC, optional, value);
            return value;
        }
        return other.get();
    }
}
