package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.GoetyHandles;
import com.Polarice3.Goety.common.capabilities.soulenergy.ISoulEnergy;
import com.Polarice3.Goety.utils.SEHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * goety_capability_handles (Goety 2.5.60.0, MIT; both sides; since 1.0.49): SEHelper.getCapability(Player) is wrapped
 * (MixinExtras @WrapMethod: the kept value while the layer says it is the same answer, see GoetyHandles) and its orElse is
 * redirected to itself plus one line that keeps a present value; SEHelper.isAlly(Player, LivingEntity) answers false at
 * once when the SE capability's two ally sets are empty (what the original computes). No Goety code is carried.
 */
@Mixin(value = SEHelper.class, remap = false)
public abstract class SEHelperHandlesMixin {
    @WrapMethod(method = "getCapability(Lnet/minecraft/world/entity/player/Player;)Lcom/Polarice3/Goety/common/capabilities/soulenergy/ISoulEnergy;")
    private static ISoulEnergy bons$keptSoulEnergy(Player player, Operation<ISoulEnergy> original) {
        Object kept = GoetyHandles.kept(player, GoetyHandles.SE);
        if (kept == GoetyHandles.MISS) return original.call(player);
        if (GoetyHandles.SHADOW) return (ISoulEnergy) GoetyHandles.shadow(player, GoetyHandles.SE, kept, original.call(player));
        return (ISoulEnergy) kept;
    }

    @Redirect(method = "getCapability(Lnet/minecraft/world/entity/player/Player;)Lcom/Polarice3/Goety/common/capabilities/soulenergy/ISoulEnergy;",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/util/LazyOptional;orElse(Ljava/lang/Object;)Ljava/lang/Object;"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object bons$keepSoulEnergy(LazyOptional optional, Object other, Player player) {
        Object value = ((LazyOptional<Object>) optional).orElse(other);
        if (value != other) GoetyHandles.keep(player, GoetyHandles.SE, optional, value);
        return value;
    }

    @WrapMethod(method = "isAlly(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/LivingEntity;)Z")
    private static boolean bons$noAllies(Player owner, LivingEntity target, Operation<Boolean> original) {
        // a null target makes the original throw at target.getType(): it runs as shipped
        if (GoetyHandles.enabled && owner != null && target != null) {
            ISoulEnergy se = SEHelper.getCapability(owner);
            if (se.allyList().isEmpty() && se.allyTypeList().isEmpty()) {
                GoetyHandles.announceAlly();
                return GoetyHandles.SHADOW ? GoetyHandles.shadowAlly(original.call(owner, target)) : false;
            }
        }
        return original.call(owner, target);
    }
}
