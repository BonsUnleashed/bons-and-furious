package bons.furious.mixin.netherexp_coo49;

import bons.furious.patch.netherexp_coo49.NetherExpFixes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * netherexp_candle_effect_refresh_half (trade-off, off by default; Jaden's Nether Expansion, CC BY-NC-ND 4.0: no code
 * carried; 1.21.1 tested build: Jaden's Nether Expansion 2.4.1; server): the one player.addEffect(Betrayed 200, I) call in
 * CiergeOfTreacheryBlockEntity.updateBossBarPlayers is skipped while the player's Betrayed is level I, not ambient, shown
 * with its icon, with 100 or more ticks left (then it returns false, the value the original's addEffect answers when nothing
 * changes; the block entity ignores it). See NetherExpFixes.
 *
 * Ported to 1.21.1: 2.3.5's treacherous candle block entity is the Cierge of Treachery in 2.4.1
 * (net.jadenxgamer.netherexp.core.block.entity.CiergeOfTreacheryBlockEntity; the treacherous candle is a plain candle block
 * now); updateBossBarPlayers is the same code (Betrayed is a Holder of the effect).
 */
@Mixin(targets = "net.jadenxgamer.netherexp.core.block.entity.CiergeOfTreacheryBlockEntity", remap = false)
public abstract class TreacherousCandleRefreshMixin {
    @WrapOperation(method = "updateBossBarPlayers(Lnet/jadenxgamer/netherexp/core/block/entity/CiergeOfTreacheryBlockEntity;Ljava/util/List;Lnet/minecraft/world/level/Level;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
            require = 1, allow = 1)
    private static boolean bons$refreshBetrayedHalf(ServerPlayer player, MobEffectInstance given, Operation<Boolean> original) {
        if (NetherExpFixes.candleRefresh && NetherExpFixes.stillFresh(player, given)) return false;
        return original.call(player, given);
    }
}
