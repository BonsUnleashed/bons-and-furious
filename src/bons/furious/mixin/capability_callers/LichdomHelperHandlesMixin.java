package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.GoetyHandles;
import com.Polarice3.Goety.common.capabilities.lichdom.ILichdom;
import com.Polarice3.Goety.utils.LichdomHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * goety_capability_handles (Goety 2.5.60.0, MIT; both sides; since 1.0.49): LichdomHelper.getCapability(Player) is wrapped
 * (the kept value while the layer says it is the same answer, see GoetyHandles) and its orElse redirected to itself plus
 * one line that keeps a present value. No Goety code is carried.
 */
@Mixin(value = LichdomHelper.class, remap = false)
public abstract class LichdomHelperHandlesMixin {
    @WrapMethod(method = "getCapability(Lnet/minecraft/world/entity/player/Player;)Lcom/Polarice3/Goety/common/capabilities/lichdom/ILichdom;")
    private static ILichdom bons$keptLichdom(Player player, Operation<ILichdom> original) {
        Object kept = GoetyHandles.kept(player, GoetyHandles.LICH);
        if (kept == GoetyHandles.MISS) return original.call(player);
        if (GoetyHandles.SHADOW) return (ILichdom) GoetyHandles.shadow(player, GoetyHandles.LICH, kept, original.call(player));
        return (ILichdom) kept;
    }

    @Redirect(method = "getCapability(Lnet/minecraft/world/entity/player/Player;)Lcom/Polarice3/Goety/common/capabilities/lichdom/ILichdom;",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/util/LazyOptional;orElse(Ljava/lang/Object;)Ljava/lang/Object;"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object bons$keepLichdom(LazyOptional optional, Object other, Player player) {
        Object value = ((LazyOptional<Object>) optional).orElse(other);
        if (value != other) GoetyHandles.keep(player, GoetyHandles.LICH, optional, value);
        return value;
    }
}
