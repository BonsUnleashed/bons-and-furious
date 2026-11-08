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
 * dungeonsdelight_tenacity_per_player_interval (Dungeon's Delight 1.5.1 for NeoForge 1.21.1, server): the two
 * TenacityEffect.getInterval(player) calls (in applyEffectTick and onEffectAdded, both server-side, players only) return as
 * before and also store the value on that player. Dungeons Delight's shared field is still written exactly as before.
 *
 * Ported to 1.21.1: the effect-start write moved from addAttributeModifiers (1.2.10 / 1.3.1) to onEffectAdded, which
 * LivingEntity.addEffect calls when a new Tenacity instance is added; applyEffectTick keeps its write.
 */
@Mixin(value = TenacityEffect.class, remap = false)
public abstract class TenacityIntervalRecordMixin {
    @WrapOperation(method = {"applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z", "onEffectAdded(Lnet/minecraft/world/entity/LivingEntity;I)V"}, at = @At(value = "INVOKE",
            target = "Lnet/yirmiri/dungeonsdelight/common/effect/TenacityEffect;getInterval(Lnet/minecraft/world/entity/player/Player;)I"),
            require = 2, allow = 2)
    private int bons$recordOwnInterval(Player player, Operation<Integer> original) {
        int interval = original.call(player);
        if (TenacityIntervals.enabled && player instanceof TenacityIntervalHolder holder) holder.bons$setTenacityInterval(interval);
        return interval;
    }
}
