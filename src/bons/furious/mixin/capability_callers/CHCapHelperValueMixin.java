package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.CallerCapabilityHandles;
import bons.furious.patch.capability_callers.ClangingHowlHandles;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mongoose.clanginghowl.common.capabilities.CHCapHelper;
import com.mongoose.clanginghowl.common.capabilities.ICHCap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * clanginghowl_capability_handles (Clanging Howl 1.0.4, no licence file: no Clanging Howl code is carried; both sides,
 * since 1.0.46).
 *
 * CHCapHelper.getCapability(entity) is wrapped as a whole (MixinExtras @WrapMethod): when the forge_capability_handle_layer
 * table keeps this entity's resolved value (ClangingHowlHandles.kept: only fields are read), that value is returned
 * without the dispatcher walk, Clanging Howl's new LazyOptional and lambda, and the fallback CHCapImp the original
 * builds and drops whenever the capability is present. Otherwise the original runs unchanged.
 *
 * The original's orElse call is redirected to itself plus one line: a present value (orElse did not return the fallback)
 * is kept for the entity with the optional it came from (the target method's own entity argument is passed along).
 */
@Mixin(value = CHCapHelper.class, remap = false)
public abstract class CHCapHelperValueMixin {
    @WrapMethod(method = "getCapability")
    private static ICHCap bons$keptCHCap(LivingEntity entity, Operation<ICHCap> original) {
        Object kept = ClangingHowlHandles.kept(entity);
        if (kept == CallerCapabilityHandles.MISS) return original.call(entity);
        if (ClangingHowlHandles.SHADOW) return (ICHCap) ClangingHowlHandles.shadow(entity, kept, original.call(entity));
        return (ICHCap) kept;
    }

    @Redirect(method = "getCapability", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/common/util/LazyOptional;orElse(Ljava/lang/Object;)Ljava/lang/Object;"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object bons$keepCHCap(LazyOptional optional, Object other, LivingEntity entity) {
        Object value = ((LazyOptional<Object>) optional).orElse(other);
        if (value != other) ClangingHowlHandles.keep(entity, optional, value);
        return value;
    }
}
