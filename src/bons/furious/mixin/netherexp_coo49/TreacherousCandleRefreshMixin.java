package bons.furious.mixin.netherexp_coo49;

import bons.furious.patch.netherexp_coo49.NetherExpFixes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jadenxgamer.netherexp.registry.block.entity.TreacherousCandleBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * netherexp_candle_effect_refresh_half (trade-off, off by default; Nether Expansion 2.3.5, server): the one
 * player.addEffect(Betrayed 200, I) call in TreacherousCandleBlockEntity.updateBossBarPlayers is skipped while the player's
 * Betrayed is level I, not ambient, with 100 or more ticks left (then it returns false, the value the original's
 * addEffect answers when nothing changes; the candle ignores it). See NetherExpFixes. COO 5.8 hooks the same method
 * (netherexp.MixinJneTreacherousCandle, option netherexp.throttleCandleEffectRefresh).
 */
@Mixin(value = TreacherousCandleBlockEntity.class, remap = false)
public abstract class TreacherousCandleRefreshMixin {
    @WrapOperation(method = "updateBossBarPlayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;m_7292_(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
            require = 1, allow = 1)
    private static boolean bons$refreshBetrayedHalf(ServerPlayer player, MobEffectInstance given, Operation<Boolean> original) {
        if (NetherExpFixes.candleRefresh && NetherExpFixes.stillFresh(player, given)) return false;
        return original.call(player, given);
    }
}
