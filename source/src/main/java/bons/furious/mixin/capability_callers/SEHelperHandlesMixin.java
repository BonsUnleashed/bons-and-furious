package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.GoetyHandles;
import com.Polarice3.Goety.common.capabilities.soulenergy.ISoulEnergy;
import com.Polarice3.Goety.utils.SEHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * goety_capability_handles (Goety 3.2.2, MIT; both sides; since 1.0.49): SEHelper.isAlly(Player, LivingEntity) answers
 * false at once when the soul energy data's two ally sets are empty (what the original computes from two new lists).
 * Goety 3.2.2's SEHelper / LichdomHelper / MiscCapHelper.getCapability are NeoForge data attachment reads
 * (entity.getData), so there is no capability lookup left to keep. No Goety code is carried.
 */
@Mixin(value = SEHelper.class, remap = false)
public abstract class SEHelperHandlesMixin {
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
