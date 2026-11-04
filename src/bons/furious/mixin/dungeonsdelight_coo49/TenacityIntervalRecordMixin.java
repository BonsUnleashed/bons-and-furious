package bons.furious.mixin.dungeonsdelight_coo49;

import bons.furious.patch.dungeonsdelight_coo49.TenacityIntervalHolder;
import bons.furious.patch.dungeonsdelight_coo49.TenacityIntervals;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.yirmiri.dungeonsdelight.common.effect.TenacityEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * dungeonsdelight_tenacity_per_player_interval: the two TenacityEffect.getInterval(player) calls (in applyEffectTick and
 * addAttributeModifiers, both server-side, players only) return as before and also store the value on that player.
 * Dungeons Delight's shared field is still written exactly as before.
 */
@Mixin(value = TenacityEffect.class, remap = false)
public abstract class TenacityIntervalRecordMixin {
    @WrapOperation(method = {"m_6742_", "m_6385_"}, at = @At(value = "INVOKE",
            target = "Lnet/yirmiri/dungeonsdelight/common/effect/TenacityEffect;getInterval(Lnet/minecraft/world/entity/player/Player;)I"),
            require = 2, allow = 2)
    private int bons$recordOwnInterval(Player player, Operation<Integer> original) {
        int interval = original.call(player);
        if (TenacityIntervals.enabled && player instanceof TenacityIntervalHolder holder) holder.bons$setTenacityInterval(interval);
        return interval;
    }
}
